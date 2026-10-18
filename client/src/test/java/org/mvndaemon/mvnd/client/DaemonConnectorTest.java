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

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.common.DaemonException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DaemonConnectorTest {

    @Test
    public void newId() {
        String id = DaemonConnector.newId();
        assertNotNull(id);
        assertEquals(8, id.length());
    }

    @Test
    public void connectsOnlyToLoopback() throws Exception {
        assertDoesNotThrow(
                () -> DaemonConnector.checkLocal(new InetSocketAddress(InetAddress.getLoopbackAddress(), 4000)));
        assertDoesNotThrow(() -> DaemonConnector.checkLocal(
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 4000)));
        assertThrows(
                DaemonException.ConnectException.class,
                () -> DaemonConnector.checkLocal(
                        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), 4000)));
        assertThrows(
                DaemonException.ConnectException.class,
                () -> DaemonConnector.checkLocal(InetSocketAddress.createUnresolved("example.com", 4000)));
    }

    @Test
    public void detectsDeadProcesses() throws Exception {
        assertTrue(DaemonConnector.isAlive((int) ProcessHandle.current().pid()));
        Process p =
                new ProcessBuilder("java", "-version").redirectErrorStream(true).start();
        int pid = (int) p.pid();
        p.waitFor();
        assertFalse(DaemonConnector.isAlive(pid));
    }
}
