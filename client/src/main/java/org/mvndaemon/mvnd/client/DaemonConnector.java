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

import static java.lang.Thread.sleep;
import static org.mvndaemon.mvnd.common.DaemonState.Canceled;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.mvndaemon.mvnd.common.DaemonCompatibilitySpec;
import org.mvndaemon.mvnd.common.DaemonCompatibilitySpec.Result;
import org.mvndaemon.mvnd.common.DaemonConnection;
import org.mvndaemon.mvnd.common.DaemonException;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.common.DaemonRegistry;
import org.mvndaemon.mvnd.common.DaemonState;
import org.mvndaemon.mvnd.common.DaemonStopEvent;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.MavenDaemon;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.common.Os;
import org.mvndaemon.mvnd.common.SocketFamily;
import org.mvndaemon.mvnd.common.logging.ClientOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File origin:
 * https://github.com/gradle/gradle/blob/v5.6.2/subprojects/launcher/src/main/java/org/gradle/launcher/daemon/client/DefaultDaemonConnector.java
 */
public class DaemonConnector {

    public static final int DEFAULT_CONNECT_TIMEOUT = 30000;
    public static final int CANCELED_WAIT_TIMEOUT = 3000;
    private static final int CONNECT_TIMEOUT = 10000;

    private static final Logger LOGGER = LoggerFactory.getLogger(DaemonConnector.class);

    private final DaemonRegistry registry;
    private final DaemonParameters parameters;

    public DaemonConnector(DaemonParameters parameters, DaemonRegistry registry) {
        this.parameters = parameters;
        this.registry = registry;
    }

    public DaemonClientConnection maybeConnect(DaemonCompatibilitySpec constraint) {
        return findConnection(getCompatibleDaemons(registry.getAll(), constraint));
    }

    public DaemonClientConnection maybeConnect(DaemonInfo daemon) {
        try {
            return connectToDaemon(daemon, new CleanupOnStaleAddress(daemon), false);
        } catch (DaemonException.ConnectException e) {
            LOGGER.debug("Cannot connect to daemon {} due to {}. Ignoring.", daemon, e);
        }
        return null;
    }

    public DaemonClientConnection connect(ClientOutput output) {
        if (parameters.noDaemon()) {
            return connectNoDaemon();
        }

        final DaemonCompatibilitySpec constraint =
                new DaemonCompatibilitySpec(parameters.javaHome(), parameters.getDaemonOpts());
        output.accept(Message.buildStatus("Looking up daemon..."));
        Map<Boolean, List<DaemonInfo>> idleBusy =
                registry.getAll().stream().collect(Collectors.groupingBy(di -> di.getState() == DaemonState.Idle));
        final Collection<DaemonInfo> idleDaemons = idleBusy.getOrDefault(true, Collections.emptyList());
        final Collection<DaemonInfo> busyDaemons = idleBusy.getOrDefault(false, Collections.emptyList());

        // Check to see if there are any compatible idle daemons
        DaemonClientConnection connection = connectToIdleDaemon(idleDaemons, constraint);
        if (connection != null) {
            return connection;
        }

        // Check to see if there are any compatible canceled daemons and wait to see if one becomes idle
        connection = connectToCanceledDaemon(busyDaemons, constraint);
        if (connection != null) {
            return connection;
        }

        // No compatible daemons available - start a new daemon
        final String daemonId = newId();
        String message = handleStopEvents(daemonId, idleDaemons, busyDaemons);
        output.accept(Message.buildStatus(message));
        return startDaemon(daemonId, output);
    }

    private DaemonClientConnection connectNoDaemon() {
        if (Environment.isNative()) {
            throw new UnsupportedOperationException(
                    "The " + Environment.MVND_NO_DAEMON.getProperty() + " property is not supported in native mode.");
        }
        String daemon = ProcessHandle.current().pid() + "-" + System.currentTimeMillis();
        Properties properties = new Properties();
        properties.put(
                Environment.JAVA_HOME.getProperty(), parameters.javaHome().toString());
        properties.put(Environment.USER_DIR.getProperty(), parameters.userDir().toString());
        properties.put(
                Environment.USER_HOME.getProperty(), parameters.userHome().toString());
        properties.put(
                Environment.MVND_HOME.getProperty(), parameters.mvndHome().toString());
        properties.put(Environment.MVND_ID.getProperty(), daemon);
        properties.put(
                Environment.MVND_DAEMON_STORAGE.getProperty(),
                parameters.daemonStorage().toString());
        properties.put(
                Environment.MVND_REGISTRY.getProperty(), parameters.registry().toString());
        properties.putAll(parameters.getDaemonOptsMap());
        Environment.setProperties(properties);
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        Thread serverThread = new Thread(() -> {
            try {
                Class<?> clazz = getClass().getClassLoader().loadClass("org.mvndaemon.mvnd.daemon.Server");
                try (AutoCloseable server =
                        (AutoCloseable) clazz.getConstructor().newInstance()) {
                    ((Runnable) server).run();
                }
            } catch (Throwable t) {
                throwable.set(t);
            }
        });
        serverThread.start();
        long start = System.currentTimeMillis();
        do {
            DaemonClientConnection daemonConnection = connectToDaemonWithId(daemon, true);
            if (daemonConnection != null) {
                return daemonConnection;
            }
            try {
                sleep(50L);
            } catch (InterruptedException e) {
                throw new DaemonException.InterruptedException(e);
            }
        } while (serverThread.isAlive() && System.currentTimeMillis() - start < DEFAULT_CONNECT_TIMEOUT);
        throw new RuntimeException("Unable to connect to internal daemon", throwable.get());
    }

    private String handleStopEvents(
            String daemonId, Collection<DaemonInfo> idleDaemons, Collection<DaemonInfo> busyDaemons) {
        final List<DaemonStopEvent> stopEvents = registry.getStopEvents();

        // Clean up old stop events
        long time = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);

        List<DaemonStopEvent> oldStopEvents =
                stopEvents.stream().filter(e -> e.getTimestamp() < time).collect(Collectors.toList());
        registry.removeStopEvents(oldStopEvents);

        final List<DaemonStopEvent> recentStopEvents = stopEvents.stream()
                .filter(e -> e.getTimestamp() >= time)
                .collect(Collectors.groupingBy(DaemonStopEvent::getDaemonId, Collectors.minBy(this::compare)))
                .values()
                .stream()
                .map(Optional::get)
                .collect(Collectors.toList());
        for (DaemonStopEvent stopEvent : recentStopEvents) {
            LOGGER.debug(
                    "Previous Daemon ({}) stopped at {} {}",
                    stopEvent.getDaemonId(),
                    stopEvent.getTimestamp(),
                    stopEvent.getReason());
        }

        return generate(daemonId, busyDaemons.size(), idleDaemons.size(), recentStopEvents.size());
    }

    public static String generate(
            final String daemonId, final int numBusy, final int numIncompatible, final int numStopped) {
        final int totalUnavailableDaemons = numBusy + numIncompatible + numStopped;
        if (totalUnavailableDaemons > 0) {
            final List<String> reasons = new ArrayList<>();
            if (numBusy > 0) {
                reasons.add(numBusy + " busy");
            }
            if (numIncompatible > 0) {
                reasons.add(numIncompatible + " incompatible");
            }
            if (numStopped > 0) {
                reasons.add(numStopped + " stopped");
            }
            return "Starting new daemon " + daemonId + ", "
                    + String.join(" and ", reasons) + " daemon" + (totalUnavailableDaemons > 1 ? "s" : "")
                    + " could not be reused, use --status for details";
        } else {
            return "Starting new daemon " + daemonId + " (subsequent builds will be faster)...";
        }
    }

    private int compare(DaemonStopEvent event1, DaemonStopEvent event2) {
        if (event1.getStatus() != null && event2.getStatus() == null) {
            return -1;
        } else if (event1.getStatus() == null && event2.getStatus() != null) {
            return 1;
        } else if (event1.getStatus() != null && event2.getStatus() != null) {
            return event2.getStatus().compareTo(event1.getStatus());
        }
        return 0;
    }

    private DaemonClientConnection connectToIdleDaemon(
            Collection<DaemonInfo> idleDaemons, DaemonCompatibilitySpec constraint) {
        final List<DaemonInfo> compatibleIdleDaemons = getCompatibleDaemons(idleDaemons, constraint);
        LOGGER.debug("Found {} idle daemons, {} compatibles", idleDaemons.size(), compatibleIdleDaemons.size());
        return findConnection(compatibleIdleDaemons);
    }

    private DaemonClientConnection connectToCanceledDaemon(
            Collection<DaemonInfo> busyDaemons, DaemonCompatibilitySpec constraint) {
        DaemonClientConnection connection = null;
        List<DaemonInfo> canceledBusy =
                busyDaemons.stream().filter(di -> di.getState() == Canceled).collect(Collectors.toList());
        final List<DaemonInfo> compatibleCanceledDaemons = getCompatibleDaemons(canceledBusy, constraint);
        LOGGER.debug(
                "Found {} busy daemons, {} cancelled, {} compatibles",
                busyDaemons.size(),
                canceledBusy.size(),
                compatibleCanceledDaemons.size());
        if (!compatibleCanceledDaemons.isEmpty()) {
            LOGGER.debug("Waiting for daemons with canceled builds to become available");
            long start = System.currentTimeMillis();
            while (connection == null && System.currentTimeMillis() - start < CANCELED_WAIT_TIMEOUT) {
                try {
                    sleep(200);
                    connection = connectToIdleDaemon(registry.getIdle(), constraint);
                } catch (InterruptedException e) {
                    throw new DaemonException.InterruptedException(e);
                }
            }
        }
        return connection;
    }

    private List<DaemonInfo> getCompatibleDaemons(Iterable<DaemonInfo> daemons, DaemonCompatibilitySpec constraint) {
        List<DaemonInfo> compatibleDaemons = new LinkedList<>();
        for (DaemonInfo daemon : daemons) {
            final Result result = constraint.isSatisfiedBy(daemon);
            if (result.isCompatible()) {
                compatibleDaemons.add(daemon);
            } else {
                LOGGER.debug(
                        "{} daemon {} does not match the desired criteria: " + result.getWhy(),
                        daemon.getState(),
                        daemon.getId());
            }
        }
        return compatibleDaemons;
    }

    private DaemonClientConnection findConnection(List<DaemonInfo> compatibleDaemons) {
        for (DaemonInfo daemon : compatibleDaemons) {
            try {
                return connectToDaemon(daemon, new CleanupOnStaleAddress(daemon), false);
            } catch (DaemonException.ConnectException e) {
                LOGGER.debug("Cannot connect to daemon {} due to {}. Trying a different daemon...", daemon, e);
            }
        }
        return null;
    }

    public DaemonClientConnection startDaemon(String daemonId, ClientOutput output) {
        final Process process = startDaemonProcess(daemonId, output);
        LOGGER.debug("Started Maven daemon {}", daemonId);
        long start = System.currentTimeMillis();
        do {
            DaemonClientConnection daemonConnection = connectToDaemonWithId(daemonId, true);
            if (daemonConnection != null) {
                return daemonConnection;
            }
            try {
                sleep(200L);
            } catch (InterruptedException e) {
                throw new DaemonException.InterruptedException(e);
            }
        } while (process.isAlive() && System.currentTimeMillis() - start < DEFAULT_CONNECT_TIMEOUT);
        DaemonDiagnostics diag = new DaemonDiagnostics(daemonId, parameters);
        throw new DaemonException.ConnectException(
                "Timeout waiting to connect to the Maven daemon.\n" + diag.describe());
    }

    static String newId() {
        return String.format("%08x", new Random().nextInt());
    }

    private Process startDaemonProcess(String daemonId, ClientOutput output) {
        final Path mvndHome = parameters.mvndHome();
        final Path workingDir = parameters.userDir();
        String command = "";
        try (DirectoryStream<Path> jarPaths = Files.newDirectoryStream(mvndHome.resolve("mvn/lib/ext"))) {
            List<String> args = new ArrayList<>();
            // executable
            final String java = Os.current().isUnixLike() ? "bin/java" : "bin\\java.exe";
            args.add(parameters.javaHome().resolve(java).toString());
            // classpath
            String mvndCommonPath = null;
            String mvndAgentPath = null;
            for (Path jar : jarPaths) {
                String s = jar.getFileName().toString();
                if (s.endsWith(".jar")) {
                    if (s.startsWith("mvnd-common-")) {
                        mvndCommonPath = jar.toString();
                    } else if (s.startsWith("mvnd-agent-")) {
                        mvndAgentPath = jar.toString();
                    }
                }
            }
            if (mvndCommonPath == null) {
                throw new IllegalStateException("Could not find mvnd-common jar in mvn/lib/ext/");
            }
            if (mvndAgentPath == null) {
                throw new IllegalStateException("Could not find mvnd-agent jar in mvn/lib/ext/");
            }
            args.add("-classpath");
            args.add(mvndCommonPath + File.pathSeparator + mvndAgentPath);
            args.add("-javaagent:" + mvndAgentPath);
            // debug options
            if (parameters.property(Environment.MVND_DEBUG).asBoolean()) {
                String address =
                        parameters.property(Environment.MVND_DEBUG_ADDRESS).asString();
                String host;
                String port;
                int column = address.indexOf(':');
                if (column >= 0) {
                    host = address.substring(0, column);
                    port = address.substring(column + 1);
                } else {
                    host = "localhost";
                    port = address;
                }
                if (!port.matches("[0-9]+")) {
                    throw new IllegalArgumentException("Wrong debug address syntax: " + address);
                }
                int iPort = Integer.parseInt(port);
                if (iPort == 0) {
                    try (ServerSocketChannel channel = SocketFamily.inet.openServerSocket()) {
                        iPort = ((InetSocketAddress) channel.getLocalAddress()).getPort();
                    } catch (IOException e) {
                        throw new IllegalStateException("Unable to find a free debug port", e);
                    }
                }
                address = host + ":" + iPort;
                output.accept(Message.buildStatus("Daemon listening for debugger on address: " + address));
                args.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + address);
            }
            // jvm args
            String jvmArgs = parameters.jvmArgs();
            if (jvmArgs != null) {
                for (String arg : jvmArgs.split(" ")) {
                    if (!arg.isEmpty()) {
                        args.add(arg);
                    }
                }
            }
            // memory
            String minHeapSize = parameters.minHeapSize();
            if (minHeapSize != null) {
                args.add("-Xms" + minHeapSize);
            }
            String maxHeapSize = parameters.maxHeapSize();
            if (maxHeapSize != null) {
                args.add("-Xmx" + maxHeapSize);
            }
            String threadStackSize = parameters.threadStackSize();
            if (threadStackSize != null) {
                args.add("-Xss" + threadStackSize);
            }

            Environment.MVND_HOME.addSystemProperty(args, mvndHome.toString());
            args.add("-Dmaven.home=" + mvndHome.resolve("mvn"));
            args.add("-Dmaven.conf=" + mvndHome.resolve("mvn/conf"));

            Environment.MVND_JAVA_HOME.addSystemProperty(
                    args, parameters.javaHome().toString());
            Environment.LOGBACK_CONFIGURATION_FILE.addSystemProperty(
                    args, parameters.logbackConfigurationPath().toString());
            Environment.MVND_ID.addSystemProperty(args, daemonId);
            Environment.MVND_DAEMON_STORAGE.addSystemProperty(
                    args, parameters.daemonStorage().toString());
            Environment.MVND_REGISTRY.addSystemProperty(
                    args, parameters.registry().toString());
            Environment.MVND_SOCKET_FAMILY.addSystemProperty(
                    args,
                    parameters
                            .socketFamily()
                            .orElseGet(() -> getJavaVersion() >= 16.0f ? SocketFamily.unix : SocketFamily.inet)
                            .toString());
            parameters.discriminatingSystemProperties(args);
            args.add(MavenDaemon.class.getName());
            command = String.join(" ", args);

            LOGGER.debug(
                    "Starting daemon process: id = {}, workingDir = {}, daemonArgs: {}", daemonId, workingDir, command);
            Path daemonOutLog = parameters.daemonOutLog(daemonId);
            Files.writeString(
                    daemonOutLog,
                    "Starting daemon process: id = " + daemonId + ", workingDir = " + workingDir + ", daemonArgs: "
                            + command,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            ProcessBuilder.Redirect redirect = ProcessBuilder.Redirect.appendTo(daemonOutLog.toFile());
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder
                    .environment()
                    .put(Environment.JDK_JAVA_OPTIONS.getEnvironmentVariable(), parameters.jdkJavaOpts());
            Process process = processBuilder
                    .directory(workingDir.toFile())
                    .command(args)
                    .redirectOutput(redirect)
                    .redirectError(redirect)
                    .start();
            return process;
        } catch (Exception e) {
            throw new DaemonException.StartException(
                    String.format(
                            "Error starting daemon: id = %s, workingDir = %s, daemonArgs: %s",
                            daemonId, workingDir, command),
                    e);
        }
    }

    private float getJavaVersion() {
        try {
            final String java = Os.current().isUnixLike() ? "bin/java" : "bin\\java.exe";
            Path javaExec = parameters.javaHome().resolve(java);
            List<String> args = new ArrayList<>();
            args.add(javaExec.toString());
            args.add("-version");
            Process process = new ProcessBuilder()
                    .directory(parameters.mvndHome().toFile())
                    .command(args)
                    .start();
            process.waitFor();
            Scanner sc = new Scanner(process.getErrorStream());
            sc.next();
            sc.next();
            String version = sc.next();
            LOGGER.warn("JAVA VERSION: " + version);
            int is = version.charAt(0) == '"' ? 1 : 0;
            int ie = version.indexOf('.', version.indexOf('.', is));
            return Float.parseFloat(version.substring(is, ie));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to detect java version", e);
        }
    }

    private DaemonClientConnection connectToDaemonWithId(String daemon, boolean newDaemon)
            throws DaemonException.ConnectException {
        // Look for 'our' daemon among the busy daemons - a daemon will start in busy state so that nobody else will
        // grab it.
        DaemonInfo daemonInfo = registry.get(daemon);
        if (daemonInfo != null) {
            try {
                return connectToDaemon(daemonInfo, new CleanupOnStaleAddress(daemonInfo), newDaemon);
            } catch (DaemonException.ConnectException e) {
                DaemonDiagnostics diag = new DaemonDiagnostics(daemon, parameters);
                throw new DaemonException.ConnectException(
                        "Could not connect to the Maven daemon.\n" + diag.describe(), e);
            }
        }
        return null;
    }

    private DaemonClientConnection connectToDaemon(
            DaemonInfo daemon, DaemonClientConnection.StaleAddressDetector staleAddressDetector, boolean newDaemon)
            throws DaemonException.ConnectException {
        LOGGER.debug("Connecting to Daemon");
        try {
            DaemonConnection connection = connect(daemon.getAddress(), daemon.getToken());
            return new DaemonClientConnection(connection, daemon, staleAddressDetector, newDaemon, parameters);
        } catch (DaemonException.ConnectException e) {
            staleAddressDetector.maybeStaleAddress(e);
            throw e;
        } finally {
            LOGGER.debug("Connected");
        }
    }

    private class CleanupOnStaleAddress implements DaemonClientConnection.StaleAddressDetector {
        private final DaemonInfo daemon;

        public CleanupOnStaleAddress(DaemonInfo daemon) {
            this.daemon = daemon;
        }

        @Override
        public boolean maybeStaleAddress(Exception failure) {
            LOGGER.debug(
                    "Removing daemon from the registry due to communication failure. Daemon information: {}", daemon);
            final long timestamp = System.currentTimeMillis();
            final DaemonStopEvent stopEvent =
                    new DaemonStopEvent(daemon.getId(), timestamp, null, "by user or operating system");
            registry.storeStopEvent(stopEvent);
            registry.remove(daemon.getId());
            return true;
        }
    }

    public DaemonConnection connect(String str, byte[] token) throws DaemonException.ConnectException {
        SocketAddress address = SocketFamily.fromString(str);
        try {
            LOGGER.debug("Trying to connect to address {}.", address);
            SocketFamily family = SocketFamily.familyOf(address);
            SocketChannel socketChannel = family.openSocket();
            socketChannel.configureBlocking(false);
            boolean connected = socketChannel.connect(address);
            if (!connected) {
                long t0 = System.nanoTime();
                long t1 = t0 + TimeUnit.MICROSECONDS.toNanos(CONNECT_TIMEOUT);
                while (!connected && t0 < t1) {
                    Thread.sleep(10);
                    connected = socketChannel.finishConnect();
                    if (!connected) {
                        t0 = System.nanoTime();
                    }
                }
                if (!connected) {
                    throw new IOException("Timeout");
                }
            }
            socketChannel.configureBlocking(true);

            //            Socket socket = socketChannel.socket();
            //            socket.connect(address, CONNECT_TIMEOUT);
            //            if (socket.getLocalSocketAddress().equals(socket.getRemoteSocketAddress())) {
            //                socketChannel.close();
            //                throw new DaemonException.ConnectException(String.format("Socket connected to itself on
            // %s.", address));
            //            }
            LOGGER.debug("Connected to address {}.", socketChannel.getRemoteAddress());

            ByteBuffer tokenBuffer = ByteBuffer.wrap(token);
            do {
                socketChannel.write(tokenBuffer);
            } while (tokenBuffer.remaining() > 0);
            LOGGER.debug("Exchanged token successfully");

            return new DaemonConnection(socketChannel);
        } catch (DaemonException.ConnectException e) {
            throw e;
        } catch (Exception e) {
            throw new DaemonException.ConnectException(String.format("Could not connect to server %s.", address), e);
        }
    }
}
