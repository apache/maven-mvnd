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
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.junit.MvndTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MvndTest(projectDir = "src/test/projects/exec-output")
class ExecOutputTest {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void cleanInstall() throws IOException, InterruptedException {

        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "clean", "verify", "-e", "-B", "-Dmvnd.log.level=DEBUG")
                .assertSuccess();
        assertTrue(output.messagesToString()
                .contains("ProjectLogMessage{projectId='exec-output', message='[INFO] [stdout] Hello world!'}"));
    }

    @Test
    void cleanTestInheritIO() throws InterruptedException {

        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "clean", "test", "-e", "-Dmvnd.log.level=DEBUG").assertSuccess();
        assertHasTestMessage(output);
    }

    private void assertHasTestMessage(final TestClientOutput output) {
        assertTrue(output.getMessages().stream()
                .filter(Message.ProjectEvent.class::isInstance)
                .map(Message.ProjectEvent.class::cast)
                .anyMatch(it -> it.getMessage().contains("[stdout] From test")));
    }
}
