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
package org.jboss.fuse.mvnd.junit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.ClientLayout;
import org.jboss.fuse.mvnd.client.ClientOutput;
import org.jboss.fuse.mvnd.client.Environment;
import org.jboss.fuse.mvnd.client.ExecutionResult;

/**
 * A wrapper around the native executable.
 */
public class NativeTestClient implements Client {

    public static final int TIMEOUT_EXIT_CODE = Integer.MIN_VALUE + 42;

    private final ClientLayout layout;

    private final Path mvndNativeExecutablePath;

    private final long timeoutMs;

    public NativeTestClient(ClientLayout layout, Path mvndNativeExecutablePath, long timeoutMs) {
        super();
        this.layout = layout;
        this.mvndNativeExecutablePath = mvndNativeExecutablePath;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public ExecutionResult execute(ClientOutput output, List<String> args) throws InterruptedException {
        final List<String> cmd = new ArrayList<String>(args.size() + 1);
        cmd.add(mvndNativeExecutablePath.toString());
        args.stream().forEach(cmd::add);
        if (!Environment.MVND_PROPERTIES_PATH.hasCommandLineProperty(args)) {
            cmd.add(Environment.MVND_PROPERTIES_PATH.asCommandLineProperty(layout.getMvndPropertiesPath().toString()));
        }
        if (!Environment.MAVEN_REPO_LOCAL.hasCommandLineProperty(args)) {
            cmd.add(Environment.MAVEN_REPO_LOCAL.asCommandLineProperty(layout.getLocalMavenRepository().toString()));
        }
        final ProcessBuilder builder = new ProcessBuilder(cmd.toArray(new String[0]))
                .directory(layout.userDir().toFile()) //
                .redirectErrorStream(true);

        final Map<String, String> env = builder.environment();
        if (!Environment.MVND_HOME.hasCommandLineProperty(args)) {
            env.put("MVND_HOME", System.getProperty("mvnd.home"));
        }
        if (!Environment.JAVA_HOME.hasCommandLineProperty(args)) {
            env.put("JAVA_HOME", System.getProperty("java.home"));
        }
        final String cmdString = cmd.stream().collect(Collectors.joining(" "));
        output.accept("Executing " + cmdString);
        try (CommandProcess process = new CommandProcess(builder.start(), cmd, output)) {
            return process.waitFor(timeoutMs);
        } catch (IOException e) {
            throw new RuntimeException("Could not execute: " + cmdString, e);
        }
    }

    public static class Result implements ExecutionResult {

        private final int exitCode;
        private final List<String> args;
        private final List<String> log;

        public Result(List<String> args, int exitCode, List<String> log) {
            super();
            this.args = new ArrayList<>(args);
            this.exitCode = exitCode;
            this.log = log;
        }

        StringBuilder appendCommand(StringBuilder sb) {
            for (String arg : args) {
                sb.append(" \"").append(arg).append('"');
            }
            return sb;

        }

        public Result assertFailure() {
            if (exitCode == 0) {
                throw new AssertionError(appendCommand(
                        new StringBuilder("mvnd returned ").append(exitCode).append(" instead of non-zero exit code: ")));
            }
            return this;
        }

        public Result assertSuccess() {
            if (exitCode != 0) {
                final StringBuilder sb = appendCommand(new StringBuilder("mvnd returned ").append(exitCode));
                if (exitCode == TIMEOUT_EXIT_CODE) {
                    sb.append(" (timeout)");
                }
                sb.append("\n--- stderr+stdout start ---");
                synchronized (log) {
                    log.stream().forEach(s -> sb.append('\n').append(s));
                }
                sb.append("\n--- stderr+stdout end ---");
                throw new AssertionError(sb);
            }
            return this;
        }

        public int getExitCode() {
            return exitCode;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }

    }

    /**
     * A simple wrapper over {@link Process} that manages its destroying and offers Java 8-like
     * {@link #waitFor(long, TimeUnit, String[])} with timeout.
     */
    static class CommandProcess implements AutoCloseable {

        private final Process process;
        private final Thread shutDownHook;
        private final StreamGobbler stdOut;
        private final List<String> args;
        private final List<String> log = new ArrayList<>();

        public CommandProcess(Process process, List<String> args, Consumer<String> outputConsumer) {
            super();
            this.process = process;
            this.args = args;
            final Consumer<String> loggingConsumer = s -> {
                synchronized (log) {
                    log.add(s);
                }
            };
            this.stdOut = new StreamGobbler(process.getInputStream(), loggingConsumer.andThen(outputConsumer));
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

        public ExecutionResult waitFor(long timeoutMs) throws InterruptedException, IOException {
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
            return new Result(args, exitCode, log);
        }

    }

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

}
