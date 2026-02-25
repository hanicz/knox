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
package org.apache.knox.gateway.filter.rewrite.api;

import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class UrlRewriteProcessorFragmentTest {

    private static URL getTestResourceUrl(String name) throws FileNotFoundException {
        name = UrlRewriteProcessorFragmentTest.class.getName().replace('.', '/') + "/" + name;
        URL url = ClassLoader.getSystemResource(name);
        if (url == null) {
            throw new FileNotFoundException(name);
        }
        return url;
    }

    private static InputStream getTestResourceStream(String name) throws IOException {
        URL url = getTestResourceUrl(name);
        return url.openStream();
    }

    private static Reader getTestResourceReader(String name) throws IOException {
        return new InputStreamReader(getTestResourceStream(name), StandardCharsets.UTF_8);
    }

    @Test
    public void testFragmentRewriteIssue() throws Exception {
        UrlRewriteEnvironment environment = EasyMock.createNiceMock(UrlRewriteEnvironment.class);
        HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
        EasyMock.replay(environment, request, response);

        UrlRewriteProcessor processor = new UrlRewriteProcessor();
        UrlRewriteRulesDescriptor config = UrlRewriteRulesDescriptorFactory.load(
                "xml", getTestResourceReader("rewrite.xml"));
        processor.initialize(environment, config);

        // Input URL with fragment

        String inputUrlStr = "/proxy/application_1770634578756_0012/streaming/?doAs=systest&completedBatches.sort=Total+Delay&completedBatches.pageSize=100#myFragment";

        Template inputUrl = Parser.parseLiteral(inputUrlStr);

        System.out.println("Parsed Input URL: " + inputUrl);

        System.out.println("Parsed Path: " + inputUrl.getPath());

        System.out.println("Parsed Query: " + inputUrl.getQuery());

        System.out.println("Parsed Fragment: " + inputUrl.getFragment());
        if (inputUrl.getFragment() != null) {
            System.out.println("Fragment Class: " + inputUrl.getFragment().getClass().getName());
        }

        Template outputUrl = processor.rewrite(environment, inputUrl, UrlRewriter.Direction.OUT, "YARNUIV2/yarnuiv2/outbound/apps/history");

        // Debug info
        if (outputUrl == null) {
            System.out.println("Rewrite failed for URL: " + inputUrlStr);
            // Try without fragment
            String inputUrlNoFrag = inputUrlStr.substring(0, inputUrlStr.indexOf('#'));
            Template input2 = Parser.parseLiteral(inputUrlNoFrag);
            Template output2 = processor.rewrite(environment, input2, UrlRewriter.Direction.OUT, "YARNUIV2/yarnuiv2/outbound/apps/history");
            System.out.println("Rewrite result without fragment: " + output2);
        }

        assertThat("Expect rewrite to produce a new URL",
                outputUrl, notNullValue());

        System.out.println("Rewritten URL: " + outputUrl.toString());

        // The issue is that the fragment "completedBatches" gets appended to the path.
        // So the path becomes .../streaming/completedBatches/
        // We want to assert that this DOES NOT happen.

        // If reproduction works, this assertion should FAIL if I assert correct behavior.
        // Or I can assert the incorrect behavior to confirm reproduction.

        // Let's just print it first and see what happens.

        processor.destroy();
    }
}
