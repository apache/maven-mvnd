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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.common.Message.Prompt;
import org.mvndaemon.mvnd.junit.MvndTest;

@MvndTest(projectDir = "src/test/projects/single-module")
@Timeout(300)
public class InteractiveTest {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void versionsSet() throws IOException, InterruptedException {
        final String version =
                MvndTestUtil.version(parameters.multiModuleProjectDirectory().resolve("pom.xml"));
        Assertions.assertEquals("0.0.1-SNAPSHOT", version);

        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "-v").assertSuccess();
        output.describeTerminal();
        String mavenVersion = output.getMessages().stream()
                .filter(m -> m.getType() == Message.BUILD_LOG_MESSAGE)
                .map(m -> ((Message.StringMessage) m).getMessage())
                .filter(s -> s.matches("[\\s\\S]*Apache Maven ([0-9][^ ]*) [\\s\\S]*"))
                .map(s -> s.replaceAll("[\\s\\S]*Apache Maven ([0-9][^ ]*) [\\s\\S]*", "$1"))
                .findFirst()
                .get();

        final TestClientOutput o = new TestClientOutput() {
            @Override
            public void accept(Message m) {
                if (m instanceof Prompt) {
                    daemonDispatch.accept(((Prompt) m).response("0.1.0-SNAPSHOT"));
                }
                super.accept(m);
            }
        };
        if (mavenVersion.startsWith("4")) {
            client.execute(o, "--force-interactive", "versions:set").assertSuccess();
        } else {
            client.execute(o, "versions:set").assertSuccess();
        }

        final String newVersion =
                MvndTestUtil.version(parameters.multiModuleProjectDirectory().resolve("pom.xml"));
        Assertions.assertEquals("0.1.0-SNAPSHOT", newVersion);
    }
}
