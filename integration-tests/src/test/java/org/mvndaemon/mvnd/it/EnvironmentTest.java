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
import java.nio.file.Path;
import java.util.Arrays;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.junit.ClientFactory;
import org.mvndaemon.mvnd.junit.MvndTest;
import org.mvndaemon.mvnd.junit.TestParameters;
import org.mvndaemon.mvnd.junit.TestRegistry;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MvndTest(projectDir = "src/test/projects/environment")
class EnvironmentTest {

    @Inject
    TestRegistry registry;

    @Inject
    ClientFactory clientFactory;

    @Inject
    TestParameters parameters;

    @Test
    void cleanInstall() throws IOException, InterruptedException {
        assertDaemonRegistrySize(0);
        /* Install the dependencies */
        for (String artifactDir : Arrays.asList("project/prj1", "project/prj2")) {
            Path dir = parameters.getTestDir().resolve(artifactDir);
            final Client cl = clientFactory.newClient(parameters.cd(dir));
            final TestClientOutput output = new TestClientOutput();
            cl.execute(
                            output,
                            "org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate",
                            "-Dexpression=user.dir",
                            "-e")
                    .assertSuccess();
            assertDaemonRegistrySize(1);
            /* Wait, till the existing instance becomes idle so that the next iteration does not start a new instance */
            registry.awaitIdle(registry.getAll().get(0).getId());

            String pathStr = dir.toAbsolutePath().toString();
            assertTrue(
                    output.getMessages().stream().anyMatch(m -> m.toString().contains(pathStr)),
                    "Output should contain " + pathStr);
            assertTrue(output.getMessages().stream().noneMatch(m -> m.toString().contains("Environment mismatch")));
        }
        assertDaemonRegistrySize(1);
    }

    private void assertDaemonRegistrySize(int size) {
        Assertions.assertThat(registry.getAll().size())
                .as("Daemon registry size should be " + size)
                .isEqualTo(size);
    }
}
