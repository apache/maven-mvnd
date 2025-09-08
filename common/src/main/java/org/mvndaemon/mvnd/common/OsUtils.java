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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OsUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(OsUtils.class);
    private static final long KB = 1024;
    private static final String UNITS = "Bkmgt";

    // Pattern for parsing tasklist CSV output memory column (handles "1,234 K" format)
    private static final Pattern MEMORY_PATTERN = Pattern.compile("([\\d,]+)\\s*K?");

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
            return findProcessRssUnix(pid);
        } else if (os == Os.WINDOWS) {
            return findProcessRssWindows(pid);
        } else {
            return -1;
        }
    }

    private static long findProcessRssUnix(long pid) {
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
    }

    private static long findProcessRssWindows(long pid) {
        // Try modern PowerShell approach first (Windows 7+ with PowerShell 2.0+)
        long result = tryPowerShellMemory(pid);
        if (result > 0) {
            return result;
        }

        // Fallback to wmic for older systems or if PowerShell fails
        result = tryWmicMemory(pid);
        if (result > 0) {
            return result;
        }

        // Final fallback to tasklist (most compatible, works from Windows XP to Windows 11)
        return tryTasklistMemory(pid);
    }

    private static long tryPowerShellMemory(long pid) {
        // Use PowerShell with error handling to get WorkingSet64
        String[] cmd = {
            "powershell",
            "-Command",
            "try { " + "(Get-Process -Id "
                    + pid + " -ErrorAction Stop).WorkingSet64 " + "} catch { "
                    + "Write-Output 'ERROR' "
                    + "}"
        };

        final List<String> output = new ArrayList<>(1);
        exec(cmd, output);

        if (!output.isEmpty()) {
            String result = output.get(0).trim();
            if (!result.isEmpty() && !result.equals("ERROR") && !result.contains("Get-Process")) {
                try {
                    return Long.parseLong(result) / KB;
                } catch (NumberFormatException e) {
                    LOGGER.debug("Could not parse PowerShell output as a long: {}", result);
                }
            }
        }
        return -1;
    }

    private static long tryWmicMemory(long pid) {
        String[] cmd = {"wmic", "process", "where", "processid=" + pid, "get", "WorkingSetSize"};
        final List<String> output = new ArrayList<>(1);
        exec(cmd, output);
        final List<String> nonEmptyLines =
                output.stream().filter(l -> !l.isEmpty()).collect(Collectors.toList());
        if (nonEmptyLines.size() >= 2) {
            try {
                return Long.parseLong(nonEmptyLines.get(1).trim()) / KB;
            } catch (NumberFormatException e) {
                LOGGER.debug(
                        "Could not parse wmic output as a long: {}",
                        nonEmptyLines.get(1).trim());
            }
        }
        return -1;
    }

    private static long tryTasklistMemory(long pid) {
        // Use tasklist with CSV format for easier parsing
        String[] cmd = {"tasklist", "/fi", "PID eq " + pid, "/fo", "csv"};
        final List<String> output = new ArrayList<>();
        exec(cmd, output);

        if (output.size() >= 2) { // Header + data row
            try {
                // Parse CSV line - memory is typically in the 5th column (index 4)
                String dataLine = output.get(1);
                String[] fields = parseCsvLine(dataLine);

                if (fields.length >= 5) {
                    String memoryField = fields[4].trim();
                    // Remove quotes if present and parse memory value
                    memoryField = memoryField.replaceAll("\"", "");

                    Matcher matcher = MEMORY_PATTERN.matcher(memoryField);
                    if (matcher.find()) {
                        String memoryStr = matcher.group(1).replaceAll(",", "");
                        return Long.parseLong(memoryStr);
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Could not parse tasklist output: {}", e.getMessage());
            }
        } else if (output.size() == 1 && output.get(0).contains("No tasks")) {
            // Process not found
            LOGGER.debug("Process {} not found", pid);
        } else {
            LOGGER.debug("Unexpected tasklist output for PID {}: {}", pid, String.join("\n", output));
        }
        return -1;
    }

    /**
     * Simple CSV line parser that handles quoted fields
     */
    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        fields.add(currentField.toString());

        return fields.toArray(new String[0]);
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
                // Only log as debug for memory queries to avoid spam in logs
                if (isMemoryQuery(cmd)) {
                    LOGGER.debug(
                            "{} exited with {}: {}",
                            String.join(" ", cmd),
                            exitCode,
                            output.isEmpty() ? "no output" : output.get(0));
                } else {
                    LOGGER.warn("{} exited with {}:\n{}", String.join(" ", cmd), exitCode, String.join("\n", output));
                }
            }
        } catch (IOException e) {
            if (isMemoryQuery(cmd)) {
                LOGGER.debug("Could not execute {}: {}", String.join(" ", cmd), e.getMessage());
            } else {
                LOGGER.warn("Could not execute {}", String.join(" ", cmd));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isMemoryQuery(String[] cmd) {
        if (cmd.length == 0) return false;
        String firstCmd = cmd[0].toLowerCase();
        return firstCmd.contains("powershell") || firstCmd.contains("wmic") || firstCmd.contains("tasklist");
    }

    /**
     * A simple wrapper over {@link Process} that manages its destroying and offers Java 8-like
     * {@link #waitFor(long)} with timeout.
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
