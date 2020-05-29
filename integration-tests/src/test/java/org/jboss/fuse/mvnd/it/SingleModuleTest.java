package org.jboss.fuse.mvnd.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.assertj.MatchInOrderAmongOthers;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.ClientLayout;
import org.jboss.fuse.mvnd.client.ClientOutput;
import org.jboss.fuse.mvnd.client.Layout;
import org.jboss.fuse.mvnd.junit.MvndTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

@MvndTest(projectDir = "src/test/projects/single-module")
public class SingleModuleTest {

    @Inject
    Client client;

    @Inject
    Layout layout;

    @Inject
    ClientLayout clientLayout;

    @Test
    void cleanInstall() throws IOException {
        final Path helloFilePath = layout.multiModuleProjectDirectory().resolve("target/hello.txt");
        if (Files.exists(helloFilePath)) {
            Files.delete(helloFilePath);
        }

        final Path installedJar = clientLayout.getLocalMavenRepository().resolve("org/jboss/fuse/mvnd/test/single-module/single-module/0.0.1-SNAPSHOT/single-module-0.0.1-SNAPSHOT.jar");
        Assertions.assertThat(installedJar).doesNotExist();

        final ClientOutput output = Mockito.mock(ClientOutput.class);
        client.execute(output, "clean", "install", "-e").assertSuccess();

        final ArgumentCaptor<String> logMessage = ArgumentCaptor.forClass(String.class);
        Mockito.verify(output, Mockito.atLeast(1)).log(logMessage.capture());
        Assertions.assertThat(logMessage.getAllValues())
                .is(new MatchInOrderAmongOthers<>(
                        "Building single-module",
                        "maven-clean-plugin:[^:]+:clean",
                        "maven-compiler-plugin:[^:]+:compile",
                        "maven-compiler-plugin:[^:]+:testCompile",
                        "maven-surefire-plugin:[^:]+:test",
                        "maven-install-plugin:[^:]+:install",
                        "SUCCESS build of project org.jboss.fuse.mvnd.test.single-module:single-module"));

        final Properties props = MvndTestUtil.properties(layout.multiModuleProjectDirectory().resolve("pom.xml"));

        final InOrder inOrder = Mockito.inOrder(output);
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-clean-plugin")
                        + ":clean {execution: default-clean}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-resources-plugin")
                        + ":resources {execution: default-resources}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-compiler-plugin")
                        + ":compile {execution: default-compile}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-resources-plugin")
                        + ":testResources {execution: default-testResources}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-compiler-plugin")
                        + ":testCompile {execution: default-testCompile}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-surefire-plugin")
                        + ":test {execution: default-test}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-install-plugin")
                        + ":install {execution: default-install}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");

        inOrder.verify(output).projectFinished("single-module");

        /* The target/hello.txt is created by HelloTest */
        Assertions.assertThat(helloFilePath).exists();

        Assertions.assertThat(installedJar).exists();

    }
}
