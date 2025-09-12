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
package org.mvndaemon.mvnd.common;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mvndaemon.mvnd.common.DaemonState.Canceled;
import static org.mvndaemon.mvnd.common.DaemonState.Idle;

/**
 * Access to daemon registry files. Useful also for testing.
 * <p>
 * File origin:
 * https://github.com/gradle/gradle/blob/v5.6.2/subprojects/launcher/src/main/java/org/gradle/launcher/daemon/registry/DaemonRegistry.java
 * https://github.com/OpenHFT/Java-Lang/blob/master/lang/src/main/java/net/openhft/lang/io/AbstractBytes.java
 */
public class DaemonRegistry implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DaemonRegistry.class);

    private static final long LOCK_TIMEOUT_MS = 1000 * 20;
    private static final Map<Path, Object> locks = new ConcurrentHashMap<>();
    private final Path registryFile;
    private final Object lck;
    private final FileChannel channel;

    private final Map<String, DaemonInfo> infosMap = new HashMap<>();
    private final List<DaemonStopEvent> stopEvents = new ArrayList<>();

    public DaemonRegistry(Path registryFile) {
        final Path absPath = registryFile.toAbsolutePath().normalize();
        this.lck = locks.computeIfAbsent(absPath, p -> new Object());
        this.registryFile = absPath;
        try {
            Files.createDirectories(absPath.getParent());
            channel = FileChannel.open(
                    absPath, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new DaemonException(e);
        }
    }

    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            throw new DaemonException("Error closing registry", e);
        }
    }

    public Path getRegistryFile() {
        return registryFile;
    }

    public DaemonInfo get(String daemonId) {
        read();
        return infosMap.get(daemonId);
    }

    public List<DaemonInfo> getAll() {
        read();
        return new ArrayList<>(infosMap.values());
    }

    public List<DaemonInfo> getIdle() {
        read();
        return infosMap.values().stream().filter(di -> di.getState() == Idle).collect(Collectors.toList());
    }

    public List<DaemonInfo> getNotIdle() {
        return infosMap.values().stream().filter(di -> di.getState() != Idle).collect(Collectors.toList());
    }

    public List<DaemonInfo> getCanceled() {
        read();
        return infosMap.values().stream()
                .filter(di -> di.getState() == Canceled)
                .collect(Collectors.toList());
    }

    public void remove(final String daemonId) {
        update(() -> infosMap.remove(daemonId));
    }

    public void markState(final String daemonId, final DaemonState state) {
        LOGGER.debug("Marking busy by id: {}", daemonId);
        update(() -> infosMap.computeIfPresent(daemonId, (id, di) -> di.withState(state)));
    }

    public void storeStopEvent(final DaemonStopEvent stopEvent) {
        LOGGER.debug("Storing daemon stop event with timestamp {}", stopEvent.getTimestamp());
        update(() -> stopEvents.add(stopEvent));
    }

    public List<DaemonStopEvent> getStopEvents() {
        read();
        return doGetDaemonStopEvents();
    }

    protected List<DaemonStopEvent> doGetDaemonStopEvents() {
        return new ArrayList<>(stopEvents);
    }

    public void removeStopEvents(final Collection<DaemonStopEvent> events) {
        LOGGER.debug("Removing {} daemon stop events from registry", events.size());
        update(() -> stopEvents.removeAll(events));
    }

    public void store(final DaemonInfo info) {
        LOGGER.debug("Storing daemon {}", info);
        update(() -> infosMap.put(info.getId(), info));
    }

    public static int getProcessId() {
        return PROCESS_ID;
    }

    private void read() {
        doUpdate(null);
    }

    private void update(Runnable updater) {
        doUpdate(updater);
    }

    private void doUpdate(Runnable updater) {
        if (!Files.isReadable(registryFile)) {
            throw new DaemonException("Registry became unaccessible");
        }

        synchronized (lck) {
            try (FileLock l = tryLock()) {
                channel.position(0);
                DataInputStream is = new DataInputStream(new BufferedInputStream(Channels.newInputStream(channel)));
                infosMap.clear();
                int nb = is.available() < 4 ? 0 : is.readInt();
                for (int i = 0; i < nb; i++) {
                    String daemonId = is.readUTF();
                    String javaHome = is.readUTF();
                    String mavenHome = is.readUTF();
                    int pid = is.readInt();
                    String address = is.readUTF();

                    byte[] token = new byte[DaemonInfo.TOKEN_SIZE];
                    is.read(token);

                    String locale = is.readUTF();
                    List<String> opts = new ArrayList<>();
                    int nbOpts = is.readInt();
                    for (int j = 0; j < nbOpts; j++) {
                        opts.add(is.readUTF());
                    }
                    DaemonState state = DaemonState.values()[is.readByte()];
                    long lastIdle = is.readLong();
                    long lastBusy = is.readLong();
                    DaemonInfo di = new DaemonInfo(
                            daemonId, javaHome, mavenHome, pid, address, token, locale, opts, state, lastIdle,
                            lastBusy);
                    infosMap.putIfAbsent(di.getId(), di);
                }
                stopEvents.clear();
                nb = is.available() < 4 ? 0 : is.readInt();
                for (int i = 0; i < nb; i++) {
                    String daemonId = is.readUTF();
                    long date = is.readLong();
                    int ord = is.readByte();
                    DaemonExpirationStatus des = ord >= 0 ? DaemonExpirationStatus.values()[ord] : null;
                    String reason = is.readUTF();
                    DaemonStopEvent se = new DaemonStopEvent(daemonId, date, des, reason);
                    stopEvents.add(se);
                }

                if (updater != null) {
                    updater.run();
                    channel.truncate(0);
                    DataOutputStream os =
                            new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)));
                    os.writeInt(infosMap.size());
                    for (DaemonInfo di : infosMap.values()) {
                        String id = di.getId();
                        os.writeUTF(id);
                        os.writeUTF(di.getJavaHome());
                        os.writeUTF(di.getMvndHome());
                        os.writeInt(di.getPid());
                        os.writeUTF(di.getAddress());
                        os.write(di.getToken());
                        os.writeUTF(di.getLocale());
                        os.writeInt(di.getOptions().size());
                        for (String opt : di.getOptions()) {
                            os.writeUTF(opt);
                        }
                        os.writeByte((byte) di.getState().ordinal());
                        os.writeLong(di.getLastIdle());
                        os.writeLong(di.getLastBusy());
                    }
                    os.writeInt(stopEvents.size());
                    for (DaemonStopEvent dse : stopEvents) {
                        os.writeUTF(dse.getDaemonId());
                        os.writeLong(dse.getTimestamp());
                        os.writeByte((byte)
                                (dse.getStatus() == null ? -1 : dse.getStatus().ordinal()));
                        os.writeUTF(dse.getReason());
                    }
                    os.flush();
                }
            } catch (DaemonException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.warn("Invalid daemon registry info at [{}], trying to recover.", registryFile, e);
                this.reset();
            }
        }
    }

    private FileLock tryLock() {
        try {
            final long deadline = System.currentTimeMillis() + LOCK_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                FileLock fileLock = channel.tryLock(0, Long.MAX_VALUE, false);
                if (fileLock != null) {
                    return fileLock;
                }
                Thread.sleep(100);
            }
            throw new DaemonException("Could not lock " + registryFile + " within " + LOCK_TIMEOUT_MS + " ms");
        } catch (IOException | InterruptedException e) {
            throw new DaemonException("Could not lock " + registryFile, e);
        }
    }

    private void reset() {
        infosMap.clear();
        stopEvents.clear();
        try {
            channel.truncate(0);
        } catch (IOException e) {
            LOGGER.error("Could not truncate [{}], please delete this file and try again.", registryFile, e);
        }
    }

    private static final int PROCESS_ID = getProcessId0();

    private static int getProcessId0() {
        if (Os.current() == Os.LINUX) {
            try {
                final Path self = Paths.get("/proc/self");
                if (Files.exists(self)) {
                    String pid = self.toRealPath().getFileName().toString();
                    if (pid.equals("self")) {
                        LOGGER.debug("/proc/self symlink could not be followed");
                    } else {
                        LOGGER.debug("loading own PID from /proc/self link: {}", pid);
                        try {
                            return Integer.parseInt(pid);
                        } catch (NumberFormatException x) {
                            LOGGER.warn("Unable to determine PID from malformed /proc/self link `{}`", pid);
                        }
                    }
                }
            } catch (IOException ignored) {
                LOGGER.debug("could not load /proc/self", ignored);
            }
        }
        String vmname = ManagementFactory.getRuntimeMXBean().getName();
        String pid = vmname.split("@", 0)[0];
        LOGGER.debug("loading own PID from VM name: {}", pid);
        try {
            return Integer.parseInt(pid);
        } catch (NumberFormatException x) {
            int rpid = new Random().nextInt(1 << 16);
            LOGGER.warn("Unable to determine PID from malformed VM name `{}`, picked a random number={}", vmname, rpid);
            return rpid;
        }
    }

    public String toString() {
        return String.format("DaemonRegistry[file=%s]", registryFile);
    }
}
