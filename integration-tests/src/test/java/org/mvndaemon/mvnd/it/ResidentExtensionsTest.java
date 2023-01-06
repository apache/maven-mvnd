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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.junit.ClientFactory;
import org.mvndaemon.mvnd.junit.MvndTest;
import org.mvndaemon.mvnd.junit.TestParameters;
import org.mvndaemon.mvnd.junit.TestRegistry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MvndTest(projectDir = "src/test/projects/resident-extensions")
public class ResidentExtensionsTest {

    @Inject
    ClientFactory clientFactory;

    @Inject
    TestRegistry registry;

    @Inject
    TestParameters parameters;

    @Test
    void verify() throws IOException, InterruptedException {
        TestClientOutput o1 = new TestClientOutput();
        Path prj1 = parameters.getTestDir().resolve("project/project1");
        clientFactory
                .newClient(parameters.withMavenMultiModuleProjectDirectory(prj1).cd(prj1))
                .execute(o1, "verify", "-X", "-B")
                .assertSuccess();
        assertTrue(o1.getMessages().stream().map(Object::toString).anyMatch(s -> s.contains("Writing maven timeline")));
        assertDaemonRegistrySize(1);

        TestClientOutput o2 = new TestClientOutput();
        Path prj2 = parameters.getTestDir().resolve("project/project2");
        clientFactory
                .newClient(parameters.withMavenMultiModuleProjectDirectory(prj2).cd(prj2))
                .execute(o2, "verify", "-e", "-B")
                .assertSuccess();
        assertFalse(
                o2.getMessages().stream().map(Object::toString).anyMatch(s -> s.contains("Writing maven timeline")));
        assertDaemonRegistrySize(1);
    }

    private void assertDaemonRegistrySize(int size) {
        Assertions.assertThat(registry.getAll().size())
                .as("Daemon registry size should be " + size)
                .isEqualTo(size);
    }
}
