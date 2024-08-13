/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.sse;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.AsyncCharConsumer;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.dispatch.ConfigurableDispatch;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SSEDispatch extends ConfigurableDispatch {

    private CloseableHttpAsyncClient asyncClient;
    private static final String TEXT_EVENT_STREAM_VALUE = "text/event-stream";

    @Override
    public void init() {
        this.asyncClient = HttpAsyncClients.createDefault();
        this.asyncClient.start();
    }

    @Override
    public void doGet(URI url, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse)
            throws IOException {
        final BlockingQueue<SSEvent> eventQueue = new LinkedBlockingQueue<>();
        final HttpGet httpGetRequest = new HttpGet(url);
        this.addAcceptHeader(httpGetRequest);
        this.copyRequestHeaderFields(httpGetRequest, inboundRequest);

        this.executeRequest(httpGetRequest, outboundResponse, eventQueue);
    }

    @Override
    public void doPost(URI url, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse)
            throws IOException, URISyntaxException {
        final BlockingQueue<SSEvent> eventQueue = new LinkedBlockingQueue<>();
        final HttpPost httpPostRequest = new HttpPost(url);
        this.addAcceptHeader(httpPostRequest);
        this.copyRequestHeaderFields(httpPostRequest, inboundRequest);
        HttpEntity entity = this.createRequestEntity(inboundRequest);
        httpPostRequest.setEntity(entity);

        this.executeRequest(httpPostRequest, outboundResponse, eventQueue);
    }

    @Override
    public void doPut(URI url, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse)
            throws IOException {
        final BlockingQueue<SSEvent> eventQueue = new LinkedBlockingQueue<>();
        final HttpPut httpPutRequest = new HttpPut(url);
        this.addAcceptHeader(httpPutRequest);
        this.copyRequestHeaderFields(httpPutRequest, inboundRequest);
        HttpEntity entity = this.createRequestEntity(inboundRequest);
        httpPutRequest.setEntity(entity);

        this.executeRequest(httpPutRequest, outboundResponse, eventQueue);
    }

    @Override
    public void doPatch(URI url, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse)
            throws IOException {
        final BlockingQueue<SSEvent> eventQueue = new LinkedBlockingQueue<>();
        final HttpPatch httpPatchRequest = new HttpPatch(url);
        httpPatchRequest.abort();
        this.addAcceptHeader(httpPatchRequest);
        this.copyRequestHeaderFields(httpPatchRequest, inboundRequest);
        HttpEntity entity = this.createRequestEntity(inboundRequest);
        httpPatchRequest.setEntity(entity);

        this.executeRequest(httpPatchRequest, outboundResponse, eventQueue);
    }

    private void executeRequest(HttpUriRequest outboundRequest, HttpServletResponse outboundResponse, BlockingQueue<SSEvent> eventQueue) {
        HttpAsyncRequestProducer producer = HttpAsyncMethods.create(outboundRequest);
        AsyncCharConsumer<SSEResponse> consumer = new SSECharConsumer(eventQueue, outboundResponse, outboundRequest.getURI());
        Future<SSEResponse> sseConnection = this.getSSEConnection(producer, consumer, outboundRequest);

        this.pollEventQueue(eventQueue, sseConnection, outboundResponse, outboundRequest);
    }

    private Future<SSEResponse> getSSEConnection(HttpAsyncRequestProducer producer, AsyncCharConsumer<SSEResponse> consumer, HttpUriRequest outboundRequest) {
        LOG.dispatchRequest(outboundRequest.getMethod(), outboundRequest.getURI());
        auditor.audit(Action.DISPATCH, outboundRequest.getURI().toString(), ResourceType.URI, ActionOutcome.UNAVAILABLE, RES.requestMethod(outboundRequest.getMethod()));
        return asyncClient.execute(producer, consumer, new FutureCallback<SSEResponse>() {

            @Override
            public void completed(final SSEResponse response) {
                LOG.sseConnectionDone();
            }

            @Override
            public void failed(final Exception ex) {
                LOG.sseConnectionError(ex.getMessage());
            }

            @Override
            public void cancelled() {
                LOG.sseConnectionCancelled();
            }
        });
    }

    private void addAcceptHeader(HttpUriRequest outboundRequest) {
        outboundRequest.setHeader(HttpHeaders.ACCEPT, SSEDispatch.TEXT_EVENT_STREAM_VALUE);
    }

    private void handleOkResponse(HttpServletResponse outboundResponse, URI url, HttpResponse inboundResponse) {
        this.prepareServletResponse(outboundResponse, inboundResponse.getStatusLine().getStatusCode());
        this.copyResponseHeaderFields(outboundResponse, inboundResponse);
        auditor.audit(Action.DISPATCH, url.toString(), ResourceType.URI, ActionOutcome.SUCCESS, RES.responseStatus(HttpStatus.SC_OK));
    }

    private void handleErrorResponse(HttpServletResponse outboundResponse, URI url, HttpResponse httpResponse) {
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        outboundResponse.setStatus(statusCode);
        LOG.dispatchResponseStatusCode(statusCode);
        auditor.audit(Action.DISPATCH, url.toString(), ResourceType.URI, ActionOutcome.FAILURE, RES.responseStatus(statusCode));
    }

    private void prepareServletResponse(HttpServletResponse outboundResponse, int statusCode) {
        LOG.dispatchResponseStatusCode(statusCode);
        outboundResponse.setStatus(statusCode);
        outboundResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
    }

    private boolean isSuccessful(int statusCode) {
        return (statusCode >= HttpStatus.SC_OK && statusCode < 300);
    }

    private void pollEventQueue(BlockingQueue<SSEvent> eventQueue, Future<SSEResponse> sseConnection, HttpServletResponse outboundResponse,
                                HttpUriRequest outboundRequest) {
        try {
            PrintWriter writer = outboundResponse.getWriter();
            while (!sseConnection.isDone()) {
                SSEvent event = eventQueue.poll(10L, TimeUnit.SECONDS);

                if (event == null) {
                    LOG.sseConnectionTimeout();
                    continue;
                }

                writer.println();
                writer.write(event.toString());
                writer.println();

                //Calling response.flushBuffer() instead of writer.flush().
                //This way an exception is thrown if the connection is already closed on the client side.
                outboundResponse.flushBuffer();
            }
        } catch (InterruptedException | IOException e) {
            LOG.errorWritingOutputStream(e);
        } finally {
            if (!sseConnection.isDone() && !outboundRequest.isAborted()) {
                LOG.sseConnectionClose();
                System.out.println("Aborting");
                outboundRequest.abort();
            }
        }
    }

    private class SSECharConsumer extends AsyncCharConsumer<SSEResponse> {
        private SSEResponse sseResponse;
        private final BlockingQueue<SSEvent> eventQueue;
        private final HttpServletResponse outboundResponse;
        private final URI url;

        SSECharConsumer(BlockingQueue<SSEvent> eventQueue, HttpServletResponse outboundResponse, URI url) {
            this.eventQueue = eventQueue;
            this.outboundResponse = outboundResponse;
            this.url = url;
        }

        @Override
        protected void onResponseReceived(final HttpResponse inboundResponse) {
            this.sseResponse = new SSEResponse(inboundResponse, eventQueue);
            if (isSuccessful(inboundResponse.getStatusLine().getStatusCode())) {
                handleOkResponse(outboundResponse, url, inboundResponse);
            } else {
                handleErrorResponse(outboundResponse, url, inboundResponse);
            }
        }

        @Override
        protected void onCharReceived(final CharBuffer buf, final IOControl ioctl) {
            this.sseResponse.getEntity().readCharBuffer(buf);
        }

        @Override
        protected void releaseResources() {
        }

        @Override
        protected SSEResponse buildResult(final HttpContext context) {
            return this.sseResponse;
        }
    }
}
