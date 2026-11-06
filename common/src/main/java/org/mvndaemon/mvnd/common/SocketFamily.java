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

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Locale;

public enum SocketFamily {
    inet,
    unix;

    public SocketChannel openSocket() throws IOException {
        switch (this) {
            case inet:
                return SocketChannel.open();
            case unix:
                return SocketHelper.openUnixSocket();
            default:
                throw new IllegalStateException();
        }
    }

    public ServerSocketChannel openServerSocket() throws IOException {
        switch (this) {
            case inet:
                return ServerSocketChannel.open().bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            case unix:
                return SocketHelper.openUnixServerSocket();
            default:
                throw new IllegalStateException();
        }
    }

    private StandardProtocolFamily getStandardProtocolFamily() {
        return StandardProtocolFamily.valueOf(name().toUpperCase(Locale.ROOT));
    }

    private SocketAddress getLoopbackAddress() {
        if (this == inet) {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        } else {
            return null;
        }
    }

    public static SocketAddress fromString(String str) {
        if (str.startsWith("inet:")) {
            String s = str.substring("inet:".length());
            int ic = s.lastIndexOf(':');
            int is = ic > 0 ? s.indexOf('/') : -1;
            if (is < 0 || is > ic) {
                throw new IllegalArgumentException("Unsupported socket address: '" + str + "'");
            }
            String h = s.substring(0, is);
            String a = s.substring(is + 1, ic);
            String p = s.substring(ic + 1);
            try {
                int port = Integer.parseInt(p);
                if ("<unresolved>".equals(a)) {
                    return InetSocketAddress.createUnresolved(h, port);
                }
                byte[] ab;
                if (a.length() > 2 && a.charAt(0) == '[' && a.charAt(a.length() - 1) == ']') {
                    // bracketed IPv6 literal, optionally followed by a scope id
                    String lit = a.substring(1, a.length() - 1);
                    int pc = lit.indexOf('%');
                    if (pc >= 0) {
                        lit = lit.substring(0, pc);
                    }
                    if (lit.indexOf(':') < 0) {
                        throw new IllegalArgumentException("Unsupported socket address: '" + str + "'");
                    }
                    ab = InetAddress.getByName(lit).getAddress();
                } else if (a.indexOf('.') > 0) {
                    String[] as = a.split("\\.");
                    if (as.length != 4) {
                        throw new IllegalArgumentException("Unsupported socket address: '" + str + "'");
                    }
                    ab = new byte[4];
                    for (int i = 0; i < 4; i++) {
                        ab[i] = (byte) Integer.parseInt(as[i]);
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported socket address: '" + str + "'");
                }
                return new InetSocketAddress(InetAddress.getByAddress(h.isEmpty() ? null : h, ab), port);
            } catch (UnknownHostException | NumberFormatException e) {
                throw new IllegalArgumentException("Unsupported socket address: '" + str + "'", e);
            }
        } else if (str.startsWith("unix:")) {
            return SocketHelper.unixSocketAddressOf(str.substring("unix:".length()));
        } else {
            throw new IllegalArgumentException("Unsupported socket address: '" + str + "'");
        }
    }

    public static String toString(SocketAddress address) {
        switch (familyOf(address)) {
            case inet:
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
            case unix:
                return "unix:" + address;
            default:
                throw new IllegalArgumentException("Unsupported socket address: '" + address + "'");
        }
    }

    public static SocketFamily familyOf(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            return SocketFamily.inet;
        } else if ("java.net.UnixDomainSocketAddress".equals(address.getClass().getName())) {
            return SocketFamily.unix;
        } else {
            throw new IllegalArgumentException("Unsupported socket address '" + address + "'");
        }
    }
}
