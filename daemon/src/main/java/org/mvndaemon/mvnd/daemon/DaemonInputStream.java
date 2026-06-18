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
package org.mvndaemon.mvnd.daemon;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.maven.logging.ProjectBuildLogAppender;

/**
 * An InputStream implementation that manages input for Maven daemon processes.
 *
 * This class implements a buffered input stream that:
 * 1. Tracks which project is currently reading input using ProjectBuildLogAppender
 * 2. Requests input from the client when needed through a callback
 * 3. Buffers received input data in memory
 *
 * Key behaviors:
 * - Input is requested through startReadingFromProject callback whenever:
 *   a) The reading project changes
 *   b) The input buffer is empty and more data is needed
 * - The callback receives both the project ID and the number of bytes requested
 * - Data is added to the buffer through addInputData, which can be called from another thread
 * - EOF is signaled by calling addInputData with null
 *
 * The stream coordinates between multiple threads:
 * - Reader thread(s): Calling methods to get input
 * - Writer thread: Calling addInputData to provide input data, addInputAvailableData to provide the number of bytes available
 *
 * Synchronization:
 * - A lock controls access to datas / available as well as internal state
 * - Readers wait when no data is available using datasReadyCondition.await() / availableReadyCondition.await()
 * - Writers notify readers when new data arrives datasReadyCondition.signalAll() / availableReadyCondition.signalAll()
 *
 * This implementation is particularly important for:
 * 1. Handling piped input (e.g., cat file | mvnd ...)
 * 2. Supporting interactive input during builds
 * 3. Managing input across multiple project builds
 */
class DaemonInputStream extends InputStream {
    private final BiConsumer<String, Integer> startReadingFromProject;
    private final Consumer<String> requestAvailable;

    private final Lock lock = new ReentrantLock();
    private final Condition datasReadyCondition = lock.newCondition();
    private final Condition availableReadyCondition = lock.newCondition();

    private final LinkedList<byte[]> datas = new LinkedList<>();
    private Optional<Integer> available = Optional.empty();

    private final Charset charset;
    private int pos = -1;
    private String projectReading = null;
    private volatile boolean eof = false;

    DaemonInputStream(BiConsumer<String, Integer> startReadingFromProject, Consumer<String> requestAvailable) {
        this.startReadingFromProject = startReadingFromProject;
        this.requestAvailable = requestAvailable;
        this.charset = Charset.forName(System.getProperty("file.encoding"));
    }

    @Override
    public int available() throws IOException {
        lock.lock();
        try {
            int buffered = datas.stream().mapToInt(a -> a.length).sum() - Math.max(pos, 0);

            if (eof) {
                return buffered;
            }

            String projectId = ProjectBuildLogAppender.getProjectId();

            if (!Objects.equals(projectId, projectReading)) {
                projectReading = projectId;
            }

            requestAvailable.accept(projectId);

            available = Optional.empty();

            try {
                while (available.isEmpty()) {
                    availableReadyCondition.await();
                }
            } catch (InterruptedException e) {
                throw new InterruptedIOException("Interrupted");
            }

            return buffered + available.orElse(0);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int read = read(b, 0, 1);
        if (read == 1) {
            return b[0];
        }
        return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        lock.lock();
        try {
            if (eof && datas.isEmpty()) {
                return -1; // Return EOF if we've reached the end and no more data
            }
            String projectId = ProjectBuildLogAppender.getProjectId();
            if (!Objects.equals(projectId, projectReading)) {
                projectReading = projectId;
            }
            int read = 0;
            while (read < len) {
                if (datas.isEmpty()) {
                    if (eof) {
                        return read > 0 ? read : -1; // Exit properly on EOF
                    }
                    if (read > 0) {
                        break;
                    }
                    // Always notify we need input when waiting for data
                    startReadingFromProject.accept(projectReading, len - read);
                    try {
                        datasReadyCondition.await();
                    } catch (InterruptedException e) {
                        throw new InterruptedIOException("Interrupted");
                    }
                    pos = -1;
                    continue;
                }
                byte[] curData = datas.getFirst();
                if (pos >= curData.length) {
                    datas.removeFirst();
                    pos = -1;
                    continue;
                }
                if (pos < 0) {
                    pos = 0;
                }
                b[off + read++] = curData[pos++];
            }
            return read;
        } finally {
            lock.unlock();
        }
    }

    public void addInputData(String data) {
        lock.lock();
        try {
            if (data == null) {
                eof = true;
            } else {
                datas.add(data.getBytes(charset));
            }
            datasReadyCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void addInputAvailableData(int bytesAvailable) {
        lock.lock();
        try {
            available = Optional.of(bytesAvailable);
            availableReadyCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
