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
import java.lang.reflect.Field;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;
import sun.nio.ch.DirectBuffer;

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

    private final Path registryFile;

    private final Lock lock = new ReentrantLock();
    private final FileChannel channel;
    private final MappedByteBuffer buffer;

    private long seq;
    private final Map<String, DaemonInfo> infosMap = new HashMap<>();
    private final List<DaemonStopEvent> stopEvents = new ArrayList<>();

    public DaemonRegistry(Path registryFile) {
        this.registryFile = registryFile;
        try {
            if (!Files.isRegularFile(registryFile)) {
                if (!Files.isDirectory(registryFile.getParent())) {
                    Files.createDirectories(registryFile.getParent());
                }
            }
            channel = FileChannel.open(registryFile,
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
        lock.lock();
        try {
            read();
            return infosMap.get(uid);
        } finally {
            lock.unlock();
        }
    }

    public List<DaemonInfo> getAll() {
        lock.lock();
        try {
            read();
            return new ArrayList<>(infosMap.values());
        } finally {
            lock.unlock();
        }
    }

    public List<DaemonInfo> getIdle() {
        lock.lock();
        try {
            read();
            return infosMap.values().stream()
                    .filter(di -> di.getState() == Idle)
                    .collect(Collectors.toList());
        } finally {
            lock.unlock();
        }
    }

    public List<DaemonInfo> getNotIdle() {
        lock.lock();
        try {
            read();
            return infosMap.values().stream()
                    .filter(di -> di.getState() != Idle)
                    .collect(Collectors.toList());
        } finally {
            lock.unlock();
        }
    }

    public List<DaemonInfo> getCanceled() {
        lock.lock();
        try {
            read();
            return infosMap.values().stream()
                    .filter(di -> di.getState() == Canceled)
                    .collect(Collectors.toList());
        } finally {
            lock.unlock();
        }
    }

    public void remove(final String uid) {
        lock.lock();
        LOGGER.debug("Removing daemon uid: {}", uid);
        try {
            update(() -> infosMap.remove(uid));
        } finally {
            lock.unlock();
        }
    }

    public void markState(final String uid, final DaemonState state) {
        lock.lock();
        LOGGER.debug("Marking busy by uid: {}", uid);
        try {
            update(() -> infosMap.computeIfPresent(uid, (id, di) -> di.withState(state)));
        } finally {
            lock.unlock();
        }
    }

    public void storeStopEvent(final DaemonStopEvent stopEvent) {
        lock.lock();
        LOGGER.debug("Storing daemon stop event with timestamp {}", stopEvent.getTimestamp());
        try {
            update(() -> stopEvents.add(stopEvent));
        } finally {
            lock.unlock();
        }
    }

    public List<DaemonStopEvent> getStopEvents() {
        lock.lock();
        LOGGER.debug("Getting daemon stop events");
        try {
            read();
            return new ArrayList<>(stopEvents);
        } finally {
            lock.unlock();
        }
    }

    public void removeStopEvents(final Collection<DaemonStopEvent> events) {
        lock.lock();
        LOGGER.debug("Removing {} daemon stop events from registry", events.size());
        try {
            update(() -> stopEvents.removeAll(events));
        } finally {
            lock.unlock();
        }
    }

    public void store(final DaemonInfo info) {
        lock.lock();
        LOGGER.debug("Storing daemon {}", info);
        try {
            update(() -> infosMap.put(info.getUid(), info));
        } finally {
            lock.unlock();
        }
    }

    private static final long OFFSET_LOCK = 0;
    private static final long OFFSET_SEQ = OFFSET_LOCK + Long.BYTES;
    private static final long OFFSET_DATA = OFFSET_SEQ + Long.BYTES;

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
        try {
            busyLockLong(OFFSET_LOCK);
            try {
                long newSeq = readLong(OFFSET_SEQ);
                if (newSeq != seq) {
                    seq = newSeq;
                    BufferCaster.cast(buffer).position((int) OFFSET_DATA);
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
                }
                if (updater != null) {
                    updater.run();
                    writeLong(OFFSET_SEQ, ++seq);
                    BufferCaster.cast(buffer).position((int) OFFSET_DATA);
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
            } finally {
                unlockLong(OFFSET_LOCK);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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

    private static long uniqueTid() {
        // Assume 48 bit for 16 to 24-bit process id and 16 million threads from the start.
        return ((long) getProcessId() << 24) | currentThread().getId();
    }

    public static int getProcessId() {
        return PROCESS_ID;
    }

    private static Thread currentThread() {
        return Thread.currentThread();
    }

    static final int SLEEP_THRESHOLD = 20 * 1000 * 1000;
    static final long BUSY_LOCK_LIMIT = 20L * 1000 * 1000 * 1000;

    public void busyLockLong(long offset) throws InterruptedException, IllegalStateException {
        boolean success = tryLockNanosLong(offset, BUSY_LOCK_LIMIT);
        if (!success)
            if (currentThread().isInterrupted())
                throw new InterruptedException();
            else
                throw new IllegalStateException("Failed to lock offset " + offset + " of " + registryFile + " within "
                        + BUSY_LOCK_LIMIT / 1e9 + " seconds.");
    }

    public void unlockLong(long offset) throws IllegalMonitorStateException {
        long id = uniqueTid();
        long firstValue = (1L << 48) | id;
        if (compareAndSwapLong(offset, firstValue, 0))
            return;
        // try to check the lowId and the count.
        unlockFailedLong(offset, id);
    }

    public void resetLockLong(long offset) {
        writeOrderedLong(offset, 0L);
    }

    public boolean tryLockLong(long offset) {
        long id = uniqueTid();
        return tryLockNanos8a(offset, id);
    }

    public boolean tryLockNanosLong(long offset, long nanos) {
        long id = uniqueTid();
        int limit = nanos <= 10000 ? (int) nanos / 10 : 1000;
        for (int i = 0; i < limit; i++)
            if (tryLockNanos8a(offset, id))
                return true;
        if (nanos <= 10000)
            return false;
        return tryLockNanosLong0(offset, nanos, id);
    }

    private boolean tryLockNanosLong0(long offset, long nanos, long id) {
        long nanos0 = Math.min(nanos, SLEEP_THRESHOLD);
        long start = System.nanoTime();
        long end0 = start + nanos0 - 10000;
        do {
            if (tryLockNanos8a(offset, id))
                return true;
        } while (end0 > System.nanoTime() && !currentThread().isInterrupted());

        long end = start + nanos - SLEEP_THRESHOLD;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(currentThread().getName() + ", waiting for lock");
        }

        try {
            do {
                if (tryLockNanos8a(offset, id)) {
                    long millis = (System.nanoTime() - start) / 1000000;
                    if (millis > 200) {
                        LOGGER.warn(currentThread().getName() +
                                ", to obtain a lock took " +
                                millis / 1e3 + " seconds");
                    }
                    return true;
                }
                Thread.sleep(1);
            } while (end > System.nanoTime());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private boolean tryLockNanos8a(long offset, long id) {
        long firstValue = (1L << 48) | id;
        if (compareAndSwapLong(offset, 0, firstValue))
            return true;
        long currentValue = readLong(offset);
        long lockedId = currentValue & ((1L << 48) - 1);
        if (lockedId == 0) {
            int count = (int) (currentValue >>> 48);
            if (count != 0)
                LOGGER.warn("Lock held by threadId 0 !?");
            return compareAndSwapLong(offset, currentValue, firstValue);
        }
        if (lockedId == id) {
            if (currentValue >>> 48 == 65535)
                throw new IllegalStateException("Reentered 65535 times without an unlock");
            currentValue += 1L << 48;
            writeOrderedLong(offset, currentValue);
            return true;
        }
        return false;
    }

    private void unlockFailedLong(long offset, long id) throws IllegalMonitorStateException {
        long currentValue = readLong(offset);
        long holderId = currentValue & (-1L >>> 16);
        if (holderId == id) {
            currentValue -= 1L << 48;
            writeOrderedLong(offset, currentValue);

        } else if (currentValue == 0) {
            throw new IllegalMonitorStateException("No thread holds this lock");

        } else {
            throw new IllegalMonitorStateException("Process " + ((currentValue >>> 32) & 0xFFFF)
                    + " thread " + (holderId & (-1L >>> 32))
                    + " holds this lock, " + (currentValue >>> 48)
                    + " times, unlock from " + getProcessId()
                    + " thread " + currentThread().getId());
        }
    }

    static final Unsafe UNSAFE;
    static final int BYTES_OFFSET;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
            BYTES_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public boolean compareAndSwapLong(long offset, long expected, long x) {
        if (buffer instanceof DirectBuffer)
            return UNSAFE.compareAndSwapLong(null, ((DirectBuffer) buffer).address() + offset, expected, x);
        return UNSAFE.compareAndSwapLong(buffer.array(), BYTES_OFFSET + offset, expected, x);
    }

    public long readVolatileLong(int offset) {
        readBarrier();
        return readLong(offset);
    }

    public long readLong(long offset) {
        return buffer.getLong((int) offset);
    }

    public void writeOrderedLong(long offset, long v) {
        writeLong(offset, v);
        writeBarrier();
    }

    public void writeLong(long offset, long v) {
        buffer.putLong((int) offset, v);
    }

    private AtomicBoolean barrier;

    private void readBarrier() {
        if (barrier == null)
            barrier = new AtomicBoolean();
        barrier.get();
    }

    private void writeBarrier() {
        if (barrier == null)
            barrier = new AtomicBoolean();
        barrier.lazySet(false);
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
