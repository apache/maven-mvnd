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
import java.nio.file.Path;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.junit.MvndTest;
import org.mvndaemon.mvnd.junit.TestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MvndTest(projectDir = "src/test/projects/concurrent-downloads")
public class ConcurrentDownloadsTest {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void build() throws IOException, InterruptedException {
        final Path localMavenRepo = parameters.mavenRepoLocal();
        TestUtils.deleteDir(localMavenRepo);

        final TestClientOutput o = new TestClientOutput();
        client.execute(o, "clean", "install", "-e", "-B").assertSuccess();

        int maxConcurrentDownloads = 0;
        int cur = 0;
        for (Message m : o.getMessages()) {
            if (m instanceof Message.TransferEvent) {
                Message.TransferEvent event = (Message.TransferEvent) m;
                String resource = event.getResourceName();
                if (resource.contains("apache-camel") || resource.contains("apache-activemq")) {
                    if (m.getType() == Message.TRANSFER_STARTED) {
                        cur++;
                        maxConcurrentDownloads = Math.max(cur, maxConcurrentDownloads);
                    } else if (m.getType() == Message.TRANSFER_SUCCEEDED) {
                        cur--;
                    }
                }
            }
        }
        assertTrue(maxConcurrentDownloads >= 1 && maxConcurrentDownloads <= 2,
                "The maximum number of concurrent downloads (actual: " + maxConcurrentDownloads
                        + ") must fulfil the condition maxConcurrentDownloads >= 1 && maxConcurrentDownloads <= 2");
    }

}
