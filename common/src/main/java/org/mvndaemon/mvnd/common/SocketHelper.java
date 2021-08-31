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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class SocketHelper {

    public static SocketChannel openUnixSocket() throws IOException {
        throw new UnsupportedOperationException("Unix sockets are supported only on JDK >= 16");
    }

    public static ServerSocketChannel openUnixServerSocket() throws IOException {
        throw new UnsupportedOperationException("Unix sockets are supported only on JDK >= 16");
    }

    public static SocketAddress unixSocketAddressOf(String s) {
        throw new UnsupportedOperationException("Unix sockets are supported only on JDK >= 16");
    }
}
