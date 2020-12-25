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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.MatchInOrderAmongOthers;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.common.Message.ProjectEvent;
import org.mvndaemon.mvnd.common.Message.StringMessage;
import org.mvndaemon.mvnd.junit.MvndTest;
import org.mvndaemon.mvnd.junit.TestUtils;

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
                        "org/mvndaemon/mvnd/test/multi-module/multi-module-api/0.0.1-SNAPSHOT/multi-module-api-0.0.1-SNAPSHOT.jar"),
                localMavenRepo.resolve(
                        "org/mvndaemon/mvnd/test/multi-module/multi-module-hello/0.0.1-SNAPSHOT/multi-module-hello-0.0.1-SNAPSHOT.jar"),
                localMavenRepo.resolve(
                        "org/mvndaemon/mvnd/test/multi-module/multi-module-hi/0.0.1-SNAPSHOT/multi-module-hi-0.0.1-SNAPSHOT.jar")
        };
        Stream.of(installedJars).forEach(jar -> Assertions.assertThat(jar).doesNotExist());

        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "clean", "install", "-e").assertSuccess();

        {
            final List<String> filteredMessages = output.getMessages().stream()
                    .filter(m -> m.getType() == Message.PROJECT_LOG_MESSAGE)
                    .map(m -> ((ProjectEvent) m).getMessage())
                    .collect(Collectors.toList());

            Assertions.assertThat(filteredMessages)
                    .satisfiesAnyOf( /* Two orderings are possible */
                            messages -> Assertions.assertThat(messages)
                                    .is(new MatchInOrderAmongOthers<>(
                                            "SUCCESS build of project org.mvndaemon.mvnd.test.multi-module:multi-module$",
                                            "SUCCESS build of project org.mvndaemon.mvnd.test.multi-module:multi-module-api",
                                            "SUCCESS build of project org.mvndaemon.mvnd.test.multi-module:multi-module-hello",
                                            "SUCCESS build of project org.mvndaemon.mvnd.test.multi-module:multi-module-hi")),
                            messages -> Assertions.assertThat(messages)
                                    .is(new MatchInOrderAmongOthers<>(
                                            "SUCCESS build of project org.mvndaemon.mvnd.test.multi-module:multi-module$",
                                            "SUCCESS build of project org.mvndaemon.mvnd.test.multi-module:multi-module-api",
                                            "SUCCESS build of project org.mvndaemon.mvnd.test.multi-module:multi-module-hi",
                                            "SUCCESS build of project org.mvndaemon.mvnd.test.multi-module:multi-module-hello")));
        }

        {
            final List<String> filteredMessages = output.getMessages().stream()
                    .filter(m -> m.getType() == Message.PROJECT_STARTED)
                    .map(m -> ((StringMessage) m).getMessage())
                    .collect(Collectors.toList());

            Assertions.assertThat(filteredMessages)
                    .satisfiesAnyOf( /* Two orderings are possible */
                            messages -> Assertions.assertThat(messages)
                                    .isEqualTo(Arrays.asList(
                                            "multi-module",
                                            "multi-module-api",
                                            "multi-module-hello",
                                            "multi-module-hi")),
                            messages -> Assertions.assertThat(messages)
                                    .isEqualTo(Arrays.asList(
                                            "multi-module",
                                            "multi-module-api",
                                            "multi-module-hi",
                                            "multi-module-hello")));
        }

        /* Make sure HelloTest and HiTest have created the files they were supposed to create */
        Stream.of(helloFilePaths).forEach(path -> Assertions.assertThat(path).exists());

        Stream.of(installedJars).forEach(jar -> Assertions.assertThat(jar).exists());

    }
}
