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
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.junit.MvndTest;
import org.mvndaemon.mvnd.junit.TestRegistry;

@MvndTest(projectDir = "src/test/projects/single-module")
public class StopStatusTest {

    @Inject
    Client client;

    @Inject
    TestRegistry registry;

    @Test
    void stopStatus() throws IOException, InterruptedException {

        /* The registry should be empty before we run anything */
        assertDaemonRegistrySize(0);

        client.execute(new TestClientOutput(), "clean").assertSuccess();
        /* There should be exactly one item in the registry after the first build */
        assertDaemonRegistrySize(1);

        final DaemonInfo d = registry.getAll().get(0);
        {
            /* The output of --status must be consistent with the registry */
            final TestClientOutput output = new TestClientOutput();
            client.execute(output, "--status").assertSuccess();

            output.assertContainsMatchingSubsequence(d.getId() + " +" + d.getPid() + " +" + d.getAddress());
        }
        /* Wait, till the instance becomes idle */
        registry.awaitIdle(d.getId());

        client.execute(new TestClientOutput(), "clean").assertSuccess();
        /* There should still be exactly one item in the registry after the second build */
        assertDaemonRegistrySize(1);
        /* Wait, till the instance becomes idle */
        registry.awaitIdle(d.getId());

        client.execute(new TestClientOutput(), "--stop").assertSuccess();
        /* No items in the registry after we have killed all daemons */
        assertDaemonRegistrySize(0);

        {
            /* After --stop, the output of --status may not contain the daemon ID we have seen before */
            final TestClientOutput output = new TestClientOutput();
            client.execute(output, "--status").assertSuccess();

            Assertions.assertThat(
                    output.messagesToString().stream()
                            .filter(m -> m.contains(d.getId()))
                            .collect(Collectors.toList()))
                    .isEmpty();
        }

    }

    private void assertDaemonRegistrySize(int size) {
        Assertions.assertThat(registry.getAll().size())
                .as("Daemon registry size should be " + size)
                .isEqualTo(size);
    }
}
