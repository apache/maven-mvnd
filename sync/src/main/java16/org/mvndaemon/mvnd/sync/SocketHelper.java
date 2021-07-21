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
package org.mvndaemon.mvnd.sync;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.Objects;

import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static java.net.StandardProtocolFamily.UNIX;

public class SocketHelper {

    public static void checkFamily(StandardProtocolFamily family, SocketAddress address) {
        Objects.requireNonNull(family);
        Objects.requireNonNull(address);
        if (address instanceof InetSocketAddress) {
            InetSocketAddress isa = (InetSocketAddress) address;
            InetAddress ia = isa.getAddress();
            if (ia != null
                    && !(ia instanceof Inet4Address && family == INET)
                    && !(ia instanceof Inet6Address && family == INET6)) {
                throw new IllegalArgumentException("Socket address '" + address + "' does not match required family '" + family + "'");
            }
        } else if (address instanceof UnixDomainSocketAddress) {
            if (family != UNIX) {
                throw new IllegalArgumentException("Socket address '" + address + "' does not match required family '" + family + "'");
            }
        } else {
            throw new IllegalArgumentException("Socket address '" + address + "' does not match required family '" + family + "'");
        }
    }

    public static SocketAddress socketAddressFromString(String str) {
        if (str.startsWith("inet:")) {
            String s = str.substring("inet:".length());
            int ic = s.lastIndexOf(':');
            String ia = s.substring(0, ic);
            int is = ia.indexOf('/');
            String h = ia.substring(0, is);
            String a = ia.substring(is + 1);
            String p = s.substring(ic + 1);
            InetAddress addr;
            if ("<unresolved>".equals(a)) {
                return InetSocketAddress.createUnresolved(h, Integer.parseInt(p));
            } else {
                if (a.indexOf('.') > 0) {
                    String[] as = a.split("\\.");
                    if (as.length != 4) {
                        throw new IllegalArgumentException("Unsupported address: " + str);
                    }
                    byte[] ab = new byte[4];
                    for (int i = 0; i < 4; i++) {
                        ab[i] = (byte) Integer.parseInt(as[i]);
                    }
                    try {
                        addr = InetAddress.getByAddress(h.isEmpty() ? null : h, ab);
                    } catch (UnknownHostException e) {
                        throw new IllegalArgumentException("Unsupported address: " + str, e);
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported address: " + str);
                }
                return new InetSocketAddress(addr, Integer.parseInt(p));
            }
        } else if (str.startsWith("unix:")) {
            return UnixDomainSocketAddress.of(str.substring("unix:".length()));
        } else {
            throw new IllegalArgumentException("Unsupported address: " + str);
        }
    }

    public static String socketAddressToString(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            InetSocketAddress isa = (InetSocketAddress) address;
            String host = isa.getHostString();
            InetAddress addr = isa.getAddress();
            int port = isa.getPort();
            String formatted;
            if (addr == null) {
                formatted = host + "/<unresolved>";
            } else {
                formatted = addr.toString();
                if (addr instanceof Inet6Address) {
                    int i = formatted.lastIndexOf("/");
                    formatted = formatted.substring(0, i + 1) + "[" + formatted.substring(i + 1) + "]";
                }
            }
            return "inet:" + formatted + ":" + port;
        } else if (address instanceof UnixDomainSocketAddress) {
            return "unix:" + address;
        } else {
            throw new IllegalArgumentException("Unsupported address: " + address);
        }
    }

    public static ServerSocketChannel openServerSocket(StandardProtocolFamily family) throws IOException {
        return ServerSocketChannel.open(family).bind(getLoopbackAddress(family), 0);
    }

    private static SocketAddress getLoopbackAddress(StandardProtocolFamily family) {
        try {
            Objects.requireNonNull(family);
            switch (family) {
                case INET:
                    return new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), 0);
                case INET6:
                    return new InetSocketAddress(Inet6Address.getByAddress(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 }), 0);
                case UNIX:
                    return null;
                default:
                    throw new IllegalArgumentException("Unsupported family: " + family);
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unsupported family: " + family, e);
        }
    }

    public static ByteChannel wrapChannel(ByteChannel channel) {
        return new ByteChannelWrapper(channel);
    }

}
