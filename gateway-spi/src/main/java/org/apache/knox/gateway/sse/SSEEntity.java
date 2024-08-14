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
import org.apache.http.entity.AbstractHttpEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.CharBuffer;
import java.util.concurrent.BlockingQueue;

public class SSEEntity extends AbstractHttpEntity {

    private final BlockingQueue<SSEvent> eventQueue;
    private final StringBuilder eventBuilder = new StringBuilder();
    private final HttpEntity httpEntity;
    private char previousChar = '0';

    public SSEEntity(HttpEntity httpEntity, BlockingQueue<SSEvent> eventQueue) {
        this.httpEntity = httpEntity;
        this.eventQueue = eventQueue;
    }

    public void readCharBuffer(CharBuffer charBuffer) {
        while (charBuffer.hasRemaining()) {
            processChar(charBuffer.get());
        }
    }

    //Two new line chars (\n\n) after each other means the event is finished streaming
    //We can process it and add it to the event queue
    private void processChar(char nextChar) {
        if (nextChar == '\n' && this.previousChar == '\n') {
            this.processEvent();
            this.eventBuilder.setLength(0);
            this.previousChar = '0';
        } else {
            this.eventBuilder.append(nextChar);
            this.previousChar = nextChar;
        }
    }

    private void processEvent() {
        String unprocessedEvent = this.eventBuilder.toString();
        StringBuilder data = new StringBuilder();
        SSEvent ssEvent = new SSEvent();

        for (String line : unprocessedEvent.split("\\R")) {
            String[] lineTokens = line.split(":", 2);
            switch (lineTokens[0]) {
                case "id":
                    ssEvent.setId(lineTokens[1].trim());
                    break;
                case "event":
                    ssEvent.setEvent(lineTokens[1].trim());
                    break;
                case "data":
                    data.append(lineTokens[1].trim());
                    break;
                default:
                    break;
            }
        }

        ssEvent.setData(data.toString());
        eventQueue.add(ssEvent);
    }

    @Override
    public boolean isRepeatable() {
        return httpEntity.isRepeatable();
    }

    @Override
    public long getContentLength() {
        return httpEntity.getContentLength();
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        return httpEntity.getContent();
    }

    @Override
    public void writeTo(OutputStream outStream) throws IOException {
        httpEntity.writeTo(outStream);
    }

    @Override
    public boolean isStreaming() {
        return httpEntity.isStreaming();
    }
}
