package org.apache.knox.gateway.dispatch;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.dto.SSEvent;
import org.eclipse.jetty.client.HttpClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SSEDispatch extends ConfigurableDispatch {

    private HttpClient jettyClient;
    private final ParameterizedTypeReference<ServerSentEvent<String>> type = new ParameterizedTypeReference<ServerSentEvent<String>>() {};

    @Override
    public void init() {
        if (this.jettyClient == null) {
            this.jettyClient = new HttpClient();
        }
    }

    @Override
    public void doGet(URI url, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        final BlockingQueue<SSEvent> eventQueue = new LinkedBlockingQueue<>();

        LOG.dispatchRequest("GET", url);
        auditor.audit(Action.DISPATCH, url.toString(), ResourceType.URI, ActionOutcome.UNAVAILABLE, RES.requestMethod("GET"));
        WebClient wb = this.createWebCLient(url);

        Flux<ServerSentEvent<String>> flux = wb.get()
                .headers(httpHeaders -> this.copyRequestHeaderFields(request, httpHeaders))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchangeToFlux(clientResponse -> {
                    if (HttpStatus.OK.value() == clientResponse.rawStatusCode()) {
                        this.prepareResponse(response);
                        auditor.audit(Action.DISPATCH, url.toString(), ResourceType.URI, ActionOutcome.SUCCESS, RES.responseStatus(HttpStatus.OK.value()));
                        return clientResponse.bodyToFlux(type);
                    }

                    response.setStatus(clientResponse.rawStatusCode());
                    LOG.dispatchResponseStatusCode(clientResponse.rawStatusCode());
                    auditor.audit(Action.DISPATCH, url.toString(), ResourceType.URI, ActionOutcome.FAILURE, RES.responseStatus(clientResponse.rawStatusCode()));
                    return Flux.empty();
                });

        Disposable sseConnection = this.getDisposable(flux, eventQueue);
        this.pollEventQueue(eventQueue, sseConnection, response);
    }

    private Disposable getDisposable(Flux<ServerSentEvent<String>> flux, BlockingQueue<SSEvent> eventQueue) {
        return flux.subscribe(
                content -> eventQueue.add(new SSEvent(content.event(), content.data())),
                error -> LOG.sseConnectionError(error.toString()),
                LOG::sseConnectionDone
        );
    }

    private WebClient createWebCLient(URI url) {
        return WebClient.builder()
                .clientConnector(new JettyClientHttpConnector(this.jettyClient))
                .baseUrl(url.toString())
                .build();
    }

    private void pollEventQueue(BlockingQueue<SSEvent> eventQueue, Disposable sseConnection, HttpServletResponse response) {
        try {
            PrintWriter writer = response.getWriter();
            while (!sseConnection.isDisposed()) {
                SSEvent event = eventQueue.poll(10L, TimeUnit.SECONDS);

                if (event == null) {
                    LOG.sseConnectionTimeout();
                    continue;
                }

                writer.println();
                writer.write(event.toString());
                writer.println();

                //Calling response.flushBuffer() instead of writer.flush().
                // This way an exception is thrown if the connection is already closed on the client side.
                response.flushBuffer();
            }
        } catch (InterruptedException | IOException e) {
            LOG.errorWritingOutputStream(e);
        } finally {
            if (!sseConnection.isDisposed()) {
                LOG.sseConnectionClose();
                sseConnection.dispose();
            }
        }
    }

    private void prepareResponse(HttpServletResponse response) {
        LOG.dispatchResponseStatusCode(HttpStatus.OK.value());
        response.setStatus(HttpStatus.OK.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private void copyRequestHeaderFields(HttpServletRequest inboundRequest, HttpHeaders httpHeaders) {
        Enumeration<String> headerNames = inboundRequest.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!httpHeaders.containsKey(name)
                    && !getOutboundRequestExcludeHeaders().contains(name)) {
                String value = inboundRequest.getHeader(name);
                httpHeaders.add(name, value);
            }
        }
    }

    //TODO: Copy response headers
    private void copyResponseHeaderFields(HttpServletResponse response, ClientResponse clientResponse) {}

    //TODO: Copy request entity (PUT, POST, PATCH)
    private void copyRequestEntity() {}

    @Override
    public void doPost(URI url, HttpServletRequest request, HttpServletResponse response)
            throws IOException, URISyntaxException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Override
    public void doPut(URI url, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Override
    public void doPatch(URI url, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Override
    public void doDelete(URI url, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Override
    public void doOptions(URI url, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Override
    public void doHead(URI url, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
