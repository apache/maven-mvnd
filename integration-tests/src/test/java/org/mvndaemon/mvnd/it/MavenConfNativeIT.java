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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.junit.MvndNativeTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MvndNativeTest(projectDir = "src/test/projects/maven-conf")
class MavenConfNativeIT {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void version() throws InterruptedException {
        final TestClientOutput o = new TestClientOutput();
        // this test also exercise the "-D foo=bar" syntax for defining properties
        client.execute(o, mvndParams().toArray(new String[0])).assertSuccess();
        String conf = parameters.mvndHome().resolve("mvn").resolve("conf").toString();
        assertTrue(
                o.getMessages().stream().anyMatch(m -> m.toString().contains(conf)), "Output should contain " + conf);
    }

    @Test
    void interpolation() throws InterruptedException {
        final TestClientOutput o = new TestClientOutput();
        client.execute(o, mvndParams("something").toArray(new String[0])).assertSuccess();
        String conf = parameters.multiModuleProjectDirectory().toString();
        assertTrue(
                o.getMessages().stream().anyMatch(m -> m.toString().contains(conf)), "Output should contain " + conf);
    }

    @Test
    void multiModuleProjectDirectory() throws InterruptedException {
        final TestClientOutput o = new TestClientOutput();
        client.execute(o, mvndParams("maven.multiModuleProjectDirectory").toArray(new String[0]))
                .assertSuccess();
        String conf = parameters.multiModuleProjectDirectory().toString();
        assertTrue(
                o.getMessages().stream().anyMatch(m -> m.toString().contains(conf)), "Output should contain " + conf);
    }

    protected List<String> mvndParams() {
        return mvndParams("maven.conf");
    }

    protected List<String> mvndParams(String expression) {
        return Arrays.asList(
                "org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate",
                "-D",
                "expression=" + expression,
                "-q",
                "-DforceStdout",
                "--raw-streams",
                "-X");
    }
}
