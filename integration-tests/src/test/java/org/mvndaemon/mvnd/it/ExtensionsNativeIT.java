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
package org.mvndaemon.mvnd.it;

import java.io.IOException;
import javax.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.junit.MvndNativeTest;
import org.mvndaemon.mvnd.junit.TestRegistry;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MvndNativeTest(projectDir = "src/test/projects/extensions")
public class ExtensionsNativeIT {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Inject
    TestRegistry registry;

    @Test
    void version() throws IOException, InterruptedException {
        registry.killAll();
        assertDaemonRegistrySize(0);

        final TestClientOutput o = new TestClientOutput();
        client.execute(o, "-v").assertSuccess();
        assertDaemonRegistrySize(1);
        DaemonInfo daemon = registry.getAll().iterator().next();
        assertTrue(daemon.getOptions().contains(
                "mvnd.coreExtensions=io.takari.aether:takari-local-repository:[0.11.3,);fr.jcgay.maven:maven-profiler:3.0"));

        registry.awaitIdle(daemon.getUid());

        client.execute(o, "-v").assertSuccess();
        assertDaemonRegistrySize(1);
    }

    private void assertDaemonRegistrySize(int size) {
        Assertions.assertThat(registry.getAll().size())
                .as("Daemon registry size should be " + size)
                .isEqualTo(size);
    }

}
