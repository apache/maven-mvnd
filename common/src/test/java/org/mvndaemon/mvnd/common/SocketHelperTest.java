/*
 * Copyright 2019 the original author or authors.
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
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SocketHelperTest {

    @Test
    void testIpv4NullHost() throws UnknownHostException {
        InetSocketAddress i4a = new InetSocketAddress(
                InetAddress.getByAddress(null, new byte[] { (byte) 192, (byte) 168, 0, 1 }), 8080);

        assertEquals("inet:/192.168.0.1:8080", SocketHelper.socketAddressToString(i4a));
        assertEquals(i4a, SocketHelper.socketAddressFromString("inet:/192.168.0.1:8080"));
    }

    @Test
    void testIpv4DummyHost() throws UnknownHostException {
        InetSocketAddress i4a = new InetSocketAddress(
                InetAddress.getByAddress("dummy.org", new byte[] { (byte) 192, (byte) 168, 0, 1 }), 8080);

        assertEquals("inet:dummy.org/192.168.0.1:8080", SocketHelper.socketAddressToString(i4a));
        assertEquals(i4a, SocketHelper.socketAddressFromString("inet:dummy.org/192.168.0.1:8080"));
    }

    @Test
    void testIpv4Loopback() throws UnknownHostException {
        InetSocketAddress i4a = new InetSocketAddress(8080);

        assertEquals("inet:0.0.0.0/0.0.0.0:8080", SocketHelper.socketAddressToString(i4a));
        assertEquals(i4a, SocketHelper.socketAddressFromString("inet:0.0.0.0/0.0.0.0:8080"));
    }

    @Test
    void testIpv4Unresolved() throws UnknownHostException {
        InetSocketAddress i4a = InetSocketAddress.createUnresolved("google.com", 8080);

        assertEquals("inet:google.com/<unresolved>:8080", SocketHelper.socketAddressToString(i4a));
        assertEquals(i4a, SocketHelper.socketAddressFromString("inet:google.com/<unresolved>:8080"));
    }

    @Test
    void testCheckInetAddress() {
        String family = "INET";
        String address = "inet:/127.0.0.1:8192";
        SocketHelper.checkFamily(StandardProtocolFamily.valueOf(family),
                SocketHelper.socketAddressFromString(address));
    }
}
