/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mvndaemon.mvnd.it;

import java.io.IOException;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.junit.MvndTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MvndTest(projectDir = "src/test/projects/forked")
public class ForkedTest {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void cleanInstall() throws IOException, InterruptedException {

        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "clean", "verify", "-e", "-B").assertSuccess();
        assertTrue(output.messagesToString()
                .contains(
                        "ProjectLogMessage{projectId='forked/forked-mod1', message='[INFO] Forking forked-mod1 0.0.1-SNAPSHOT'}"));

    }
}
