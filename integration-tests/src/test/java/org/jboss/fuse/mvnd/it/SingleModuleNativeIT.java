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
import java.util.Properties;
import javax.inject.Inject;
import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.ClientLayout;
import org.jboss.fuse.mvnd.client.ClientOutput;
import org.jboss.fuse.mvnd.junit.MvndNativeTest;
import org.jboss.fuse.mvnd.junit.TestUtils;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

@MvndNativeTest(projectDir = "src/test/projects/single-module")
public class SingleModuleNativeIT {

    @Inject
    Client client;

    @Inject
    ClientLayout layout;

    @Test
    void cleanInstall() throws IOException, InterruptedException {
        final Path helloFilePath = layout.multiModuleProjectDirectory().resolve("target/hello.txt");
        if (Files.exists(helloFilePath)) {
            Files.delete(helloFilePath);
        }

        final Path installedJar = layout.getLocalMavenRepository().resolve(
                "org/jboss/fuse/mvnd/test/single-module/single-module/0.0.1-SNAPSHOT/single-module-0.0.1-SNAPSHOT.jar");
        final Path localMavenRepo = layout.getLocalMavenRepository();
        TestUtils.extractLocalMavenRepo(localMavenRepo);
        Assertions.assertThat(installedJar).doesNotExist();

        final ClientOutput o = Mockito.mock(ClientOutput.class);
        client.execute(o, "clean", "install", "-e").assertSuccess();
        final Properties props = MvndTestUtil.properties(layout.multiModuleProjectDirectory().resolve("pom.xml"));

        final InOrder inOrder = Mockito.inOrder(o);
        inOrder.verify(o).accept(Mockito.contains("Building single-module"));
        inOrder.verify(o).accept(Mockito.contains(MvndTestUtil.plugin(props, "maven-clean-plugin") + ":clean"));
        inOrder.verify(o).accept(Mockito.contains(MvndTestUtil.plugin(props, "maven-compiler-plugin") + ":compile"));
        inOrder.verify(o).accept(Mockito.contains(MvndTestUtil.plugin(props, "maven-compiler-plugin") + ":testCompile"));
        inOrder.verify(o).accept(Mockito.contains(MvndTestUtil.plugin(props, "maven-surefire-plugin") + ":test"));
        inOrder.verify(o).accept(Mockito.contains(MvndTestUtil.plugin(props, "maven-install-plugin") + ":install"));
        inOrder.verify(o)
                .accept(Mockito.contains("SUCCESS build of project org.jboss.fuse.mvnd.test.single-module:single-module"));

        assertJVM(o, props);

        /* The target/hello.txt is created by HelloTest */
        Assertions.assertThat(helloFilePath).exists();

        Assertions.assertThat(installedJar).exists();

    }

    protected void assertJVM(ClientOutput o, Properties props) {
        /* implemented in the subclass*/
    }
}
