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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.junit.MvndNativeTest;

@MvndNativeTest(projectDir = "src/test/projects/invoker")
class InvokerNativeIT {

    @Inject
    Client client;

    @Inject
    DaemonParameters parameters;

    @Test
    void cleanInstall() throws IOException, InterruptedException {

        final Path helloPath =
                parameters.multiModuleProjectDirectory().resolve("target/it/invoke-hello/target/hello.txt");
        try {
            Files.deleteIfExists(helloPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not delete " + helloPath);
        }
        final Path logPath = parameters.multiModuleProjectDirectory().resolve("target/it/invoke-hello/build.log");
        try {
            Files.deleteIfExists(logPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not delete " + helloPath);
        }

        final TestClientOutput output = new TestClientOutput();
        client.execute(
                        output,
                        "clean",
                        "verify",
                        "-e",
                        "-Dmvnd.log.level=DEBUG",
                        "-Dmaven.experimental.buildconsumer=false")
                .assertSuccess();

        Assertions.assertThat(helloPath).exists();
        Assertions.assertThat(helloPath).usingCharset(StandardCharsets.UTF_8).hasContent("Hello");

        Assertions.assertThat(logPath).exists();
        final List<String> logLines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
        Assertions.assertThat(logLines.size()).isGreaterThan(0);

        final String lastLine = logLines.get(logLines.size() - 1);
        Assertions.assertThat(lastLine).matches(Pattern.compile("\\QFinished post-build script: \\E.*verify.groovy$"));
    }
}
