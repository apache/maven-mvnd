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
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.junit.ClientFactory;
import org.mvndaemon.mvnd.junit.MvndNativeTest;
import org.mvndaemon.mvnd.junit.TestParameters;
import org.mvndaemon.mvnd.junit.TestRegistry;

@MvndNativeTest(projectDir = "src/test/projects/new-managed-module")
class NewManagedModuleNativeIT {

    @Inject
    TestParameters parameters;

    @Inject
    TestRegistry registry;

    @Inject
    ClientFactory clientFactory;

    @Test
    void upgrade() throws IOException, InterruptedException {
        assertDaemonRegistrySize(0);

        /* Build the initial state of the test project */
        final Path parentDir = parameters.getTestDir().resolve("project/parent");
        final Client cl = clientFactory.newClient(parameters.cd(parentDir));
        {
            final TestClientOutput output = new TestClientOutput();
            cl.execute(output, "clean", "install", "-e", "-B", "-ntp").assertSuccess();
        }
        assertDaemonRegistrySize(1);

        final DaemonInfo d = registry.getAll().get(0);
        /* Wait, till the instance becomes idle */
        registry.awaitIdle(d.getId());

        /* Do the changes */
        System.gc();
        Thread.sleep(100);
        System.gc();

        final Path srcDir = parentDir.resolve("../changes").normalize();
        try (Stream<Path> files = Files.walk(srcDir)) {
            files.forEach(source -> {
                final Path relPath = srcDir.relativize(source);
                final Path dest = parentDir.resolve(relPath);
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        /* Build again */
        {
            final TestClientOutput output = new TestClientOutput();
            cl.execute(output, "clean", "install", "-e", "-B", "-ntp").assertSuccess();
        }
        assertDaemonRegistrySize(1);
    }

    private void assertDaemonRegistrySize(int size) {
        Assertions.assertThat(registry.getAll().size())
                .as("Daemon registry size should be " + size)
                .isEqualTo(size);
    }
}
