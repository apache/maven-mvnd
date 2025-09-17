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
import java.util.function.BiConsumer;

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
 * - Reader thread(s): Calling read() methods to get input
 * - Writer thread: Calling addInputData to provide input data
 *
 * Synchronization:
 * - All buffer access is synchronized on the datas collection
 * - Readers wait when no data is available using datas.wait()
 * - Writers notify readers when new data arrives using datas.notifyAll()
 *
 * This implementation is particularly important for:
 * 1. Handling piped input (e.g., cat file | mvnd ...)
 * 2. Supporting interactive input during builds
 * 3. Managing input across multiple project builds.
 */
class DaemonInputStream extends InputStream {
    private final BiConsumer<String, Integer> startReadingFromProject;
    private final LinkedList<byte[]> datas = new LinkedList<>();
    private final Charset charset;
    private int pos = -1;
    private String projectReading = null;
    private volatile boolean eof = false;

    DaemonInputStream(BiConsumer<String, Integer> startReadingFromProject) {
        this.startReadingFromProject = startReadingFromProject;
        this.charset = Charset.forName(System.getProperty("file.encoding"));
    }

    @Override
    public int available() throws IOException {
        synchronized (datas) {
            String projectId = ProjectBuildLogAppender.getProjectId();
            if (!eof && !Objects.equals(projectId, projectReading)) {
                projectReading = projectId;
                startReadingFromProject.accept(projectId, 1);
            }
            return datas.stream().mapToInt(a -> a.length).sum() - Math.max(pos, 0);
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
        synchronized (datas) {
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
                        datas.wait();
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
        }
    }

    public void addInputData(String data) {
        synchronized (datas) {
            if (data == null) {
                eof = true;
            } else {
                datas.add(data.getBytes(charset));
            }
            datas.notifyAll();
        }
    }
}
