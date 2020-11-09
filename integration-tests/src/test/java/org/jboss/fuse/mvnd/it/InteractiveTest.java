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
import org.jboss.fuse.mvnd.assertj.TestClientOutput;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.DaemonParameters;
import org.jboss.fuse.mvnd.common.Message;
import org.jboss.fuse.mvnd.common.Message.Prompt;
import org.jboss.fuse.mvnd.junit.MvndTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MvndTest(projectDir = "src/test/projects/single-module")
public class InteractiveTest {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void versionsSet() throws IOException, InterruptedException {
        final String version = MvndTestUtil.version(parameters.multiModuleProjectDirectory().resolve("pom.xml"));
        Assertions.assertEquals("0.0.1-SNAPSHOT", version);

        final TestClientOutput o = new TestClientOutput() {
            @Override
            public void accept(Message m) {
                if (m instanceof Prompt) {
                    ((Prompt) m).getCallback().accept("0.1.0-SNAPSHOT");
                }
                super.accept(m);
            }
        };
        client.execute(o, "versions:set").assertSuccess();

        final String newVersion = MvndTestUtil.version(parameters.multiModuleProjectDirectory().resolve("pom.xml"));
        Assertions.assertEquals("0.1.0-SNAPSHOT", newVersion);
    }

}
