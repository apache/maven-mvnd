package org.jboss.fuse.mvnd.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.assertj.EqualsInOrderAmongOthers;
import org.jboss.fuse.mvnd.assertj.MatchInOrderAmongOthers;
import org.jboss.fuse.mvnd.daemon.Client;
import org.jboss.fuse.mvnd.daemon.ClientOutput;
import org.jboss.fuse.mvnd.daemon.Layout;
import org.jboss.fuse.mvnd.junit.MvndTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@MvndTest(projectDir = "src/test/projects/multi-module")
public class MultiModuleTest {

    @Inject
    Client client;

    @Inject
    Layout layout;

    @Test
    void cleanTest() throws IOException {
        final Path[] helloFilePaths = {
                layout.multiModuleProjectDirectory().resolve("hello/target/hello.txt"),
                layout.multiModuleProjectDirectory().resolve("hi/target/hi.txt")
        };
        for (Path path : helloFilePaths) {
            if (Files.exists(path)) {
                Files.delete(path);
            }
        }

        final ClientOutput output = Mockito.mock(ClientOutput.class);
        client.execute(output, "clean", "test").assertSuccess();

        final ArgumentCaptor<String> logMessage = ArgumentCaptor.forClass(String.class);
        Mockito.verify(output, Mockito.atLeast(1)).log(logMessage.capture());
        Assertions.assertThat(logMessage.getAllValues())
                .satisfiesAnyOf( /* Two orderings are possible */
                        messages -> Assertions.assertThat(messages)
                                .is(new MatchInOrderAmongOthers<>(
                                        "SUCCESS build of project org.jboss.fuse.mvnd.test.multi-module:multi-module$",
                                        "SUCCESS build of project org.jboss.fuse.mvnd.test.multi-module:multi-module-api",
                                        "SUCCESS build of project org.jboss.fuse.mvnd.test.multi-module:multi-module-hello",
                                        "SUCCESS build of project org.jboss.fuse.mvnd.test.multi-module:multi-module-hi")),
                        messages -> Assertions.assertThat(messages)
                                .is(new MatchInOrderAmongOthers<>(
                                        "SUCCESS build of project org.jboss.fuse.mvnd.test.multi-module:multi-module$",
                                        "SUCCESS build of project org.jboss.fuse.mvnd.test.multi-module:multi-module-api",
                                        "SUCCESS build of project org.jboss.fuse.mvnd.test.multi-module:multi-module-hi",
                                        "SUCCESS build of project org.jboss.fuse.mvnd.test.multi-module:multi-module-hello"))

                );

        final ArgumentCaptor<String> projectFinished = ArgumentCaptor.forClass(String.class);
        Mockito.verify(output, Mockito.atLeast(1)).projectFinished(projectFinished.capture());
        Assertions.assertThat(projectFinished.getAllValues())
                .satisfiesAnyOf( /* Two orderings are possible */
                        messages -> Assertions.assertThat(messages)
                                .is(new EqualsInOrderAmongOthers<>(
                                        "multi-module",
                                        "multi-module-api",
                                        "multi-module-hello",
                                        "multi-module-hi")),
                        messages -> Assertions.assertThat(messages)
                                .is(new EqualsInOrderAmongOthers<>(
                                        "multi-module",
                                        "multi-module-api",
                                        "multi-module-hi",
                                        "multi-module-hello")));

        /* Make sure HelloTest and HiTest have created the files they were supposed to create */
        for (Path path : helloFilePaths) {
            Assertions.assertThat(path).exists();
        }

    }
}
