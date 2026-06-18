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
import org.mvndaemon.mvnd.junit.MvndNativeTest;

@MvndNativeTest(projectDir = "src/test/projects/input-stream")
class InputStreamNativeIT {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void installPluginAndTest() throws IOException, InterruptedException {
        final TestClientOutput output = new TestClientOutput() {
            int input = 0;

            @Override
            public void accept(Message message) {
                if (message instanceof Message.RequestInput) {
                    if (input++ < 10) {
                        daemonDispatch.accept(Message.inputResponse("0123456789\n"));
                    } else {
                        daemonDispatch.accept(Message.inputEof());
                    }
                }
                if (!(message instanceof Message.TransferEvent)) {
                    super.accept(message);
                }
            }
        };
        client.execute(output, "install").assertSuccess();

        client.execute(output, "org.mvndaemon.mvnd.test.input-stream:echo-maven-plugin:echo");
    }

    @Test
    void installPluginAndCount() throws IOException, InterruptedException {
        final TestClientOutput output = new TestClientOutput() {
            boolean sentInput = false;
            String data = "0123456789";

            @Override
            public void accept(Message message) {
                if (message instanceof Message.RequestInput) {
                    if (!sentInput) {
                        daemonDispatch.accept(Message.inputResponse(data));
                        sentInput = true;
                    } else {
                        daemonDispatch.accept(Message.inputEof());
                    }
                } else if (message instanceof Message.RequestInputAvailable) {
                    daemonDispatch.accept(Message.inputAvailableData(data.length()));
                }

                if (!(message instanceof Message.TransferEvent)) {
                    super.accept(message);
                }
            }
        };
        client.execute(output, "install").assertSuccess();

        client.execute(output, "org.mvndaemon.mvnd.test.input-stream:echo-maven-plugin:countbytes")
                .assertSuccess();

        output.assertContainsMatchingSubsequence(
                "Saw 10 bytes available from stdin. Actually read 10 bytes from stdin.");
    }
}
