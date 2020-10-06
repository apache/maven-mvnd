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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.ClientLayout;
import org.jboss.fuse.mvnd.client.ClientOutput;
import org.jboss.fuse.mvnd.junit.MvndNativeTest;
import org.jboss.fuse.mvnd.junit.TestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@MvndNativeTest(projectDir = "src/test/projects/module-and-plugin")
public class ModuleAndPluginNativeIT {

    @Inject
    Client client;

    @Inject
    ClientLayout layout;

    @Test
    void cleanInstall() throws IOException, InterruptedException {
        final Path helloPath = layout.multiModuleProjectDirectory().resolve("hello/target/hello.txt");
        try {
            Files.deleteIfExists(helloPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not delete " + helloPath);
        }

        final Path localMavenRepo = layout.getLocalMavenRepository();
        TestUtils.deleteDir(localMavenRepo);
        final Path[] installedJars = {
                localMavenRepo.resolve(
                        "org/jboss/fuse/mvnd/test/module-and-plugin/module-and-plugin-maven-plugin/0.0.1-SNAPSHOT/module-and-plugin-maven-plugin-0.0.1-SNAPSHOT.jar"),
        };
        Stream.of(installedJars).forEach(jar -> Assertions.assertThat(jar).doesNotExist());

        /* Build #1: with "Hello" output to target/hello.txt */
        {
            final ClientOutput output = Mockito.mock(ClientOutput.class);
            client.execute(output, "clean", "install", "-e", "-Dmvnd.log.level=DEBUG").assertSuccess();

            Assertions.assertThat(helloPath).exists();
            Assertions.assertThat(helloPath).usingCharset(StandardCharsets.UTF_8).hasContent("Hello");
            Stream.of(installedJars).forEach(jar -> Assertions.assertThat(jar).exists());
        }

        /* Build #2: with the mojo source changed to output "Hi" to target/hello.txt */
        {
            final Path mojoPath = layout.multiModuleProjectDirectory()
                    .resolve("plugin/src/main/java/org/jboss/fuse/mvnd/test/module/plugin/mojo/HelloMojo.java");
            TestUtils.replace(mojoPath, "\"Hello\".getBytes", "\"Hi\".getBytes");

            final ClientOutput output = Mockito.mock(ClientOutput.class);
            client.execute(output,
                    "clean",
                    "install", "-e", "-Dmvnd.log.level=DEBUG").assertSuccess();

            Assertions.assertThat(helloPath).exists();
            Assertions.assertThat(helloPath).usingCharset(StandardCharsets.UTF_8).hasContent("Hi");
            Stream.of(installedJars).forEach(jar -> Assertions.assertThat(jar).exists());
        }

    }
}
