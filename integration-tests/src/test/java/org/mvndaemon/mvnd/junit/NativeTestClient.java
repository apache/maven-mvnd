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
package org.mvndaemon.mvnd.junit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.mvndaemon.mvnd.client.Client;
import org.mvndaemon.mvnd.client.DaemonParameters;
import org.mvndaemon.mvnd.client.ExecutionResult;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.common.OsUtils.CommandProcess;
import org.mvndaemon.mvnd.common.logging.ClientOutput;

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
        if (!Environment.MVND_DAEMON_STORAGE.hasCommandOption(args)) {
            Path daemonStorage = parameters.daemonStorage();
            Environment.MVND_DAEMON_STORAGE.appendAsCommandLineOption(cmd::add, daemonStorage.toString());
        }
        if (!Environment.MAVEN_REPO_LOCAL.hasCommandOption(args)) {
            Path mavenRepoLocal = parameters.mavenRepoLocal();
            Environment.MAVEN_REPO_LOCAL.appendAsCommandLineOption(cmd::add, mavenRepoLocal.toString());
        }
        if (!Environment.MAVEN_SETTINGS.hasCommandOption(args)) {
            final Path settings = parameters.settings();
            if (settings != null) {
                Environment.MAVEN_SETTINGS.appendAsCommandLineOption(cmd::add, settings.toString());
            }
        }
        if (!Environment.MVND_THREADS.hasCommandOption(args)) {
            final String threads = parameters.threads();
            Environment.MVND_THREADS.appendAsCommandLineOption(cmd::add, threads);
        }

        final ProcessBuilder builder = new ProcessBuilder(cmd.toArray(new String[0]))
                .directory(parameters.userDir().toFile()) //
                .redirectErrorStream(true);

        final Map<String, String> env = builder.environment();
        if (!Environment.MVND_HOME.hasCommandOption(args)) {
            env.put("MVND_HOME", System.getProperty("mvnd.home"));
        }
        if (!Environment.JAVA_HOME.hasCommandOption(args)) {
            env.put("JAVA_HOME", System.getProperty("java.home"));
        }
        final String cmdString = String.join(" ", cmd);
        output.accept(Message.log("Executing " + cmdString));

        final List<String> log = new ArrayList<>();
        final Consumer<String> loggingConsumer = s -> {
            synchronized (log) {
                log.add(s);
            }
        };
        try (CommandProcess process = new CommandProcess(builder.start(),
                loggingConsumer.andThen(s -> output.accept(Message.log(s))))) {
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

        @Override
        public Result assertFailure() {
            if (exitCode == 0) {
                final StringBuilder sb = ExecutionResult.appendCommand(
                        new StringBuilder("mvnd returned ").append(exitCode).append(" instead of non-zero exit code: "),
                        args);
                sb.append("\n--- stderr+stdout start ---");
                synchronized (log) {
                    log.forEach(s -> sb.append('\n').append(s));
                }
                sb.append("\n--- stderr+stdout end ---");
                throw new AssertionError(sb);
            }
            return this;
        }

        @Override
        public Result assertSuccess() {
            if (exitCode != 0) {
                final StringBuilder sb = ExecutionResult.appendCommand(
                        new StringBuilder("mvnd returned ").append(exitCode),
                        args);
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

        @Override
        public int getExitCode() {
            return exitCode;
        }

        @Override
        public boolean isSuccess() {
            return exitCode == 0;
        }

    }

}
