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

import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.junit.MvndTest;

import javax.inject.Inject;

import static junit.framework.Assert.assertTrue;

@MvndTest(projectDir = "src/test/projects/junit-platform")
public class JUnitPlatformTest {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void cleanTestInheritIO() throws InterruptedException {

        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "clean", "test", "-e", "-Dmvnd.log.level=DEBUG", "-DinheritIO=true").assertSuccess();
        assertHasTestMessage(output);

    }

    @Test
    void cleanTestDontInheritIO() throws InterruptedException {

        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "clean", "test", "-e", "-Dmvnd.log.level=DEBUG", "-DinheritIO=false").assertSuccess();
        assertHasTestMessage(output);

    }

    private void assertHasTestMessage(final TestClientOutput output) {
        assertTrue(output.getMessages().stream()
                .filter(Message.ProjectEvent.class::isInstance)
                .map(Message.ProjectEvent.class::cast)
                .anyMatch(it -> it.getMessage() != null &&
                        "From test".equals(it.getMessage().replaceFirst(".*] ", ""))));
    }
}
