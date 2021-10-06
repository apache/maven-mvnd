/*
 * Copyright 2021 the original author or authors.
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
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.junit.ClientFactory;
import org.mvndaemon.mvnd.junit.MvndNativeTest;
import org.mvndaemon.mvnd.junit.TestParameters;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MvndNativeTest(projectDir = "src/test/projects/specific-file")
public class SpecificFileNativeIT {

    @Inject
    ClientFactory clientFactory;

    @Inject
    TestParameters parameters;

    @Test
    void version() throws IOException, InterruptedException {
        TestClientOutput output = new TestClientOutput();


        Path prj1 = parameters.getTestDir().resolve("project/prj1").toAbsolutePath();
        Path prj2 = parameters.getTestDir().resolve("project/prj2").toAbsolutePath();

        Client cl = clientFactory.newClient(parameters.clearMavenMultiModuleProjectDirectory().cd(prj1));

        cl.execute(output, "org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate",
                "-Dexpression=maven.multiModuleProjectDirectory", "-f", "../prj2/pom.xml", "-e").assertSuccess();
        assertTrue(output.getMessages().stream()
                .anyMatch(m -> m.toString().contains(prj2.toString())), "Output should contain " + prj2);
    }

    protected boolean isNative() {
        return true;
    }
}
