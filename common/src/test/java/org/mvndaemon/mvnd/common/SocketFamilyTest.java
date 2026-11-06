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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SocketFamilyTest {

    @Test
    void testInetNullHost() throws UnknownHostException {
        InetSocketAddress i4a =
                new InetSocketAddress(InetAddress.getByAddress(null, new byte[] {(byte) 192, (byte) 168, 0, 1}), 8080);

        assertEquals("inet:/192.168.0.1:8080", SocketFamily.toString(i4a));
        assertEquals(i4a, SocketFamily.fromString("inet:/192.168.0.1:8080"));
    }

    @Test
    void testInetDummyHost() throws UnknownHostException {
        InetSocketAddress i4a = new InetSocketAddress(
                InetAddress.getByAddress("dummy.org", new byte[] {(byte) 192, (byte) 168, 0, 1}), 8080);

        assertEquals("inet:dummy.org/192.168.0.1:8080", SocketFamily.toString(i4a));
        assertEquals(i4a, SocketFamily.fromString("inet:dummy.org/192.168.0.1:8080"));
    }

    @Test
    void testInetLoopback() throws UnknownHostException {
        InetSocketAddress i4a = new InetSocketAddress(8080);

        assertEquals("inet:0.0.0.0/0.0.0.0:8080", SocketFamily.toString(i4a));
        assertEquals(i4a, SocketFamily.fromString("inet:0.0.0.0/0.0.0.0:8080"));
    }

    @Test
    void testInetUnresolved() throws UnknownHostException {
        InetSocketAddress i4a = InetSocketAddress.createUnresolved("google.com", 8080);

        assertEquals("inet:google.com/<unresolved>:8080", SocketFamily.toString(i4a));
        assertEquals(i4a, SocketFamily.fromString("inet:google.com/<unresolved>:8080"));
    }

    @Test
    void testInetIpv6Loopback() throws UnknownHostException {
        InetSocketAddress i6a = new InetSocketAddress(
                InetAddress.getByAddress("localhost", new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}),
                4000);

        assertEquals("inet:localhost/[0:0:0:0:0:0:0:1]:4000", SocketFamily.toString(i6a));
        assertEquals(i6a, SocketFamily.fromString("inet:localhost/[0:0:0:0:0:0:0:1]:4000"));
        assertEquals(i6a, SocketFamily.fromString(SocketFamily.toString(i6a)));
    }

    @Test
    void testInetIpv6NullHost() throws UnknownHostException {
        InetSocketAddress i6a = new InetSocketAddress(
                InetAddress.getByAddress(
                        null, new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}),
                8080);

        assertEquals(i6a, SocketFamily.fromString(SocketFamily.toString(i6a)));
    }

    @Test
    void testInetMalformed() {
        for (String s : new String[] {
            "inet:localhost:4000",
            "inet:",
            "inet:/",
            "inet:/1.2.3:4000",
            "inet:/1.2.3.4",
            "inet:/1.2.3.4:abc",
            "inet:/1.2.3.x:4000",
            "inet:/[::1:4000",
            "inet:/[nonsense]:4000",
            "inet:/[]:4000",
            "inet:host/name:4000",
            "bogus:/1.2.3.4:4000"
        }) {
            IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class, () -> SocketFamily.fromString(s), s);
            assertEquals("Unsupported socket address: '" + s + "'", e.getMessage());
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_16)
    @Disabled(
            "The Java 16 SocketHelper is in META-INF/versions/16, which surefire does not see on the target/classes directory")
    void testUnixFromTo() {
        SocketAddress address = SocketFamily.fromString("unix:/tmp/foo-0123456.socket");
        assertEquals(SocketFamily.unix, SocketFamily.familyOf(address));
        assertEquals("unix:/tmp/foo-0123456.socket", SocketFamily.toString(address));
    }
}
