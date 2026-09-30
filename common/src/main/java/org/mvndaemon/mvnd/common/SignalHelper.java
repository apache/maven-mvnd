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

public class SignalHelper {

    /**
     * Ignore signals to that stopping the mvnd client won't stop the daemon
     */
    public static void ignoreStopSignals() throws Exception {
        handle("INT");
        if (Os.current() != Os.WINDOWS) {
            handle("TSTP");
        }
    }

    /**
     * Equivalent of {@code sun.misc.Signal.handle(new sun.misc.Signal(signal), sun.misc.SignalHandler.SIG_IGN)},
     * done reflectively because {@code sun.misc} is not reachable when compiling with {@code --release}.
     */
    private static void handle(String signal) throws Exception {
        Class<?> signalClass = Class.forName("sun.misc.Signal");
        Class<?> handlerClass = Class.forName("sun.misc.SignalHandler");
        Object sigIgn = handlerClass.getField("SIG_IGN").get(null);
        Object sig = signalClass.getConstructor(String.class).newInstance(signal);
        signalClass.getMethod("handle", signalClass, handlerClass).invoke(null, sig, sigIgn);
    }
}
