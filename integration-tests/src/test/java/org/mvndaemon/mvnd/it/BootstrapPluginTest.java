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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.junit.MvndTest;
import org.mvndaemon.mvnd.junit.TestRegistry;

import static org.junit.jupiter.api.Assertions.fail;

@MvndTest(projectDir = "src/test/projects/bootstrap-plugin")
@DisabledOnOs(OS.WINDOWS)
class BootstrapPluginTest {

    @Inject
    Client client;

    @Inject
    TestRegistry registry;

    @Inject
    DaemonParameters parameters;

    @Test
    void cleanInstall() throws IOException, InterruptedException {
        assertDaemonRegistrySize(0);

        for (int i = 0; i < 5; i++) {
            final TestClientOutput output = new TestClientOutput();
            try {
                client.execute(output, "clean", "install", "-e", "-Dmvnd.log.level=DEBUG")
                        .assertSuccess();
            } catch (Exception e) {
                output.getMessages().forEach(System.out::println);
                fail("Error", e);
            }
            assertDaemonRegistrySize(1);
            /* Wait, till the instance becomes idle */
            DaemonInfo d = registry.getAll().get(0);
            registry.awaitIdle(d.getId());
        }
    }

    private void assertDaemonRegistrySize(int size) {
        Assertions.assertThat(registry.getAll().size())
                .as("Daemon registry size should be " + size)
                .isEqualTo(size);
    }
}
