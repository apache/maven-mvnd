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
package org.jboss.fuse.mvnd.it;

import java.io.IOException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.assertj.MatchInOrderAmongOthers;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.common.DaemonInfo;
import org.jboss.fuse.mvnd.common.logging.ClientOutput;
import org.jboss.fuse.mvnd.junit.MvndTest;
import org.jboss.fuse.mvnd.junit.TestRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;

@MvndTest(projectDir = "src/test/projects/single-module")
public class StopStatusTest {

    @Inject
    Client client;

    @Inject
    TestRegistry registry;

    @Test
    void stopStatus() throws IOException, InterruptedException {

        /* The registry should be empty before we run anything */
        Assertions.assertThat(registry.getAll()).isEmpty();

        client.execute(Mockito.mock(ClientOutput.class), "clean").assertSuccess();
        /* There should be exactly one item in the registry after the first build */
        Assertions.assertThat(registry.getAll().size()).isEqualTo(1);

        final DaemonInfo d = registry.getAll().get(0);
        {
            /* The output of --status must be consistent with the registry */
            final ClientOutput output = Mockito.mock(ClientOutput.class);
            client.execute(output, "--status").assertSuccess();
            final ArgumentCaptor<String> logMessage = ArgumentCaptor.forClass(String.class);
            Mockito.verify(output, Mockito.atLeast(1)).accept(any(), logMessage.capture());
            Assertions.assertThat(logMessage.getAllValues())
                    .is(new MatchInOrderAmongOthers<>(
                            d.getUid() + " +" + d.getPid() + " +" + d.getAddress()));

        }
        /* Wait, till the instance becomes idle */
        registry.awaitIdle(d.getUid());

        client.execute(Mockito.mock(ClientOutput.class), "clean").assertSuccess();
        /* There should still be exactly one item in the registry after the second build */
        Assertions.assertThat(registry.getAll().size()).isEqualTo(1);

        client.execute(Mockito.mock(ClientOutput.class), "--stop").assertSuccess();
        /* No items in the registry after we have killed all daemons */
        Assertions.assertThat(registry.getAll()).isEmpty();

        {
            /* After --stop, the output of --status may not contain the UID we have seen before */
            final ClientOutput output = Mockito.mock(ClientOutput.class);
            client.execute(output, "--status").assertSuccess();
            final ArgumentCaptor<String> logMessage = ArgumentCaptor.forClass(String.class);
            Mockito.verify(output, Mockito.atLeast(1)).accept(any(), logMessage.capture());
            Assertions.assertThat(
                    logMessage.getAllValues().stream()
                            .filter(m -> m.contains(d.getUid()))
                            .collect(Collectors.toList()))
                    .isEmpty();
        }

    }
}
