package org.jboss.fuse.mvnd.it;

import java.io.IOException;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.assertj.MatchInOrderAmongOthers;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.ClientOutput;
import org.jboss.fuse.mvnd.client.DaemonInfo;
import org.jboss.fuse.mvnd.client.DaemonRegistry;
import org.jboss.fuse.mvnd.client.DaemonState;
import org.jboss.fuse.mvnd.junit.MvndTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@MvndTest(projectDir = "src/test/projects/single-module")
public class StopStatusTest {

    @Inject
    Client client;

    @Inject
    DaemonRegistry registry;

    @Test
    void stopStatus() throws IOException {

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
            Mockito.verify(output, Mockito.atLeast(1)).log(logMessage.capture());
            Assertions.assertThat(logMessage.getAllValues())
                    .is(new MatchInOrderAmongOthers<>(
                            d.getUid() + " +" + d.getPid() + " +" + d.getAddress()));

        }
        /* Wait, till the instance becomes idle */
        final int timeoutMs = 5000;
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (!registry.getAll().stream()
                .filter(di -> di.getUid().equals(d.getUid()) && di.getState() == DaemonState.Idle)
                .findFirst()
                .isPresent()) {
            Assertions.assertThat(deadline)
                    .withFailMessage("Daemon %s should have become idle within %d", d.getUid(), timeoutMs)
                    .isGreaterThan(System.currentTimeMillis());
        }

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
            Mockito.verify(output, Mockito.atLeast(1)).log(logMessage.capture());
            Assertions.assertThat(
                    logMessage.getAllValues().stream()
                            .filter(m -> m.contains(d.getUid()))
                            .collect(Collectors.toList()))
                    .isEmpty();
        }

    }
}
