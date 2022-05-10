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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
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
    private MappedByteBuffer buffer;
    private long size;

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
            size = nextPowerOf2(channel.size(), MAX_LENGTH);
            buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, size);
        } catch (IOException e) {
            throw new DaemonException(e);
        }
    }

    private long nextPowerOf2(long a, long min) {
        long b = min;
        while (b < a) {
            b = b << 1;
        }
        return b;
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
            final long deadline = System.currentTimeMillis() + LOCK_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                try (FileLock l = channel.tryLock(0, size, false)) {
                    BufferCaster.cast(buffer).position(0);
                    infosMap.clear();
                    int nb = buffer.getInt();
                    for (int i = 0; i < nb; i++) {
                        String daemonId = readString();
                        String javaHome = readString();
                        String mavenHome = readString();
                        int pid = buffer.getInt();
                        String address = readString();

                        byte[] token = new byte[DaemonInfo.TOKEN_SIZE];
                        buffer.get(token);

                        String locale = readString();
                        List<String> opts = new ArrayList<>();
                        int nbOpts = buffer.getInt();
                        for (int j = 0; j < nbOpts; j++) {
                            opts.add(readString());
                        }
                        DaemonState state = DaemonState.values()[buffer.get()];
                        long lastIdle = buffer.getLong();
                        long lastBusy = buffer.getLong();
                        DaemonInfo di = new DaemonInfo(daemonId, javaHome, mavenHome, pid, address, token, locale,
                                opts, state, lastIdle, lastBusy);
                        infosMap.putIfAbsent(di.getId(), di);
                    }
                    stopEvents.clear();
                    nb = buffer.getInt();
                    for (int i = 0; i < nb; i++) {
                        String daemonId = readString();
                        long date = buffer.getLong();
                        int ord = buffer.get();
                        DaemonExpirationStatus des = ord >= 0 ? DaemonExpirationStatus.values()[ord] : null;
                        String reason = readString();
                        DaemonStopEvent se = new DaemonStopEvent(daemonId, date, des, reason);
                        stopEvents.add(se);
                    }

                    if (updater != null) {
                        updater.run();
                        BufferCaster.cast(buffer).position((int) 0);
                        buffer.putInt(infosMap.size());
                        for (DaemonInfo di : infosMap.values()) {
                            writeString(di.getId());
                            writeString(di.getJavaHome());
                            writeString(di.getMvndHome());
                            buffer.putInt(di.getPid());
                            writeString(di.getAddress());
                            buffer.put(di.getToken());
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
                            writeString(dse.getDaemonId());
                            buffer.putLong(dse.getTimestamp());
                            buffer.put((byte) (dse.getStatus() == null ? -1 : dse.getStatus().ordinal()));
                            writeString(dse.getReason());
                        }
                    }
                    if (buffer.remaining() >= buffer.position() * 2) {
                        long ns = nextPowerOf2(buffer.position(), MAX_LENGTH);
                        if (ns != size) {
                            size = ns;
                            LOGGER.info("Resizing registry to {} kb due to buffer underflow", (size / 1024));
                            channel.truncate(size);
                            try {
                                buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, size);
                            } catch (IOException ex) {
                                throw new DaemonException("Could not resize registry " + registryFile, ex);
                            }
                        }
                    }
                    return;
                } catch (BufferOverflowException e) {
                    size <<= 1;
                    LOGGER.info("Resizing registry to {} kb due to buffer overflow", (size / 1024));
                    try {
                        buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, size);
                    } catch (IOException ex) {
                        throw new DaemonException("Could not resize registry " + registryFile, ex);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Could not lock offset 0 of " + registryFile);
                } catch (IllegalStateException | ArrayIndexOutOfBoundsException | BufferUnderflowException e) {
                    String absPath = registryFile.toAbsolutePath().normalize().toString();
                    LOGGER.warn("Invalid daemon registry info, " +
                            "trying to recover from this issue. " +
                            "If you keep getting this warning, " +
                            "try deleting the `registry.bin` file at [" + absPath + "]", e);
                    this.reset();
                    return;
                }
            }
            throw new RuntimeException("Could not lock " + registryFile + " within " + LOCK_TIMEOUT_MS + " ms");
        }
    }

    private void reset() {
        infosMap.clear();
        stopEvents.clear();
        BufferCaster.cast(buffer).clear();
        buffer.putInt(0); // reset daemon count
        buffer.putInt(0); // reset stop event count
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
                            LOGGER.warn("Unable to determine PID from malformed /proc/self link `" + pid + "`");
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
            LOGGER.warn("Unable to determine PID from malformed VM name `" + vmname + "`, picked a random number=" + rpid);
            return rpid;
        }
    }

    protected String readString() {
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

    protected void writeString(String str) {
        if (str == null) {
            buffer.putShort((short) -1);
            return;
        }
        byte[] buf = str.getBytes(StandardCharsets.UTF_8);
        if (buf.length > 1024) {
            LOGGER.warn("Attempting to write string longer than 1024 bytes: '{}'. Please raise an issue.", str);
            str = str.substring(0, 1033);
            while (buf.length > 1024) {
                str = str.substring(0, str.length() - 12) + "…";
                buf = str.getBytes(StandardCharsets.UTF_8);
            }
        }
        buffer.putShort((short) buf.length);
        buffer.put(buf);
    }

    protected ByteBuffer buffer() {
        return buffer;
    }

    public String toString() {
        return String.format("DaemonRegistry[file=%s]", registryFile);
    }
}
