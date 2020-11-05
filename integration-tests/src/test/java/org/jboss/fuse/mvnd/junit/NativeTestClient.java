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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jboss.fuse.mvnd.client.Client;
import org.jboss.fuse.mvnd.client.DaemonParameters;
import org.jboss.fuse.mvnd.client.ExecutionResult;
import org.jboss.fuse.mvnd.common.Environment;
import org.jboss.fuse.mvnd.common.OsUtils.CommandProcess;
import org.jboss.fuse.mvnd.common.logging.ClientOutput;

/**
 * A wrapper around the native executable.
 */
public class NativeTestClient implements Client {

    private final DaemonParameters parameters;

    private final Path mvndNativeExecutablePath;

    private final long timeoutMs;

    public NativeTestClient(DaemonParameters parameters, Path mvndNativeExecutablePath, long timeoutMs) {
        super();
        this.parameters = parameters;
        this.mvndNativeExecutablePath = mvndNativeExecutablePath;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public ExecutionResult execute(ClientOutput output, List<String> args) throws InterruptedException {
        final List<String> cmd = new ArrayList<String>(args.size() + 1);
        cmd.add(mvndNativeExecutablePath.toString());
        cmd.addAll(args);
        if (!Environment.MVND_DAEMON_STORAGE.hasCommandLineProperty(args)) {
            Path daemonStorage = parameters.daemonStorage();
            cmd.add(Environment.MVND_DAEMON_STORAGE.asCommandLineProperty(daemonStorage.toString()));
        }
        if (!Environment.MAVEN_REPO_LOCAL.hasCommandLineProperty(args)) {
            Path mavenRepoLocal = parameters.mavenRepoLocal();
            cmd.add(Environment.MAVEN_REPO_LOCAL.asCommandLineProperty(mavenRepoLocal.toString()));
        }
        if (!Environment.MAVEN_SETTINGS.hasCommandLineProperty(args)) {
            final Path settings = parameters.settings();
            if (settings != null) {
                cmd.add(Environment.MAVEN_SETTINGS.asCommandLineProperty(settings.toString()));
            }
        }
        if (!Environment.MVND_THREADS.hasCommandLineProperty(args)) {
            final String threads = parameters.threads();
            cmd.add(Environment.MVND_THREADS.asCommandLineProperty(threads));
        }

        final ProcessBuilder builder = new ProcessBuilder(cmd.toArray(new String[0]))
                .directory(parameters.userDir().toFile()) //
                .redirectErrorStream(true);

        final Map<String, String> env = builder.environment();
        if (!Environment.MVND_HOME.hasCommandLineProperty(args)) {
            env.put("MVND_HOME", System.getProperty("mvnd.home"));
        }
        if (!Environment.JAVA_HOME.hasCommandLineProperty(args)) {
            env.put("JAVA_HOME", System.getProperty("java.home"));
        }
        final String cmdString = String.join(" ", cmd);
        output.accept(null, "Executing " + cmdString);

        final List<String> log = new ArrayList<>();
        final Consumer<String> loggingConsumer = s -> {
            synchronized (log) {
                log.add(s);
            }
        };
        try (CommandProcess process = new CommandProcess(builder.start(),
                loggingConsumer.andThen(s -> output.accept(null, s)))) {
            final int exitCode = process.waitFor(timeoutMs);
            return new Result(args, exitCode, log);
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
                if (exitCode == CommandProcess.TIMEOUT_EXIT_CODE) {
                    sb.append(" (timeout)");
                }
                sb.append("\n--- stderr+stdout start ---");
                synchronized (log) {
                    log.forEach(s -> sb.append('\n').append(s));
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

}
