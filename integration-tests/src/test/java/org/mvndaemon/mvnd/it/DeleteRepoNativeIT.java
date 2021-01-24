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
import javax.inject.Inject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.common.Os;
import org.mvndaemon.mvnd.junit.MvndNativeTest;
import org.mvndaemon.mvnd.junit.TestUtils;

@MvndNativeTest(projectDir = "src/test/projects/delete-repo")
public class DeleteRepoNativeIT {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void buildDeleteRepoAndRebuild() throws IOException, InterruptedException {
        Assumptions.assumeTrue(Os.current() != Os.WINDOWS,
                "Test disabled on windows because all jar files are locked, so we can't even delete the repository");

        final Path localMavenRepo = parameters.mavenRepoLocal();

        final TestClientOutput o1 = new TestClientOutput();
        client.execute(o1, "clean", "install", "-e", "-B").assertSuccess();

        TestUtils.deleteDir(localMavenRepo, Os.current() != Os.WINDOWS);
        if (Os.current() == Os.WINDOWS) {
            // On windows, we're using the service watcher which polls every 2s by default
            Thread.sleep(2500);
        }

        final TestClientOutput o2 = new TestClientOutput();
        client.execute(o2, "clean", "install", "-e", "-B").assertSuccess();
    }

}
