/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.mvndaemon.mvnd.it;

import javax.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.common.DaemonException;
import org.mvndaemon.mvnd.junit.MvndTest;

import static org.junit.jupiter.api.Assertions.assertThrows;

@MvndTest(projectDir = "src/test/projects/daemon-crash", keepAlive = "100ms", maxLostKeepAlive = "30")
@Timeout(60)
class DaemonCrashTest {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void cleanInstall() throws IOException, InterruptedException {
        final Path helloPath = parameters.multiModuleProjectDirectory().resolve("hello/target/hello.txt");
        try {
            Files.deleteIfExists(helloPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not delete " + helloPath);
        }

        final Path localMavenRepo = parameters.mavenRepoLocal();
        final Path[] installedJars = {
            localMavenRepo.resolve(
                    "org/mvndaemon/mvnd/test/daemon-crash/daemon-crash-maven-plugin/0.0.1-SNAPSHOT/daemon-crash-maven-plugin-0.0.1-SNAPSHOT.jar"),
        };
        Stream.of(installedJars).forEach(jar -> Assertions.assertThat(jar).doesNotExist());

        final TestClientOutput output = new TestClientOutput();
        assertThrows(DaemonException.StaleAddressException.class, () -> client.execute(
                        output, "clean", "install", "-e", "-Dmvnd.log.level=DEBUG")
                .assertFailure());
    }
}
