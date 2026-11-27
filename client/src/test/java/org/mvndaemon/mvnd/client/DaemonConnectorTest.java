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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mvndaemon.mvnd.common.DaemonException;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.common.DaemonRegistry;
import org.mvndaemon.mvnd.common.DaemonState;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.common.logging.ClientOutput;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DaemonConnectorTest {

    @Test
    void limitsConcurrentStartsAndAllowsCancellation(@TempDir Path dir) throws Exception {
        var parameters = new DaemonParameters()
                .derive(b -> b.put(Environment.MVND_MAX_DAEMONS, "1")
                        .put(Environment.MVND_HOME, dir.toString())
                        .put(Environment.JAVA_HOME, System.getProperty("java.home")));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var waiting = new CountDownLatch(1);
        var cancel = new AtomicReference<Consumer<Message>>();
        var starts = new AtomicInteger();
        var failure = new AtomicReference<Throwable>();
        try (var registry = new DaemonRegistry(dir.resolve("registry.bin"))) {
            DaemonConnector connector = new DaemonConnector(parameters, registry) {
                @Override
                public DaemonClientConnection startDaemon(String id, ClientOutput output) {
                    starts.incrementAndGet();
                    entered.countDown();
                    try {
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    registry.store(new DaemonInfo(
                            id,
                            System.getProperty("java.home"),
                            dir.toString(),
                            (int) ProcessHandle.current().pid(),
                            "",
                            new byte[16],
                            "en",
                            List.of(),
                            DaemonState.Busy,
                            0,
                            0));
                    return null;
                }
            };
            var output = new QuietOutput() {
                @Override
                public void setDaemonDispatch(Consumer<Message> sink) {
                    cancel.set(sink);
                }

                @Override
                public void accept(Message message) {
                    waiting.countDown();
                }
            };
            Thread first = new Thread(() -> {
                try {
                    connector.connect(new QuietOutput());
                } catch (Throwable e) {
                    failure.set(e);
                }
            });
            Thread second = new Thread(() -> {
                try {
                    connector.connect(output);
                    failure.set(new AssertionError("Second client started another daemon"));
                } catch (DaemonException.InterruptedException expected) {
                    // Waiting for a slot is cancellable.
                } catch (Throwable e) {
                    failure.set(e);
                }
            });
            try {
                first.start();
                assertTrue(entered.await(5, TimeUnit.SECONDS), () -> String.valueOf(failure.get()));
                second.start();
                assertTrue(waiting.await(5, TimeUnit.SECONDS));
                release.countDown();
                first.join(5000);
                assertFalse(first.isAlive());
                cancel.get().accept(Message.BareMessage.CANCEL_BUILD_SINGLETON);
                second.join(5000);
                assertFalse(second.isAlive());
                assertEquals(1, starts.get());
                assertEquals(null, failure.get());
                registry.remove(registry.getAll().get(0).getId());
                connector.connect(new QuietOutput());
                assertEquals(2, starts.get());
            } finally {
                release.countDown();
                first.interrupt();
                second.interrupt();
                first.join(5000);
                second.join(5000);
            }
        }
    }

    @Test
    void refusesIncompatibleIdleDaemonsAndNegativeLimits(@TempDir Path dir) {
        var parameters = new DaemonParameters()
                .derive(b -> b.put(Environment.MVND_MAX_DAEMONS, "1")
                        .put(Environment.MVND_HOME, dir.toString())
                        .put(Environment.JAVA_HOME, System.getProperty("java.home")));
        try (var registry = new DaemonRegistry(dir.resolve("registry.bin"))) {
            registry.store(new DaemonInfo(
                    "incompatible",
                    "/other/jdk",
                    dir.toString(),
                    (int) ProcessHandle.current().pid(),
                    "",
                    new byte[16],
                    "en",
                    List.of(),
                    DaemonState.Idle,
                    0,
                    0));
            var error = assertThrows(
                    DaemonException.class, () -> new DaemonConnector(parameters, registry).connect(new QuietOutput()));
            assertTrue(error.getMessage().contains("incompatible idle daemons"));
            var negative = parameters.derive(b -> b.put(Environment.MVND_MAX_DAEMONS, "-1"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new DaemonConnector(negative, registry).connect(new QuietOutput()));
        }
    }

    @Test
    void waitsForAndReusesBusyDaemon(@TempDir Path dir) throws Exception {
        var parameters = new DaemonParameters()
                .derive(b -> b.put(Environment.MVND_MAX_DAEMONS, "1")
                        .put(Environment.MVND_HOME, dir.toString())
                        .put(Environment.JAVA_HOME, System.getProperty("java.home")));
        try (var server = java.nio.channels.ServerSocketChannel.open();
                var registry = new DaemonRegistry(dir.resolve("registry.bin"))) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            registry.store(new DaemonInfo(
                    "existing",
                    System.getProperty("java.home"),
                    dir.toString(),
                    (int) ProcessHandle.current().pid(),
                    org.mvndaemon.mvnd.common.SocketFamily.toString(server.getLocalAddress()),
                    new byte[16],
                    "en",
                    parameters.getDaemonOpts(),
                    DaemonState.Busy,
                    0,
                    0));
            var waits = new AtomicInteger();
            var output = new QuietOutput() {
                @Override
                public void accept(Message message) {
                    waits.incrementAndGet();
                    registry.markState("existing", DaemonState.Idle);
                }
            };
            var connector = new DaemonConnector(parameters, registry) {
                @Override
                public DaemonClientConnection startDaemon(String id, ClientOutput out) {
                    throw new AssertionError("Must reuse the existing daemon");
                }
            };
            try (var connection = connector.connect(output);
                    var accepted = server.accept()) {
                assertEquals("existing", connection.getDaemon().getId());
                assertEquals(1, waits.get());
                assertEquals(1, registry.getAll().size());
            }
        }
    }

    private static class QuietOutput implements ClientOutput {
        public void setDaemonId(String id) {}

        public void setDaemonDispatch(Consumer<Message> sink) {}

        public void setDaemonReceive(Consumer<Message> sink) {}

        public void accept(Message message) {}

        public void accept(List<Message> messages) {}

        public void describeTerminal() {}

        public int getTerminalWidth() {
            return 80;
        }

        public void close() {}
    }

    @Test
    public void newId() {
        String id = DaemonConnector.newId();
        assertNotNull(id);
        assertEquals(8, id.length());
    }

    @Test
    public void splitsJvmArgs() {
        assertEquals(List.of(), DaemonConnector.splitJvmArgs(""));
        assertEquals(List.of(), DaemonConnector.splitJvmArgs("   "));
        assertEquals(List.of("-Xmx1g", "-Dfoo=bar"), DaemonConnector.splitJvmArgs("-Xmx1g -Dfoo=bar"));
        assertEquals(List.of("-Xmx1g", "-Dfoo=bar"), DaemonConnector.splitJvmArgs("  -Xmx1g   -Dfoo=bar "));
        assertEquals(
                List.of("-Djavax.net.ssl.trustStore=/my trust/store.jks", "-Dx=y"),
                DaemonConnector.splitJvmArgs("\"-Djavax.net.ssl.trustStore=/my trust/store.jks\" -Dx=y"));
        assertEquals(
                List.of("-Djavax.net.ssl.trustStore=/my trust/store.jks"),
                DaemonConnector.splitJvmArgs("-Djavax.net.ssl.trustStore=\"/my trust/store.jks\""));
        assertEquals(List.of("-Da=b c", "-Dd=e f"), DaemonConnector.splitJvmArgs("'-Da=b c' -Dd='e f'"));
        assertEquals(List.of("-Da=it's", "x"), DaemonConnector.splitJvmArgs("\"-Da=it's\" x"));
        assertEquals(List.of("", "a"), DaemonConnector.splitJvmArgs("\"\" a"));
        assertEquals(List.of("-Da=b c"), DaemonConnector.splitJvmArgs("-Da=\"b c"));
    }

    @Test
    public void connectsOnlyToLoopback() throws Exception {
        assertDoesNotThrow(
                () -> DaemonConnector.checkLocal(new InetSocketAddress(InetAddress.getLoopbackAddress(), 4000)));
        assertDoesNotThrow(() -> DaemonConnector.checkLocal(
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 4000)));
        assertThrows(
                DaemonException.ConnectException.class,
                () -> DaemonConnector.checkLocal(
                        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), 4000)));
        assertThrows(
                DaemonException.ConnectException.class,
                () -> DaemonConnector.checkLocal(InetSocketAddress.createUnresolved("example.com", 4000)));
    }

    @Test
    public void detectsDeadProcesses() throws Exception {
        assertTrue(DaemonConnector.isAlive((int) ProcessHandle.current().pid()));
        Process p =
                new ProcessBuilder("java", "-version").redirectErrorStream(true).start();
        int pid = (int) p.pid();
        p.waitFor();
        assertFalse(DaemonConnector.isAlive(pid));
    }
}
