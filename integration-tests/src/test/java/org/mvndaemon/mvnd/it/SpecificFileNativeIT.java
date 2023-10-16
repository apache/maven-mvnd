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

import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.junit.ClientFactory;
import org.mvndaemon.mvnd.junit.MvndNativeTest;
import org.mvndaemon.mvnd.junit.TestParameters;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NOTE: there is no related JVM test because the support for the maven
 * multiModuleProjectDiscovery is done very early in the client in order
 * to be able to load the JVM parameters from the .mvn/jvm.config file.
 * Thus, there's point in setting because it would have to be done
 * in the test iself.
 */
@MvndNativeTest(projectDir = "src/test/projects/specific-file")
class SpecificFileNativeIT {

    @Inject
    ClientFactory clientFactory;

    @Inject
    TestParameters parameters;

    @Test
    void version() throws IOException, InterruptedException {
        TestClientOutput output = new TestClientOutput();

        Path prj1 = parameters.getTestDir().resolve("project/prj1").toAbsolutePath();
        Path prj2 = parameters.getTestDir().resolve("project/prj2").toAbsolutePath();

        Client cl = clientFactory.newClient(
                parameters.clearMavenMultiModuleProjectDirectory().cd(prj1));

        cl.execute(
                        output,
                        "org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate",
                        "-Dexpression=maven.multiModuleProjectDirectory",
                        "-f",
                        "../prj2/pom.xml",
                        "-e")
                .assertSuccess();
        assertTrue(
                output.getMessages().stream().anyMatch(m -> m.toString().contains(prj2.toString())),
                "Output should contain " + prj2);

        output.getMessages().clear();
        cl.execute(
                        output,
                        "org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate",
                        "-Dexpression=foo-bar",
                        "-f",
                        "../prj2/pom.xml",
                        "-e")
                .assertSuccess();
        assertTrue(
                output.getMessages().stream().anyMatch(m -> m.toString().contains("xx-prj2-xx")),
                "Output should contain " + prj2);
    }

    protected boolean isNative() {
        return true;
    }
}
