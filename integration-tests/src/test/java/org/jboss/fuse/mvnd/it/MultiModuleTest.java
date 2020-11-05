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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.assertj.EqualsInOrderAmongOthers;
import org.jboss.fuse.mvnd.assertj.MatchInOrderAmongOthers;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.DaemonParameters;
import org.jboss.fuse.mvnd.common.logging.ClientOutput;
import org.jboss.fuse.mvnd.junit.MvndTest;
import org.jboss.fuse.mvnd.junit.TestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;

@MvndTest(projectDir = "src/test/projects/multi-module")
public class MultiModuleTest {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void cleanInstall() throws IOException, InterruptedException {
        final Path[] helloFilePaths = {
                parameters.multiModuleProjectDirectory().resolve("hello/target/hello.txt"),
                parameters.multiModuleProjectDirectory().resolve("hi/target/hi.txt")
        };
        Stream.of(helloFilePaths).forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new RuntimeException("Could not delete " + path);
            }
        });

        final Path localMavenRepo = parameters.mavenRepoLocal();
        TestUtils.deleteDir(localMavenRepo);
        final Path[] installedJars = {
                localMavenRepo.resolve(
                        "org/jboss/fuse/mvnd/test/multi-module/multi-module-api/0.0.1-SNAPSHOT/multi-module-api-0.0.1-SNAPSHOT.jar"),
                localMavenRepo.resolve(
                        "org/jboss/fuse/mvnd/test/multi-module/multi-module-hello/0.0.1-SNAPSHOT/multi-module-hello-0.0.1-SNAPSHOT.jar"),
                localMavenRepo.resolve(
                        "org/jboss/fuse/mvnd/test/multi-module/multi-module-hi/0.0.1-SNAPSHOT/multi-module-hi-0.0.1-SNAPSHOT.jar")
        };
        Stream.of(installedJars).forEach(jar -> Assertions.assertThat(jar).doesNotExist());

        final ClientOutput output = Mockito.mock(ClientOutput.class);
        client.execute(output, "clean", "install", "-e").assertSuccess();

        final ArgumentCaptor<String> logMessage = ArgumentCaptor.forClass(String.class);
        Mockito.verify(output, Mockito.atLeast(1)).accept(any(), logMessage.capture());
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
        Stream.of(helloFilePaths).forEach(path -> Assertions.assertThat(path).exists());

        Stream.of(installedJars).forEach(jar -> Assertions.assertThat(jar).exists());

    }
}
