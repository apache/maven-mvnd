/*
 * Copyright 2011 the original author or authors.
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
package org.jboss.fuse.mvnd.client;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jboss.fuse.mvnd.common.BuildProperties;
import org.jboss.fuse.mvnd.common.DaemonCompatibilitySpec;
import org.jboss.fuse.mvnd.common.DaemonCompatibilitySpec.Result;
import org.jboss.fuse.mvnd.common.DaemonConnection;
import org.jboss.fuse.mvnd.common.DaemonDiagnostics;
import org.jboss.fuse.mvnd.common.DaemonException;
import org.jboss.fuse.mvnd.common.DaemonInfo;
import org.jboss.fuse.mvnd.common.DaemonRegistry;
import org.jboss.fuse.mvnd.common.DaemonState;
import org.jboss.fuse.mvnd.common.DaemonStopEvent;
import org.jboss.fuse.mvnd.common.Environment;
import org.jboss.fuse.mvnd.common.MavenDaemon;
import org.jboss.fuse.mvnd.common.Os;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Thread.sleep;
import static org.jboss.fuse.mvnd.common.DaemonState.Canceled;

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
    private final ClientLayout layout;
    private final BuildProperties buildProperties;

    public DaemonConnector(ClientLayout layout, DaemonRegistry registry, BuildProperties buildProperties) {
        this.layout = layout;
        this.registry = registry;
        this.buildProperties = buildProperties;
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

    public DaemonClientConnection connect(DaemonCompatibilitySpec constraint, Consumer<String> logger) {
        Map<Boolean, List<DaemonInfo>> idleBusy = registry.getAll().stream()
                .collect(Collectors.groupingBy(di -> di.getState() == DaemonState.Idle));

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
        String message = handleStopEvents(idleDaemons, busyDaemons);
        logger.accept(message);
        return startDaemon(constraint);
    }

    private String handleStopEvents(Collection<DaemonInfo> idleDaemons, Collection<DaemonInfo> busyDaemons) {
        final List<DaemonStopEvent> stopEvents = registry.getStopEvents();

        // Clean up old stop events
        long time = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);

        List<DaemonStopEvent> oldStopEvents = stopEvents.stream()
                .filter(e -> e.getTimestamp() < time)
                .collect(Collectors.toList());
        registry.removeStopEvents(oldStopEvents);

        final List<DaemonStopEvent> recentStopEvents = stopEvents.stream()
                .filter(e -> e.getTimestamp() >= time)
                .collect(Collectors.groupingBy(DaemonStopEvent::getUid,
                        Collectors.minBy(this::compare)))
                .values()
                .stream()
                .map(Optional::get)
                .collect(Collectors.toList());
        for (DaemonStopEvent stopEvent : recentStopEvents) {
            LOGGER.debug("Previous Daemon ({}) stopped at {} {}",
                    stopEvent.getUid(), stopEvent.getTimestamp(), stopEvent.getReason());
        }

        return generate(busyDaemons.size(), idleDaemons.size(), recentStopEvents.size());
    }

    public static String generate(final int numBusy, final int numIncompatible, final int numStopped) {
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
            return "Starting a Maven Daemon, "
                    + String.join(" and ", reasons) + " Daemon" + (totalUnavailableDaemons > 1 ? "s" : "")
                    + " could not be reused, use --status for details";
        } else {
            return "Starting a Maven Daemon (subsequent builds will be faster)";
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

    private DaemonClientConnection connectToIdleDaemon(Collection<DaemonInfo> idleDaemons, DaemonCompatibilitySpec constraint) {
        final List<DaemonInfo> compatibleIdleDaemons = getCompatibleDaemons(idleDaemons, constraint);
        return findConnection(compatibleIdleDaemons);
    }

    private DaemonClientConnection connectToCanceledDaemon(Collection<DaemonInfo> busyDaemons,
            DaemonCompatibilitySpec constraint) {
        DaemonClientConnection connection = null;
        Map<Boolean, List<DaemonInfo>> canceledBusy = busyDaemons.stream()
                .collect(Collectors.groupingBy(di -> di.getState() == Canceled));
        final Collection<DaemonInfo> compatibleCanceledDaemons = getCompatibleDaemons(
                canceledBusy.getOrDefault(true, Collections.emptyList()), constraint);
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
                LOGGER.debug("{} daemon {} does not match the desired criteria: "
                        + result.getWhy(), daemon.getState(), daemon.getUid());
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

    public DaemonClientConnection startDaemon(DaemonCompatibilitySpec constraint) {
        final String daemon = startDaemon();
        LOGGER.debug("Started Maven daemon {}", daemon);
        long start = System.currentTimeMillis();
        do {
            DaemonClientConnection daemonConnection = connectToDaemonWithId(daemon, true);
            if (daemonConnection != null) {
                return daemonConnection;
            }
            try {
                sleep(200L);
            } catch (InterruptedException e) {
                throw new DaemonException.InterruptedException(e);
            }
        } while (System.currentTimeMillis() - start < DEFAULT_CONNECT_TIMEOUT);
        DaemonDiagnostics diag = new DaemonDiagnostics(daemon, layout.daemonLog(daemon));
        throw new DaemonException.ConnectException("Timeout waiting to connect to the Maven daemon.\n" + diag.describe());
    }

    private String startDaemon() {

        final String uid = UUID.randomUUID().toString();
        final Path mavenHome = layout.mavenHome();
        final Path workingDir = layout.userDir();
        String command = "";
        try {
            final String classpath = mavenHome.resolve("mvn/lib/ext/mvnd-common-" + buildProperties.getVersion() + ".jar")
                    .toString();
            final String java = Os.current().isUnixLike() ? "bin/java" : "bin\\java.exe";
            List<String> args = new ArrayList<>();
            args.add(layout.javaHome().resolve(java).toString());
            args.add("-classpath");
            args.add(classpath);
            if (Environment.DAEMON_DEBUG.systemProperty().orDefault(() -> "false").asBoolean()) {
                args.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000");
            }
            args.add("-Dmvnd.home=" + mavenHome);
            args.add("-Dmvnd.java.home=" + layout.javaHome().toString());
            args.add("-Dlogback.configurationFile=" + layout.getLogbackConfigurationPath());
            args.add("-Ddaemon.uid=" + uid);
            if (Boolean.getBoolean(Environment.DEBUG_ENVIRONMENT_PROP)) {
                args.add("-D" + Environment.DEBUG_ENVIRONMENT_PROP + "=true");
            }
            args.add("-Xmx4g");
            args.add(Environment.DAEMON_IDLE_TIMEOUT_MS.asCommandLineProperty(Integer.toString(layout.getIdleTimeoutMs())));
            args.add(Environment.DAEMON_KEEP_ALIVE_MS.asCommandLineProperty(Integer.toString(layout.getKeepAliveMs())));
            args.add(MavenDaemon.class.getName());
            command = String.join(" ", args);

            LOGGER.debug("Starting daemon process: uid = {}, workingDir = {}, daemonArgs: {}", uid, workingDir, command);
            ProcessBuilder.Redirect redirect = ProcessBuilder.Redirect.appendTo(layout.daemonLog(uid + ".out").toFile());
            new ProcessBuilder()
                    .directory(workingDir.toFile())
                    .command(args)
                    .redirectOutput(redirect)
                    .redirectError(redirect)
                    .start();
            return uid;
        } catch (Exception e) {
            throw new DaemonException.StartException(
                    String.format("Error starting daemon: uid = %s, workingDir = %s, daemonArgs: %s",
                            uid, workingDir, command),
                    e);
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
                DaemonDiagnostics diag = new DaemonDiagnostics(daemon, layout.daemonLog(daemon));
                throw new DaemonException.ConnectException("Could not connect to the Maven daemon.\n" + diag.describe(), e);
            }
        }
        return null;
    }

    private DaemonClientConnection connectToDaemon(DaemonInfo daemon,
            DaemonClientConnection.StaleAddressDetector staleAddressDetector, boolean newDaemon)
            throws DaemonException.ConnectException {
        LOGGER.debug("Connecting to Daemon");
        try {
            int maxKeepAliveMs = layout.getKeepAliveMs() * layout.getMaxLostKeepAlive();
            DaemonConnection connection = connect(daemon.getAddress());
            return new DaemonClientConnection(connection, daemon, staleAddressDetector, newDaemon, maxKeepAliveMs);
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
            LOGGER.debug("Removing daemon from the registry due to communication failure. Daemon information: {}", daemon);
            final long timestamp = System.currentTimeMillis();
            final DaemonStopEvent stopEvent = new DaemonStopEvent(daemon.getUid(), timestamp, null,
                    "by user or operating system");
            registry.storeStopEvent(stopEvent);
            registry.remove(daemon.getUid());
            return true;
        }
    }

    public DaemonConnection connect(int port) throws DaemonException.ConnectException {
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        try {
            LOGGER.debug("Trying to connect to address {}.", address);
            SocketChannel socketChannel = SocketChannel.open();
            Socket socket = socketChannel.socket();
            socket.connect(address, CONNECT_TIMEOUT);
            if (socket.getLocalSocketAddress().equals(socket.getRemoteSocketAddress())) {
                socketChannel.close();
                throw new DaemonException.ConnectException(String.format("Socket connected to itself on %s.", address));
            }
            LOGGER.debug("Connected to address {}.", socket.getRemoteSocketAddress());
            return new DaemonConnection(socketChannel);
        } catch (DaemonException.ConnectException e) {
            throw e;
        } catch (Exception e) {
            throw new DaemonException.ConnectException(String.format("Could not connect to server %s.", address), e);
        }
    }

}
