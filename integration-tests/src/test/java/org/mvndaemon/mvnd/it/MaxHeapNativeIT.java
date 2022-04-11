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

import java.nio.file.Path;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.junit.ClientFactory;
import org.mvndaemon.mvnd.junit.MvndNativeTest;
import org.mvndaemon.mvnd.junit.TestParameters;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MvndNativeTest(projectDir = "src/test/projects/max-heap")
public class MaxHeapNativeIT {

    @Inject
    ClientFactory clientFactory;

    @Inject
    TestParameters parameters;

    @Test
    void noXmxPassedByDefault() throws InterruptedException {
        Path dir = parameters.getTestDir().resolve("project/default-heap");
        final Client client = clientFactory.newClient(parameters.cd(dir));
        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "org.codehaus.gmaven:groovy-maven-plugin:2.1.1:execute",
                "-Dsource=System.out.println(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments())")
                .assertSuccess();
        assertTrue(output.getMessages().stream()
                .noneMatch(m -> m.toString().contains("-Xmx") || m.toString().contains("mvnd.maxHeapSize")),
                "Output must not contain -Xmx or mvnd.maxHeapSize but is:\n"
                        + output.getMessages().stream().map(Object::toString).collect(Collectors.joining("\n")));
    }

    @Test
    void XmxFromJvmConfig() throws InterruptedException {
        Path dir = parameters.getTestDir().resolve("project/jvm-config");
        final Client client = clientFactory.newClient(parameters.cd(dir));
        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "org.codehaus.gmaven:groovy-maven-plugin:2.1.1:execute",
                "-Dsource=System.out.println(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments())")
                .assertSuccess();
        assertTrue(output.getMessages().stream()
                .anyMatch(m -> m.toString().contains("-Xmx140M") && !m.toString().contains("mvnd.maxHeapSize")),
                "Output must contain -Xmx140M or mvnd.maxHeapSize=140M but is:\n"
                        + output.getMessages().stream().map(Object::toString).collect(Collectors.joining("\n")));
    }

    @Test
    void XmxFromMvndProperties() throws InterruptedException {
        Path dir = parameters.getTestDir().resolve("project/mvnd-props");
        final Client client = clientFactory.newClient(parameters.cd(dir));
        final TestClientOutput output = new TestClientOutput();
        client.execute(output, "org.codehaus.gmaven:groovy-maven-plugin:2.1.1:execute",
                "-Dsource=System.out.println(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments())")
                .assertSuccess();
        assertTrue(output.getMessages().stream()
                .anyMatch(m -> m.toString().contains("-Xmx130M") && m.toString().contains("mvnd.maxHeapSize=130M")),
                "Output must contain -Xmx130M or mvnd.maxHeapSize=130M but is:\n"
                        + output.getMessages().stream().map(Object::toString).collect(Collectors.joining("\n")));
    }

    protected boolean isNative() {
        return true;
    }

}
