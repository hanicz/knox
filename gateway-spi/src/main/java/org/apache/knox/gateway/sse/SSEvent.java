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

public class SSEvent {

    private String data;
    private String event;
    private String id;
    //TODO: Add comment and retry

    public SSEvent() {
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public String getEvent() {
        return event;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        boolean needNewLine = false;
        StringBuilder eventString = new StringBuilder();

        if (id != null && !id.isEmpty()) {
            eventString.append("id:");
            eventString.append(id);
            needNewLine = true;
        }
        if (event != null && !event.isEmpty()) {
            if (needNewLine) {
                eventString.append('\n');
            }
            eventString.append("event:");
            eventString.append(event);
            needNewLine = true;
        }
        if (data != null && !data.isEmpty()) {
            if (needNewLine) {
                eventString.append('\n');
            }
            eventString.append("data:");
            eventString.append(data);
        }
        return eventString.toString();
    }
}
