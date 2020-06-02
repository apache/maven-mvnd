package org.jboss.fuse.mvnd.it;

import java.io.IOException;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.assertj.MatchInOrderAmongOthers;
import org.jboss.fuse.mvnd.daemon.Client;
import org.jboss.fuse.mvnd.daemon.ClientOutput;
import org.jboss.fuse.mvnd.junit.MvndTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@MvndTest(projectDir = "src/test/projects/single-module")
public class VersionTest {

    @Inject
    Client client;

    @Test
    void version() throws IOException {
        final ClientOutput output = Mockito.mock(ClientOutput.class);

        client.execute(output, "-v").assertSuccess();

        final ArgumentCaptor<String> logMessage = ArgumentCaptor.forClass(String.class);
        Mockito.verify(output, Mockito.atLeast(1)).log(logMessage.capture());

        Assertions.assertThat(logMessage.getAllValues())
                .is(new MatchInOrderAmongOthers<>("Maven Daemon " + System.getProperty("project.version")));
    }
}
