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

import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.junit.MvndTest;
import org.mvndaemon.mvnd.junit.TestParameters;
import org.mvndaemon.mvnd.junit.TestRegistry;
import org.mvndaemon.mvnd.junit.TestUtils;

@MvndTest(projectDir = "src/test/projects/parent-with-property")
class ParentWithPropertyTest {

    @Inject
    Client client;

    @Inject
    TestParameters parameters;

    @Inject
    TestRegistry registry;

    @Test
    void changeVersion() throws InterruptedException {
        final Path parentDir = parameters.getTestDir().resolve("project");

        /* Build */
        final TestClientOutput output1 = new TestClientOutput();
        client.execute(output1, "clean", "install", "-e").assertSuccess();
        output1.assertContainsMatchingSubsequence("Building parent 1", "Building child 1");
        assertDaemonRegistrySize(1);

        /* Wait, till the instance becomes idle */
        registry.awaitIdle(registry.getAll().get(0).getId());

        /* Upgrade the parent  */
        final Path parentPomPath = parentDir.resolve("pom.xml");
        TestUtils.replace(parentPomPath, "<revision>1</revision>", "<revision>2</revision>");

        /* Build */
        final TestClientOutput output2 = new TestClientOutput();
        client.execute(output2, "clean", "install", "-e").assertSuccess();
        output2.assertContainsMatchingSubsequence("Building parent 2", "Building child 2");
        assertDaemonRegistrySize(1);
    }

    private void assertDaemonRegistrySize(int size) {
        Assertions.assertThat(registry.getAll().size())
                .as("Daemon registry size should be " + size)
                .isEqualTo(size);
    }
}
