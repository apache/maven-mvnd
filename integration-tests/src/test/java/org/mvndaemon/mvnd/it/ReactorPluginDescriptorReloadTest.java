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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.junit.MvndNativeTest;
import org.mvndaemon.mvnd.junit.TestUtils;

/**
 * Reproduces the case where a reactor plugin is rebuilt between two daemon invocations without being installed
 * (so it is resolved as a {@code target/classes} directory). The plugin descriptor (e.g. parameter default values,
 * the {@code threadSafe} flag, ...) is cached independently from the plugin realm and must be evicted as well,
 * otherwise the daemon keeps serving the stale descriptor. See
 * <a href="https://github.com/apache/maven-mvnd/issues/877">#877</a>.
 */
@MvndNativeTest(projectDir = "src/test/projects/module-and-plugin")
class ReactorPluginDescriptorReloadTest {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void reloadDescriptorOnReactorPluginChange() throws IOException, InterruptedException {
        /* Note: we run "package" (not "install"), so the plugin is consumed from the reactor as a
         * target/classes directory rather than from a freshly installed jar in the local repository. */
        final Path helloPropertyPath =
                parameters.multiModuleProjectDirectory().resolve("hello/target/hello.property.txt");

        /* Build #1: the "property" parameter has its default value "Hello" */
        {
            final TestClientOutput output = new TestClientOutput();
            client.execute(output, "clean", "package", "-e").assertSuccess();
            Assertions.assertThat(helloPropertyPath)
                    .usingCharset(StandardCharsets.UTF_8)
                    .hasContent("Hello");
        }

        /* Build #2: change the parameter default value (a plugin descriptor change that does not alter the
         * mojo's byte code path) and verify the daemon picks it up instead of reusing the stale descriptor. */
        {
            final Path mojoPath = parameters
                    .multiModuleProjectDirectory()
                    .resolve("plugin/src/main/java/org/mvndaemon/mvnd/test/module/plugin/mojo/HelloMojo.java");
            TestUtils.replace(mojoPath, "defaultValue = \"Hello\"", "defaultValue = \"Goodbye\"");

            Thread.sleep(200); // avoid intermittent timestamp resolution issues on some filesystems

            final TestClientOutput output = new TestClientOutput();
            client.execute(output, "clean", "package", "-e").assertSuccess();
            Assertions.assertThat(helloPropertyPath)
                    .usingCharset(StandardCharsets.UTF_8)
                    .hasContent("Goodbye");
        }
    }
}
