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

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File origin:
 * https://github.com/gradle/gradle/blob/v5.6.2/subprojects/messaging/src/main/java/org/gradle/internal/remote/internal/inet/SocketConnection.java.
 */
public class DaemonConnection implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DaemonConnection.class);

    private final SocketChannel socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final SocketAddress localAddress;
    private final SocketAddress remoteAddress;

    public DaemonConnection(SocketChannel socket) throws IOException {
        this.socket = socket;
        try {
            // NOTE: we use non-blocking IO as there is no reliable way when using blocking IO to shutdown reads while
            // keeping writes active. For example, Socket.shutdownInput() does not work on Windows.
            socket.configureBlocking(false);
            this.output = new DataOutputStream(new SocketOutputStream(socket));
            this.input = new DataInputStream(new SocketInputStream(socket));
        } catch (IOException e) {
            throw new DaemonException.InterruptedException(e);
        }
        localAddress = socket.getLocalAddress();
        remoteAddress = socket.getRemoteAddress();
    }

    @Override
    public String toString() {
        return "socket connection from " + localAddress + " to " + remoteAddress;
    }

    public Message receive() throws DaemonException.MessageIOException {
        try {
            return Message.read(input);
        } catch (EOFException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Discarding EOFException: {}", e.toString(), e);
            }
            return null;
        } catch (IOException e) {
            throw new DaemonException.RecoverableMessageIOException(
                    String.format("Could not read message from '%s'.", remoteAddress), e);
        } catch (Throwable e) {
            throw new DaemonException.MessageIOException(
                    String.format("Could not read message from '%s'.", remoteAddress), e);
        }
    }

    private static boolean isEndOfStream(Exception e) {
        if (e instanceof EOFException) {
            return true;
        }
        if (e instanceof IOException) {
            if (Objects.equals(e.getMessage(), "An existing connection was forcibly closed by the remote host")) {
                return true;
            }
            if (Objects.equals(
                    e.getMessage(), "An established connection was aborted by the software in your host machine")) {
                return true;
            }
            if (Objects.equals(e.getMessage(), "Connection reset by peer")) {
                return true;
            }
            if (Objects.equals(e.getMessage(), "Connection reset")) {
                return true;
            }
        }
        return false;
    }

    public void dispatch(Message message) throws DaemonException.MessageIOException {
        try {
            message.write(output);
            output.flush();
        } catch (IOException e) {
            throw new DaemonException.RecoverableMessageIOException(
                    String.format("Could not write message %s to '%s'.", message, remoteAddress), e);
        } catch (Throwable e) {
            throw new DaemonException.MessageIOException(
                    String.format("Could not write message %s to '%s'.", message, remoteAddress), e);
        }
    }

    public void flush() throws DaemonException.MessageIOException {
        try {
            output.flush();
        } catch (Throwable e) {
            throw new DaemonException.MessageIOException(String.format("Could not write '%s'.", remoteAddress), e);
        }
    }

    public void close() {
        Throwable failure = null;
        List<Closeable> elements = Arrays.asList(this::flush, input, output, socket);
        for (Closeable element : elements) {
            try {
                element.close();
            } catch (Throwable throwable) {
                if (failure == null) {
                    failure = throwable;
                } else if (!Thread.currentThread().isInterrupted()) {
                    LOGGER.error("Could not stop {}.", element, throwable);
                }
            }
        }
        if (failure != null) {
            throw new DaemonException(failure);
        }
    }

    private static class SocketInputStream extends InputStream {
        private final Selector selector;
        private final ByteBuffer buffer;
        private final SocketChannel socket;
        private final byte[] readBuffer = new byte[1];

        public SocketInputStream(SocketChannel socket) throws IOException {
            this.socket = socket;
            selector = Selector.open();
            socket.register(selector, SelectionKey.OP_READ);
            buffer = ByteBuffer.allocateDirect(4096);
            BufferCaster.cast(buffer).limit(0);
        }

        @Override
        public int read() throws IOException {
            int nread = read(readBuffer, 0, 1);
            if (nread <= 0) {
                return nread;
            }
            return readBuffer[0] & 0xFF;
        }

        @Override
        public int read(byte[] dest, int offset, int max) throws IOException {
            if (max == 0) {
                return 0;
            }

            if (buffer.remaining() == 0) {
                try {
                    selector.select();
                } catch (ClosedSelectorException e) {
                    return -1;
                }
                if (!selector.isOpen()) {
                    return -1;
                }

                BufferCaster.cast(buffer).clear();
                int nread;
                try {
                    nread = socket.read(buffer);
                } catch (IOException e) {
                    if (isEndOfStream(e)) {
                        BufferCaster.cast(buffer).position(0);
                        BufferCaster.cast(buffer).limit(0);
                        return -1;
                    }
                    throw e;
                }
                BufferCaster.cast(buffer).flip();

                if (nread < 0) {
                    return -1;
                }
            }

            int count = Math.min(buffer.remaining(), max);
            buffer.get(dest, offset, count);
            return count;
        }

        @Override
        public void close() throws IOException {
            selector.close();
        }
    }

    private static class SocketOutputStream extends OutputStream {
        private static final int RETRIES_WHEN_BUFFER_FULL = 2;
        private Selector selector;
        private final SocketChannel socket;
        private final ByteBuffer buffer;
        private final byte[] writeBuffer = new byte[1];

        public SocketOutputStream(SocketChannel socket) throws IOException {
            this.socket = socket;
            buffer = ByteBuffer.allocateDirect(32 * 1024);
        }

        @Override
        public void write(int b) throws IOException {
            writeBuffer[0] = (byte) b;
            write(writeBuffer);
        }

        @Override
        public void write(byte[] src, int offset, int max) throws IOException {
            int remaining = max;
            int currentPos = offset;
            while (remaining > 0) {
                int count = Math.min(remaining, buffer.remaining());
                if (count > 0) {
                    buffer.put(src, currentPos, count);
                    remaining -= count;
                    currentPos += count;
                }
                while (buffer.remaining() == 0) {
                    writeBufferToChannel();
                }
            }
        }

        @Override
        public void flush() throws IOException {
            while (buffer.position() > 0) {
                writeBufferToChannel();
            }
        }

        private void writeBufferToChannel() throws IOException {
            BufferCaster.cast(buffer).flip();
            int count = writeWithNonBlockingRetry();
            if (count == 0) {
                // buffer was still full after non-blocking retries, now block
                waitForWriteBufferToDrain();
            }
            buffer.compact();
        }

        private int writeWithNonBlockingRetry() throws IOException {
            int count = 0;
            int retryCount = 0;
            while (count == 0 && retryCount++ < RETRIES_WHEN_BUFFER_FULL) {
                count = socket.write(buffer);
                if (count < 0) {
                    throw new EOFException();
                } else if (count == 0) {
                    // buffer was full, just call Thread.yield
                    Thread.yield();
                }
            }
            return count;
        }

        private void waitForWriteBufferToDrain() throws IOException {
            if (selector == null) {
                selector = Selector.open();
            }
            SelectionKey key = socket.register(selector, SelectionKey.OP_WRITE);
            // block until ready for write operations
            selector.select();
            // cancel OP_WRITE selection
            key.cancel();
            // complete cancelling key
            selector.selectNow();
        }

        @Override
        public void close() throws IOException {
            if (selector != null) {
                selector.close();
                selector = null;
            }
        }
    }
}
