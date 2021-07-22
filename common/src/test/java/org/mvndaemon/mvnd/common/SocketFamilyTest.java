/*
 * Copyright 2021 the original author or authors.
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SocketFamilyTest {

    @Test
    void testInetNullHost() throws UnknownHostException {
        InetSocketAddress i4a = new InetSocketAddress(
                InetAddress.getByAddress(null, new byte[] { (byte) 192, (byte) 168, 0, 1 }), 8080);

        assertEquals("inet:/192.168.0.1:8080", SocketFamily.toString(i4a));
        assertEquals(i4a, SocketFamily.fromString("inet:/192.168.0.1:8080"));
    }

    @Test
    void testInetDummyHost() throws UnknownHostException {
        InetSocketAddress i4a = new InetSocketAddress(
                InetAddress.getByAddress("dummy.org", new byte[] { (byte) 192, (byte) 168, 0, 1 }), 8080);

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
    @EnabledForJreRange(min = JRE.JAVA_16)
    @Disabled("Test framework does not support multi-release expanded jars")
    void testUnixFromTo() {
        SocketAddress address = SocketFamily.fromString("unix:/tmp/foo-0123456.socket");
        assertEquals(SocketFamily.unix, SocketFamily.familyOf(address));
        assertEquals("unix:/tmp/foo-0123456.socket", SocketFamily.toString(address));
    }

}
