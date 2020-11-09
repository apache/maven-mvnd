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
import javax.inject.Inject;
import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.assertj.TestClientOutput;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.DaemonParameters;
import org.jboss.fuse.mvnd.common.DaemonInfo;
import org.jboss.fuse.mvnd.junit.MvndNativeTest;
import org.jboss.fuse.mvnd.junit.TestRegistry;
import org.junit.jupiter.api.Test;

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
        Assertions.assertThat(registry.getAll().size()).isEqualTo(0);

        final TestClientOutput o = new TestClientOutput();
        client.execute(o, "-v").assertSuccess();
        Assertions.assertThat(registry.getAll().size()).isEqualTo(1);
        DaemonInfo daemon = registry.getAll().iterator().next();
        assertTrue(daemon.getOptions().contains("daemon.core.extensions=io.takari.aether:takari-local-repository:0.11.3"));

        registry.awaitIdle(daemon.getUid());

        client.execute(o, "-v").assertSuccess();
        Assertions.assertThat(registry.getAll().size()).isEqualTo(1);
    }

}
