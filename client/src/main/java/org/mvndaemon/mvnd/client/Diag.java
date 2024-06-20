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

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ServiceLoader;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.AbstractPosixTerminal;
import org.jline.terminal.spi.SystemStream;
import org.jline.terminal.spi.TerminalProvider;
import org.jline.utils.OSUtils;

public class Diag {

    public static void main(String[] args) {
        diag(System.out);
    }

    public static void diag(PrintStream out) {
        out.println("System properties");
        out.println("=================");
        out.println("os.name =         " + System.getProperty("os.name"));
        out.println("OSTYPE =          " + System.getenv("OSTYPE"));
        out.println("MSYSTEM =         " + System.getenv("MSYSTEM"));
        out.println("PWD =             " + System.getenv("PWD"));
        out.println("ConEmuPID =       " + System.getenv("ConEmuPID"));
        out.println("WSL_DISTRO_NAME = " + System.getenv("WSL_DISTRO_NAME"));
        out.println("WSL_INTEROP =     " + System.getenv("WSL_INTEROP"));
        out.println();

        out.println("OSUtils");
        out.println("=================");
        out.println("IS_WINDOWS = " + OSUtils.IS_WINDOWS);
        out.println("IS_CYGWIN =  " + OSUtils.IS_CYGWIN);
        out.println("IS_MSYSTEM = " + OSUtils.IS_MSYSTEM);
        out.println("IS_WSL =     " + OSUtils.IS_WSL);
        out.println("IS_WSL1 =    " + OSUtils.IS_WSL1);
        out.println("IS_WSL2 =    " + OSUtils.IS_WSL2);
        out.println("IS_CONEMU =  " + OSUtils.IS_CONEMU);
        out.println("IS_OSX =     " + OSUtils.IS_OSX);
        out.println();

        // FFM
        out.println("FFM Support");
        out.println("=================");
        try {
            TerminalProvider provider = TerminalProvider.load("ffm");
            testProvider(out, provider);
        } catch (Throwable t) {
            out.println("FFM support not available: " + t);
        }
        out.println();

        out.println("JnaSupport");
        out.println("=================");
        try {
            TerminalProvider provider = TerminalProvider.load("jna");
            testProvider(out, provider);
        } catch (Throwable t) {
            out.println("JNA support not available: " + t);
        }
        out.println();

        out.println("Jansi2Support");
        out.println("=================");
        try {
            TerminalProvider provider = TerminalProvider.load("jansi");
            testProvider(out, provider);
        } catch (Throwable t) {
            out.println("Jansi 2 support not available: " + t);
        }
        out.println();

        out.println("JniSupport");
        out.println("=================");
        try {
            TerminalProvider provider = TerminalProvider.load("jni");
            testProvider(out, provider);
        } catch (Throwable t) {
            out.println("JNI support not available: " + t);
        }
        out.println();

        // Exec
        out.println("Exec Support");
        out.println("=================");
        try {
            TerminalProvider provider = TerminalProvider.load("exec");
            testProvider(out, provider);
        } catch (Throwable t) {
            out.println("Exec support not available: " + t);
        }
    }

    private static void testProvider(PrintStream out, TerminalProvider provider) {
        try {
            out.println("StdIn stream =    " + provider.isSystemStream(SystemStream.Input));
            out.println("StdOut stream =   " + provider.isSystemStream(SystemStream.Output));
            out.println("StdErr stream =   " + provider.isSystemStream(SystemStream.Error));
        } catch (Throwable t2) {
            out.println("Unable to check stream: " + t2);
        }
        try {
            out.println("StdIn stream name =     " + provider.systemStreamName(SystemStream.Input));
            out.println("StdOut stream name =    " + provider.systemStreamName(SystemStream.Output));
            out.println("StdErr stream name =    " + provider.systemStreamName(SystemStream.Error));
        } catch (Throwable t2) {
            out.println("Unable to check stream names: " + t2);
        }
        try (Terminal terminal = provider.sysTerminal(
                "diag",
                "xterm",
                false,
                StandardCharsets.UTF_8,
                false,
                Terminal.SignalHandler.SIG_DFL,
                false,
                SystemStream.Output)) {
            if (terminal != null) {
                Attributes attr = terminal.enterRawMode();
                try {
                    out.println("Terminal size: " + terminal.getSize());
                    ForkJoinTask<Integer> t =
                            new ForkJoinPool(1).submit(() -> terminal.reader().read(1));
                    int r = t.get(1000, TimeUnit.MILLISECONDS);
                    StringBuilder sb = new StringBuilder();
                    sb.append("The terminal seems to work: ");
                    sb.append("terminal ").append(terminal.getClass().getName());
                    if (terminal instanceof AbstractPosixTerminal) {
                        sb.append(" with pty ")
                                .append(((AbstractPosixTerminal) terminal)
                                        .getPty()
                                        .getClass()
                                        .getName());
                    }
                    out.println(sb);
                } catch (Throwable t3) {
                    out.println("Unable to read from terminal: " + t3);
                    printStackTrace(t3, out);
                } finally {
                    terminal.setAttributes(attr);
                }
            } else {
                out.println("Not supported by provider");
            }
        } catch (Throwable t2) {
            out.println("Unable to open terminal: " + t2);
            printStackTrace(t2, out);
        }
    }

    private static void printStackTrace(Throwable t, PrintStream out) {
        t.printStackTrace(out);
        if (t.getCause() == null) {
            out.println("No cause in the Throwable");
        } else {
            printStackTrace(t, out);
        }
    }

    static <S> S load(Class<S> clazz) {
        return ServiceLoader.load(clazz, clazz.getClassLoader()).iterator().next();
    }
}
