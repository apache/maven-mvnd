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
import org.jboss.fuse.mvnd.junit.MvndNativeTest;
import org.jboss.fuse.mvnd.junit.MvndTestExtension;
import org.junit.jupiter.api.Test;

@MvndNativeTest(projectDir = MvndTestExtension.TEMP_EXTERNAL)
public class VersionNativeIT {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void version() throws IOException, InterruptedException {
        final TestClientOutput output = new TestClientOutput();

        client.execute(output, "-v").assertSuccess();

        output.assertContainsMatchingSubsequence(
                "\\QMaven Daemon "
                        + System.getProperty("project.version")
                        + "-" + System.getProperty("os.detected.name")
                        + "-" + System.getProperty("os.detected.arch")
                        + "\\E",
                "\\QMaven home: " + parameters.mvndHome() + "\\E");
    }
}
