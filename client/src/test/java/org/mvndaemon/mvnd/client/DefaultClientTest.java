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
package org.mvndaemon.mvnd.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.common.logging.ClientOutput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultClientTest {

    /**
     * {@code --version} must complete without ever touching a daemon: with no fix, this would try to connect
     * to (and possibly spawn) one using the bogus paths below and fail or hang instead of returning quickly.
     */
    @Test
    void versionDoesNotConnectToDaemon(@TempDir Path dir) {
        final Path bogusHome = dir.resolve("no-such-mvnd-home");
        DaemonParameters parameters = new DaemonParameters() {
            @Override
            public Path mvndHome() {
                return bogusHome;
            }

            @Override
            public Path registry() {
                return bogusHome.resolve("registry.bin");
            }
        };
        DefaultClient client = new DefaultClient(parameters);

        ExecutionResult result = client.execute(new NoopClientOutput(), Arrays.asList("--version"));

        assertTrue(result.isSuccess());
        assertEquals(0, result.getExitCode());
    }

    private static final class NoopClientOutput implements ClientOutput {
        @Override
        public void setDaemonId(String daemonId) {}

        @Override
        public void setDaemonDispatch(Consumer<Message> sink) {}

        @Override
        public void setDaemonReceive(Consumer<Message> sink) {}

        @Override
        public void accept(Message message) {}

        @Override
        public void accept(List<Message> messages) {}

        @Override
        public void describeTerminal() {}

        @Override
        public int getTerminalWidth() {
            return 80;
        }

        @Override
        public void close() {}
    }

    @Test
    void blankArgumentsAreDropped() {
        List<String> args = new ArrayList<>(Arrays.asList("clean", "", "  ", "install"));
        DefaultClient.removeBlankArguments(args);
        assertEquals(Arrays.asList("clean", "install"), args);
    }

    @Test
    void builderFromMavenConfigIsNotOverridden() {
        List<String> args = new ArrayList<>(Arrays.asList("clean"));
        DefaultClient.addDefaultOption(Environment.MVND_BUILDER, args, Arrays.asList("-b", "turbo"), () -> "smart");
        assertEquals(Arrays.asList("clean"), args);
    }

    @Test
    void builderFromCommandLineWinsOverDefault() {
        List<String> args = new ArrayList<>(Arrays.asList("--builder=turbo", "clean"));
        DefaultClient.addDefaultOption(Environment.MVND_BUILDER, args, Arrays.asList(), () -> "smart");
        assertEquals(Arrays.asList("clean", "-b", "turbo"), args);
    }

    @Test
    void defaultBuilderIsAddedOtherwise() {
        List<String> args = new ArrayList<>(Arrays.asList("clean"));
        DefaultClient.addDefaultOption(Environment.MVND_BUILDER, args, Arrays.asList("-T4"), () -> "smart");
        assertEquals(Arrays.asList("clean", "-b", "smart"), args);
    }

    @Test
    void threadsFromMavenConfigAreNotOverridden() {
        List<String> args = new ArrayList<>(Arrays.asList("clean"));
        DefaultClient.addDefaultOption(Environment.MVND_THREADS, args, Arrays.asList("-T4"), () -> "7");
        assertEquals(Arrays.asList("clean"), args);

        DefaultClient.addDefaultOption(Environment.MVND_THREADS, args, Arrays.asList("--threads=1C"), () -> "7");
        assertEquals(Arrays.asList("clean"), args);

        DefaultClient.addDefaultOption(Environment.MVND_THREADS, args, Arrays.asList(), () -> "7");
        assertEquals(Arrays.asList("clean", "-T", "7"), args);
    }

    @Test
    void readsMavenConfig(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("maven.config");
        Files.write(config, Arrays.asList("-bturbo", "", "  -T 4  ", "-Dfoo=bar"));
        assertEquals(Arrays.asList("-bturbo", "-T", "4", "-Dfoo=bar"), DaemonParameters.readMavenConfig(config));
        assertEquals(Arrays.asList(), DaemonParameters.readMavenConfig(dir.resolve("missing")));
    }

    @Test
    void purgeKeepsLogsOfActiveDaemons(@TempDir Path dir) throws Exception {
        FileTime old = FileTime.from(Instant.now().minusSeconds(30L * 24 * 3600));
        for (String name : Arrays.asList("daemon-abc.log", "daemon-abc.out.log", "daemon-gone.log")) {
            Path p = Files.createFile(dir.resolve(name));
            Files.setLastModifiedTime(p, old);
        }
        DefaultClient.purgeLogs(dir, Duration.ofDays(7), Set.of("abc"));
        assertTrue(Files.exists(dir.resolve("daemon-abc.log")));
        assertTrue(Files.exists(dir.resolve("daemon-abc.out.log")));
        assertFalse(Files.exists(dir.resolve("daemon-gone.log")));
    }
}
