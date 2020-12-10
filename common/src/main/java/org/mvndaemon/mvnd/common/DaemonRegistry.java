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

package org.mvndaemon.mvnd.common;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 *
 * File origin:
 * https://github.com/gradle/gradle/blob/v5.6.2/subprojects/launcher/src/main/java/org/gradle/launcher/daemon/registry/DaemonRegistry.java
 * https://github.com/OpenHFT/Java-Lang/blob/master/lang/src/main/java/net/openhft/lang/io/AbstractBytes.java
 */
public class DaemonRegistry implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DaemonRegistry.class);
    private static final int MAX_LENGTH = 32768;

    private static final long LOCK_TIMEOUT_MS = 1000 * 20;
    private final Path registryFile;
    private static final Map<Path, Object> locks = new ConcurrentHashMap<>();
    private final Object lck;
    private final FileChannel channel;
    private final MappedByteBuffer buffer;

    private final Map<String, DaemonInfo> infosMap = new HashMap<>();
    private final List<DaemonStopEvent> stopEvents = new ArrayList<>();

    public DaemonRegistry(Path registryFile) {
        final Path absPath = registryFile.toAbsolutePath().normalize();
        this.lck = locks.computeIfAbsent(absPath, p -> new Object());
        this.registryFile = absPath;
        try {
            if (!Files.isRegularFile(absPath)) {
                if (!Files.isDirectory(absPath.getParent())) {
                    Files.createDirectories(absPath.getParent());
                }
            }
            channel = FileChannel.open(absPath,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, MAX_LENGTH);
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

    public DaemonInfo get(String uid) {
        read();
        return infosMap.get(uid);
    }

    public List<DaemonInfo> getAll() {
        read();
        return new ArrayList<>(infosMap.values());
    }

    public List<DaemonInfo> getIdle() {
        read();
        return infosMap.values().stream()
                .filter(di -> di.getState() == Idle)
                .collect(Collectors.toList());
    }

    public List<DaemonInfo> getNotIdle() {
        return infosMap.values().stream()
                .filter(di -> di.getState() != Idle)
                .collect(Collectors.toList());
    }

    public List<DaemonInfo> getCanceled() {
        read();
        return infosMap.values().stream()
                .filter(di -> di.getState() == Canceled)
                .collect(Collectors.toList());
    }

    public void remove(final String uid) {
        update(() -> infosMap.remove(uid));
    }

    public void markState(final String uid, final DaemonState state) {
        LOGGER.debug("Marking busy by uid: {}", uid);
        update(() -> infosMap.computeIfPresent(uid, (id, di) -> di.withState(state)));
    }

    public void storeStopEvent(final DaemonStopEvent stopEvent) {
        LOGGER.debug("Storing daemon stop event with timestamp {}", stopEvent.getTimestamp());
        update(() -> stopEvents.add(stopEvent));
    }

    public List<DaemonStopEvent> getStopEvents() {
        read();
        return new ArrayList<>(stopEvents);
    }

    public void removeStopEvents(final Collection<DaemonStopEvent> events) {
        LOGGER.debug("Removing {} daemon stop events from registry", events.size());
        update(() -> stopEvents.removeAll(events));
    }

    public void store(final DaemonInfo info) {
        LOGGER.debug("Storing daemon {}", info);
        update(() -> infosMap.put(info.getUid(), info));
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
            final long deadline = System.currentTimeMillis() + LOCK_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                try (FileLock l = channel.tryLock(0, MAX_LENGTH, false)) {
                    BufferCaster.cast(buffer).position(0);
                    infosMap.clear();
                    int nb = buffer.getInt();
                    for (int i = 0; i < nb; i++) {
                        String uid = readString();
                        String javaHome = readString();
                        String mavenHome = readString();
                        int pid = buffer.getInt();
                        int address = buffer.getInt();
                        String locale = readString();
                        List<String> opts = new ArrayList<>();
                        int nbOpts = buffer.getInt();
                        for (int j = 0; j < nbOpts; j++) {
                            opts.add(readString());
                        }
                        DaemonState state = DaemonState.values()[buffer.get()];
                        long lastIdle = buffer.getLong();
                        long lastBusy = buffer.getLong();
                        DaemonInfo di = new DaemonInfo(uid, javaHome, mavenHome, pid, address, locale, opts, state,
                                lastIdle, lastBusy);
                        infosMap.putIfAbsent(di.getUid(), di);
                    }
                    stopEvents.clear();
                    nb = buffer.getInt();
                    for (int i = 0; i < nb; i++) {
                        String uid = readString();
                        long date = buffer.getLong();
                        int ord = buffer.get();
                        DaemonExpirationStatus des = ord >= 0 ? DaemonExpirationStatus.values()[ord] : null;
                        String reason = readString();
                        DaemonStopEvent se = new DaemonStopEvent(uid, date, des, reason);
                        stopEvents.add(se);
                    }

                    if (updater != null) {
                        updater.run();
                        BufferCaster.cast(buffer).position((int) 0);
                        buffer.putInt(infosMap.size());
                        for (DaemonInfo di : infosMap.values()) {
                            writeString(di.getUid());
                            writeString(di.getJavaHome());
                            writeString(di.getMvndHome());
                            buffer.putInt(di.getPid());
                            buffer.putInt(di.getAddress());
                            writeString(di.getLocale());
                            buffer.putInt(di.getOptions().size());
                            for (String opt : di.getOptions()) {
                                writeString(opt);
                            }
                            buffer.put((byte) di.getState().ordinal());
                            buffer.putLong(di.getLastIdle());
                            buffer.putLong(di.getLastBusy());
                        }
                        buffer.putInt(stopEvents.size());
                        for (DaemonStopEvent dse : stopEvents) {
                            writeString(dse.getUid());
                            buffer.putLong(dse.getTimestamp());
                            buffer.put((byte) (dse.getStatus() == null ? -1 : dse.getStatus().ordinal()));
                            writeString(dse.getReason());
                        }
                    }
                    return;
                } catch (IOException e) {
                    throw new RuntimeException("Could not lock offset 0 of " + registryFile);
                }
            }
            throw new RuntimeException("Could not lock " + registryFile + " within " + LOCK_TIMEOUT_MS + " ms");
        }
    }

    private static final int PROCESS_ID = getProcessId0();

    private static int getProcessId0() {
        String pid = null;
        final File self = new File("/proc/self");
        try {
            if (self.exists()) {
                pid = self.getCanonicalFile().getName();
            }
        } catch (IOException ignored) {
        }
        if (pid == null) {
            pid = ManagementFactory.getRuntimeMXBean().getName().split("@", 0)[0];
        }
        if (pid == null) {
            int rpid = new Random().nextInt(1 << 16);
            LOGGER.warn("Unable to determine PID, picked a random number=" + rpid);
            return rpid;
        } else {
            return Integer.parseInt(pid);
        }
    }

    private String readString() {
        int sz = buffer.getShort();
        if (sz == -1) {
            return null;
        }
        if (sz < -1 || sz > 1024) {
            throw new IllegalStateException("Bad string size: " + sz);
        }
        byte[] buf = new byte[sz];
        buffer.get(buf);
        return new String(buf, StandardCharsets.UTF_8);
    }

    private void writeString(String str) {
        if (str == null) {
            buffer.putShort((short) -1);
        } else if (str.length() > 1024) {
            throw new IllegalStateException("String too long: " + str);
        } else {
            byte[] buf = str.getBytes(StandardCharsets.UTF_8);
            buffer.putShort((short) buf.length);
            buffer.put(buf);
        }
    }

    public String toString() {
        return String.format("PersistentDaemonRegistry[file=%s]", registryFile);
    }
}
