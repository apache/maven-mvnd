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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.InOrder;
import org.mockito.Mockito;

@MvndNativeTest(projectDir = "src/test/projects/single-module")
public class SingleModuleNativeIT {

    @Inject
    Client client;

    @Inject
    ClientLayout layout;

    @Test
    void cleanInstall(TestInfo testInfo) throws IOException, InterruptedException {
        final Path helloFilePath = layout.multiModuleProjectDirectory().resolve("target/hello.txt");
        if (Files.exists(helloFilePath)) {
            Files.delete(helloFilePath);
        }

        final Path installedJar = layout.getLocalMavenRepository().resolve("org/jboss/fuse/mvnd/test/single-module/single-module/0.0.1-SNAPSHOT/single-module-0.0.1-SNAPSHOT.jar");
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
        inOrder.verify(o).accept(Mockito.contains("SUCCESS build of project org.jboss.fuse.mvnd.test.single-module:single-module"));

        assertJVM(o, props);

        /* The target/hello.txt is created by HelloTest */
        Assertions.assertThat(helloFilePath).exists();

        Assertions.assertThat(installedJar).exists();

    }

    protected void assertJVM(ClientOutput o, Properties props) {
        /* implemented in the subclass*/
    }
}
