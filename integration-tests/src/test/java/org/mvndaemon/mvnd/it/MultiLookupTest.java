/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.mvndaemon.mvnd.it;

import javax.inject.Inject;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.junit.ClientFactory;
import org.mvndaemon.mvnd.junit.MvndTest;
import org.mvndaemon.mvnd.junit.TestParameters;

@MvndTest(projectDir = "src/test/projects/multi-lookup")
public class MultiLookupTest {

    @Inject
    ClientFactory clientFactory;

    @Inject
    TestParameters parameters;

    @Test
    void cleanInstall() throws IOException, InterruptedException {

        final TestClientOutput output = new TestClientOutput();

        clientFactory
                .newClient(parameters.cd(parameters.getTestDir().resolve("project/hello")))
                .execute(output, "clean", "install", "-e")
                .assertFailure();

        clientFactory
                .newClient(parameters)
                .execute(output, "clean", "install", "-e")
                .assertSuccess();

        clientFactory
                .newClient(parameters.cd(parameters.getTestDir().resolve("project/hello")))
                .execute(output, "clean", "install", "-e")
                .assertSuccess();
    }
}
