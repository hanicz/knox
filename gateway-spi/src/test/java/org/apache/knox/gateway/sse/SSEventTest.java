/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.sse;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SSEventTest {


    @Test
    public void toStringWithAll() {
        SSEvent ssEvent = new SSEvent("data", "event", "id");
        String expected = "id:id\nevent:event\ndata:data";

        assertEquals(expected, ssEvent.toString());
    }

    @Test
    public void toStringNoId() {
        SSEvent ssEventNull = new SSEvent("data", "event", null);
        SSEvent ssEventEmpty = new SSEvent("data", "event", "");
        String expected = "event:event\ndata:data";

        assertEquals(expected, ssEventNull.toString());
        assertEquals(expected, ssEventEmpty.toString());
    }

    @Test
    public void toStringNoEvent() {
        SSEvent ssEventNull = new SSEvent("data", null, "id");
        SSEvent ssEventEmpty = new SSEvent("data", "", "id");
        String expected = "id:id\ndata:data";

        assertEquals(expected, ssEventNull.toString());
        assertEquals(expected, ssEventEmpty.toString());
    }

    @Test
    public void toStringNoData() {
        SSEvent ssEventNull = new SSEvent(null, "event", "id");
        SSEvent ssEventEmpty = new SSEvent("", "event", "id");
        String expected = "id:id\nevent:event";

        assertEquals(expected, ssEventNull.toString());
        assertEquals(expected, ssEventEmpty.toString());
    }
}