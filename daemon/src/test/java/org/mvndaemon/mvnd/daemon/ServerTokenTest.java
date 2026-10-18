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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerTokenTest {

    @Test
    void readsTheFullToken() throws IOException {
        byte[] sent = new byte[16];
        for (int i = 0; i < sent.length; i++) {
            sent[i] = (byte) (i + 1);
        }
        try (ServerSocketChannel server = listen();
                SocketChannel client = SocketChannel.open(server.getLocalAddress());
                SocketChannel accepted = server.accept()) {
            client.write(ByteBuffer.wrap(sent));
            assertArrayEquals(sent, Server.readToken(accepted, 16, 5_000));
            assertTrue(accepted.isBlocking(), "the channel is handed back in blocking mode");
        }
    }

    @Test
    void givesUpOnASilentPeer() throws IOException {
        try (ServerSocketChannel server = listen();
                SocketChannel client = SocketChannel.open(server.getLocalAddress());
                SocketChannel accepted = server.accept()) {
            client.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));
            long start = System.nanoTime();
            byte[] token = Server.readToken(accepted, 16, 300);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertTrue(elapsedMs < 5_000, "returned after " + elapsedMs + " ms");
            assertArrayEquals(new byte[] {1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, token);
        }
    }

    private static ServerSocketChannel listen() throws IOException {
        return ServerSocketChannel.open().bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    }
}
