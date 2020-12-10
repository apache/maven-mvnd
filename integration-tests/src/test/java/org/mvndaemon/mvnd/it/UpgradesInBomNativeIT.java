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
import java.nio.file.Path;
import java.util.Arrays;
import javax.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.junit.ClientFactory;
import org.mvndaemon.mvnd.junit.MvndNativeTest;
import org.mvndaemon.mvnd.junit.TestParameters;
import org.mvndaemon.mvnd.junit.TestRegistry;
import org.mvndaemon.mvnd.junit.TestUtils;

@MvndNativeTest(projectDir = "src/test/projects/upgrades-in-bom")
public class UpgradesInBomNativeIT {

    @Inject
    TestParameters parameters;

    @Inject
    TestRegistry registry;

    @Inject
    ClientFactory clientFactory;

    @Test
    void upgrade() throws IOException, InterruptedException {
        assertDaemonRegistrySize(0);
        /* Install the dependencies */
        for (String artifactDir : Arrays.asList("project/hello-0.0.1", "project/hello-0.0.2-SNAPSHOT")) {
            final Client cl = clientFactory.newClient(parameters.cd(parameters.getTestDir().resolve(artifactDir)));
            final TestClientOutput output = new TestClientOutput();
            cl.execute(output, "clean", "install", "-e").assertSuccess();

            assertDaemonRegistrySize(1);
            final DaemonInfo d = registry.getAll().get(0);
            /* Wait, till the instance becomes idle */
            registry.awaitIdle(d.getUid());
            registry.killAll();
        }
        assertDaemonRegistrySize(0);

        /* Build the initial state of the test project */
        final Path parentDir = parameters.getTestDir().resolve("project/parent");
        final Client cl = clientFactory.newClient(parameters.cd(parentDir));
        {
            final TestClientOutput output = new TestClientOutput();
            cl.execute(output, "clean", "install", "-e").assertSuccess();
        }
        assertDaemonRegistrySize(1);

        final DaemonInfo d = registry.getAll().get(0);
        /* Wait, till the instance becomes idle */
        registry.awaitIdle(d.getUid());

        /* Upgrade the dependency  */
        final Path parentPomPath = parentDir.resolve("pom.xml");
        TestUtils.replace(parentPomPath, "<hello.version>0.0.1</hello.version>",
                "<hello.version>0.0.2-SNAPSHOT</hello.version>");
        /* Adapt the caller  */
        final Path useHelloPath = parentDir
                .resolve("module/src/main/java/org/mvndaemon/mvnd/test/upgrades/bom/module/UseHello.java");
        TestUtils.replace(useHelloPath, "new Hello().sayHello()", "new Hello().sayWisdom()");
        {
            final TestClientOutput output = new TestClientOutput();
            cl.execute(output, "clean", "install", "-e")
                    .assertSuccess();
        }
        assertDaemonRegistrySize(1);

    }

    private void assertDaemonRegistrySize(int size) {
        Assertions.assertThat(registry.getAll().size())
                .as("Daemon registry size should be " + size)
                .isEqualTo(size);
    }
}
