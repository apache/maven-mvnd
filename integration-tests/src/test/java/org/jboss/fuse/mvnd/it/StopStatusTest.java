package org.jboss.fuse.mvnd.it;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.jboss.fuse.mvnd.assertj.MatchInOrderAmongOthers;
import org.jboss.fuse.mvnd.daemon.Client;
import org.jboss.fuse.mvnd.daemon.ClientOutput;
import org.jboss.fuse.mvnd.daemon.DaemonInfo;
import org.jboss.fuse.mvnd.daemon.DaemonRegistry;
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

        final ClientOutput output = Mockito.mock(ClientOutput.class);
        client.execute(output, "--status").assertSuccess();
        final DaemonInfo d = registry.getAll().get(0);
        final ArgumentCaptor<String> logMessage = ArgumentCaptor.forClass(String.class);
        Mockito.verify(output, Mockito.atLeast(1)).log(logMessage.capture());
        Assertions.assertThat(logMessage.getAllValues())
                .is(new MatchInOrderAmongOthers<>(
                        d.getUid() + " +" + d.getPid() + " +" + d.getAddress() + " +" + d.getState()));

        client.execute(Mockito.mock(ClientOutput.class), "clean").assertSuccess();
        /* There should still be exactly one item in the registry after the second build */
        Assertions.assertThat(registry.getAll().size()).isEqualTo(1);

        client.execute(Mockito.mock(ClientOutput.class), "--stop").assertSuccess();
        /* No items in the registry after we have killed all daemons */
        Assertions.assertThat(registry.getAll()).isEmpty();
    }
}
