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
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.assertj.MatchInOrderAmongOthers;
import org.jboss.fuse.mvnd.assertj.TestClientOutput;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.DaemonParameters;
import org.jboss.fuse.mvnd.common.Message;
import org.jboss.fuse.mvnd.junit.MvndNativeTest;
import org.jboss.fuse.mvnd.junit.TestUtils;
import org.junit.jupiter.api.Test;

@MvndNativeTest(projectDir = "src/test/projects/single-module")
public class SingleModuleNativeIT {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void cleanInstall() throws IOException, InterruptedException {
        final Path helloFilePath = parameters.multiModuleProjectDirectory().resolve("target/hello.txt");
        if (Files.exists(helloFilePath)) {
            Files.delete(helloFilePath);
        }

        final Path localMavenRepo = parameters.mavenRepoLocal();
        TestUtils.deleteDir(localMavenRepo);
        final Path installedJar = localMavenRepo.resolve(
                "org/jboss/fuse/mvnd/test/single-module/single-module/0.0.1-SNAPSHOT/single-module-0.0.1-SNAPSHOT.jar");
        Assertions.assertThat(installedJar).doesNotExist();

        final TestClientOutput o = new TestClientOutput();
        client.execute(o, "clean", "install", "-e").assertSuccess();
        final Properties props = MvndTestUtil.properties(parameters.multiModuleProjectDirectory().resolve("pom.xml"));

        final List<String> messages = o.getMessages().stream()
                .filter(m -> m.getType() != Message.MOJO_STARTED)
                .map(m -> m.toString())
                .collect(Collectors.toList());
        Assertions.assertThat(messages)
                .is(new MatchInOrderAmongOthers<>(
                        "Building single-module",
                        MvndTestUtil.plugin(props, "maven-clean-plugin") + ":clean",
                        MvndTestUtil.plugin(props, "maven-compiler-plugin") + ":compile",
                        MvndTestUtil.plugin(props, "maven-compiler-plugin") + ":testCompile",
                        MvndTestUtil.plugin(props, "maven-surefire-plugin") + ":test",
                        MvndTestUtil.plugin(props, "maven-install-plugin") + ":install",
                        "BUILD SUCCESS"));

        assertJVM(o, props);

        /* The target/hello.txt is created by HelloTest */
        Assertions.assertThat(helloFilePath).exists();

        Assertions.assertThat(installedJar).exists();

    }

    protected void assertJVM(TestClientOutput o, Properties props) {
        /* implemented in the subclass */
    }
}
