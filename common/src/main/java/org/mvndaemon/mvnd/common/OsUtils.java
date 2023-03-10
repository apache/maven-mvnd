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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OsUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(OsUtils.class);
    private static final long KB = 1024;
    private static final String UNITS = "Bkmgt";

    private OsUtils() {}

    public static String bytesToHumanReadable(long bytes) {
        int unit = 0;
        while (bytes >= KB && unit < UNITS.length() - 1) {
            bytes /= KB;
            unit++;
        }
        String bytesString = String.valueOf(bytes);
        return bytesString + UNITS.charAt(unit);
    }

    public static String kbToHumanReadable(long kb) {
        int unit = 1;
        while (kb >= KB && unit < UNITS.length() - 1) {
            kb /= KB;
            unit++;
        }
        String kbString = String.valueOf(kb);
        return kbString + UNITS.charAt(unit);
    }

    public static long findProcessRssInKb(long pid) {
        final Os os = Os.current();
        if (os.isUnixLike()) {
            String[] cmd = {"ps", "-o", "rss=", "-p", String.valueOf(pid)};
            final List<String> output = new ArrayList<>(1);
            exec(cmd, output);
            if (output.size() == 1) {
                try {
                    return Long.parseLong(output.get(0).trim());
                } catch (NumberFormatException e) {
                    LOGGER.warn(
                            "Could not parse the output of {} as a long:\n{}",
                            String.join(" ", cmd),
                            String.join("\n", output));
                }
            } else {
                LOGGER.warn("Unexpected output of {}:\n{}", String.join(" ", cmd), String.join("\n", output));
            }
            return -1;
        } else if (os == Os.WINDOWS) {
            String[] cmd = {"wmic", "process", "where", "processid=" + pid, "get", "WorkingSetSize"};
            final List<String> output = new ArrayList<>(1);
            exec(cmd, output);
            final List<String> nonEmptyLines =
                    output.stream().filter(l -> !l.isEmpty()).collect(Collectors.toList());
            if (nonEmptyLines.size() >= 2) {
                try {
                    return Long.parseLong(nonEmptyLines.get(1).trim()) / KB;
                } catch (NumberFormatException e) {
                    LOGGER.warn(
                            "Could not parse the second line of {} output as a long:\n{}",
                            String.join(" ", cmd),
                            String.join("\n", nonEmptyLines));
                }
            } else {
                LOGGER.warn("Unexpected output of {}:\n{}", String.join(" ", cmd), String.join("\n", output));
            }
            return -1;
        } else {
            return -1;
        }
    }

    /**
     * Executes the given {@code javaExecutable} with {@code -XshowSettings:properties -version} parameters and extracts
     * the value of {@code java.home} from the output.
     *
     * @param  javaExecutable pass {@code "java"} to get {@code java} binary available in {@code PATH} environment
     *                        variable or pass an absolute path to a {@code "java"} executable
     * @return                a {@code java.home} value or null
     */
    public static String findJavaHomeFromJavaExecutable(String javaExecutable) {
        String[] cmd = {javaExecutable, "-XshowSettings:properties", "-version"};
        final List<String> output = new ArrayList<>();
        exec(cmd, output);
        return output.stream()
                .filter(l -> l.contains(" java.home = "))
                .map(l -> l.substring(l.indexOf('=') + 1).trim())
                .findFirst()
                .orElse(null);
    }

    private static void exec(String[] cmd, final List<String> output) {
        final ProcessBuilder builder = new ProcessBuilder(cmd).redirectErrorStream(true);
        try (CommandProcess ps = new CommandProcess(builder.start(), output::add)) {
            final int exitCode = ps.waitFor(1000);
            if (exitCode != 0) {
                LOGGER.warn("{} exited with {}:\n{}", String.join(" ", cmd), exitCode, String.join("\n", output));
            }
        } catch (IOException e) {
            LOGGER.warn("Could not execute {}", String.join(" ", cmd));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A simple wrapper over {@link Process} that manages its destroying and offers Java 8-like
     * {@link #waitFor(long, TimeUnit, String[])} with timeout.
     */
    public static class CommandProcess implements AutoCloseable {
        public static final int TIMEOUT_EXIT_CODE = Integer.MIN_VALUE + 42;

        /**
         * The usual friend of {@link Process#getInputStream()} / {@link Process#getErrorStream()}.
         */
        static class StreamGobbler extends Thread {
            private volatile boolean cancelled;
            private IOException exception;
            private final InputStream in;
            private final Consumer<String> out;

            private StreamGobbler(InputStream in, Consumer<String> out) {
                this.in = in;
                this.out = out;
            }

            public void assertSuccess() throws IOException {
                if (exception != null) {
                    throw exception;
                }
            }

            public void cancel() {
                this.cancelled = true;
            }

            @Override
            public void run() {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while (!cancelled && (line = r.readLine()) != null) {
                        out.accept(line);
                    }
                } catch (IOException e) {
                    exception = e;
                }
            }
        }

        private final Process process;
        private final Thread shutDownHook;
        private final StreamGobbler stdOut;

        public CommandProcess(Process process, Consumer<String> outputConsumer) {
            super();
            this.process = process;
            this.stdOut = new StreamGobbler(process.getInputStream(), outputConsumer);
            stdOut.start();

            this.shutDownHook = new Thread(new Runnable() {
                @Override
                public void run() {
                    stdOut.cancel();
                    CommandProcess.this.process.destroy();
                }
            });
            Runtime.getRuntime().addShutdownHook(shutDownHook);
        }

        @Override
        public void close() {
            process.destroy();
        }

        public int waitFor(long timeoutMs) throws InterruptedException, IOException {
            final long deadline = System.currentTimeMillis() + timeoutMs;
            final boolean timeouted = !process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            timeoutMs = Math.max(0, deadline - System.currentTimeMillis());
            stdOut.join(timeoutMs);
            stdOut.assertSuccess();
            try {
                Runtime.getRuntime().removeShutdownHook(shutDownHook);
            } catch (Exception ignored) {
            }
            final int exitCode = timeouted ? TIMEOUT_EXIT_CODE : process.exitValue();
            return exitCode;
        }
    }
}
