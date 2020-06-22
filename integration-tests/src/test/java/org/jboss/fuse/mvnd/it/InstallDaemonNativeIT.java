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
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.client.BuildProperties;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.ClientOutput;
import org.jboss.fuse.mvnd.client.DefaultClient;
import org.jboss.fuse.mvnd.client.Environment;
import org.jboss.fuse.mvnd.junit.MvndNativeTest;
import org.jboss.fuse.mvnd.junit.TestLayout;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@MvndNativeTest(projectDir = "src/test/projects/single-module")
public class InstallDaemonNativeIT {

    @Inject
    Client client;

    @Inject
    TestLayout layout;

    @Test
    void installDaemon() throws IOException, InterruptedException {

        final Path testDir = layout.getTestDir();
        final Path mvndPropertiesPath = testDir.resolve("installation-mvnd.properties");
        final Path mavenHome = testDir.resolve("installation-maven-home");

        Assertions.assertThat(mvndPropertiesPath).doesNotExist();
        Assertions.assertThat(mavenHome).doesNotExist();

        final ClientOutput o = Mockito.mock(ClientOutput.class);
        final Path mvndDistPath = Paths.get(Objects.requireNonNull(System.getProperty("mvnd.dist.path")))
                .toAbsolutePath()
                .normalize();
        Assertions.assertThat(mvndDistPath).exists();

        final String mvndDistUri = mvndDistPath.toUri().toString();

        client.execute(o,
                "--install",
                Environment.MVND_DIST_URI.asCommandLineProperty(mvndDistUri),
                Environment.MVND_PROPERTIES_PATH.asCommandLineProperty(mvndPropertiesPath.toString()),
                Environment.JAVA_HOME.asCommandLineProperty(layout.javaHome().toString()),
                Environment.MAVEN_HOME.asCommandLineProperty(mavenHome.toString()))
                .assertSuccess();

        Assertions.assertThat(mvndPropertiesPath).exists();
        Assertions.assertThat(mavenHome).exists();
        final Path mvndShPath = mavenHome.resolve("bin/mvnd");
        Assertions.assertThat(mvndShPath).exists();
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            final PosixFileAttributeView attributes = Files.getFileAttributeView(mvndShPath, PosixFileAttributeView.class);
            Assertions.assertThat(attributes).isNotNull();
            final Set<PosixFilePermission> permissions = attributes.readAttributes().permissions();
            Assertions.assertThat(permissions).contains(PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.OTHERS_EXECUTE);
        }

        final String version = BuildProperties.getInstance().getVersion();
        Assertions.assertThat(mavenHome.resolve("lib/ext/mvnd-client-" + version + ".jar")).exists();
        Assertions.assertThat(mavenHome.resolve("lib/ext/mvnd-daemon-" + version + ".jar")).exists();
    }

}
