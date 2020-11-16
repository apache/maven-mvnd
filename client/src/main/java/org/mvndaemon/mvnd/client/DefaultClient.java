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
package org.mvndaemon.mvnd.client;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.fusesource.jansi.Ansi;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.mvndaemon.mvnd.common.BuildProperties;
import org.mvndaemon.mvnd.common.DaemonException;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.common.DaemonRegistry;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.common.Message.BuildException;
import org.mvndaemon.mvnd.common.Message.BuildFinished;
import org.mvndaemon.mvnd.common.OsUtils;
import org.mvndaemon.mvnd.common.TimeUtils;
import org.mvndaemon.mvnd.common.logging.ClientOutput;
import org.mvndaemon.mvnd.common.logging.TerminalOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mvndaemon.mvnd.client.DaemonParameters.LOG_EXTENSION;

public class DefaultClient implements Client {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClient.class);

    private final DaemonParameters parameters;

    public static void main(String[] argv) throws Exception {
        final List<String> args = new ArrayList<>(argv.length);

        Path logFile = null;
        int i = 0;
        boolean batchMode = false;
        while (i < argv.length) {
            final String arg = argv[i++];
            if ("-l".equals(arg) || "--log-file".equals(arg)) {
                if (i < argv.length) {
                    logFile = Paths.get(argv[i++]);
                } else {
                    throw new IllegalArgumentException("-l and --log-file need to be followed by a path");
                }
            } else {
                if (!batchMode && ("-B".equals(arg) || "--batch-mode".equals(arg))) {
                    batchMode = true;
                }
                args.add(arg);
            }
        }

        DaemonParameters parameters = new DaemonParameters();
        int exitCode = 0;
        try (TerminalOutput output = new TerminalOutput(batchMode || parameters.noBuffering(), parameters.rollingWindowSize(),
                logFile)) {
            try {
                final ExecutionResult result = new DefaultClient(parameters).execute(output, args);
                exitCode = result.getExitCode();
            } catch (DaemonException.InterruptedException e) {
                final AttributedStyle s = new AttributedStyle().bold().foreground(AttributedStyle.RED);
                String str = new AttributedString(System.lineSeparator() + "Canceled by user", s).toAnsi();
                output.accept(Message.display(str));
                exitCode = 130;
            }
        }
        System.exit(exitCode);
    }

    public DefaultClient(DaemonParameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public ExecutionResult execute(ClientOutput output, List<String> argv) {
        LOGGER.debug("Starting client");

        final List<String> args = new ArrayList<>(argv.size());
        boolean version = false;
        boolean showVersion = false;
        boolean debug = false;
        for (String arg : argv) {
            switch (arg) {
            case "-v":
            case "-version":
            case "--version":
                version = true;
                args.add(arg);
                break;
            case "-V":
            case "--show-version":
                showVersion = true;
                args.add(arg);
                break;
            case "-X":
            case "--debug":
                debug = true;
                args.add(arg);
                break;
            default:
                if (arg.startsWith("-D")) {
                    final int eqPos = arg.indexOf('=');
                    if (eqPos >= 0) {
                        System.setProperty(arg.substring(2, eqPos), arg.substring(eqPos + 1));
                    } else {
                        System.setProperty(arg.substring(2), "");
                    }
                }
                args.add(arg);
                break;
            }
        }

        // Print version if needed
        if (version || showVersion || debug) {
            // Print mvnd version
            BuildProperties buildProperties = BuildProperties.getInstance();
            final String nativeSuffix = Environment.isNative() ? " (native)" : "";
            final String v = Ansi.ansi().bold().a(
                    "Maven Daemon "
                            + buildProperties.getVersion()
                            + "-" + buildProperties.getOsName()
                            + "-" + buildProperties.getOsArch()
                            + nativeSuffix)
                    .reset().toString();
            output.accept(Message.log(v));
            // Print terminal information
            output.describeTerminal();
            /*
             * Do not return, rather pass -v to the server so that the client module does not need to depend on any
             * Maven artifacts
             */
        }

        try (DaemonRegistry registry = new DaemonRegistry(parameters.registry())) {
            boolean status = args.remove("--status");
            if (status) {
                final String template = "    %36s  %7s  %5s  %7s  %5s  %23s  %s";
                output.accept(Message.log(String.format(template,
                        "UUID", "PID", "Port", "Status", "RSS", "Last activity", "Java home")));
                for (DaemonInfo d : registry.getAll()) {
                    if (ProcessHandle.of(d.getPid()).isEmpty()) {
                        /* The process does not exist anymore - remove it from the registry */
                        registry.remove(d.getUid());
                    } else {
                        output.accept(Message.log(String.format(template,
                                d.getUid(), d.getPid(), d.getAddress(), d.getState(),
                                OsUtils.kbTohumanReadable(OsUtils.findProcessRssInKb(d.getPid())),
                                LocalDateTime.ofInstant(
                                        Instant.ofEpochMilli(Math.max(d.getLastIdle(), d.getLastBusy())),
                                        ZoneId.systemDefault()),
                                d.getJavaHome())));
                    }
                }
                return DefaultResult.success(argv);
            }
            boolean stop = args.remove("--stop");
            if (stop) {
                DaemonInfo[] dis = registry.getAll().toArray(new DaemonInfo[0]);
                if (dis.length > 0) {
                    output.accept(Message.display("Stopping " + dis.length + " running daemons"));
                    for (DaemonInfo di : dis) {
                        try {
                            ProcessHandle.of(di.getPid()).ifPresent(ProcessHandle::destroyForcibly);
                        } catch (Exception t) {
                            System.out.println("Daemon " + di.getUid() + ": " + t);
                        } finally {
                            registry.remove(di.getUid());
                        }
                    }
                }
                return DefaultResult.success(argv);
            }
            boolean purge = args.remove("--purge");
            if (purge) {
                String result = purgeLogs();
                output.accept(Message.display(result != null ? result : "Nothing to purge"));
                return DefaultResult.success(argv);
            }

            if (args.stream().noneMatch(arg -> arg.startsWith("-T") || arg.equals("--threads"))) {
                args.add("--threads");
                args.add(parameters.threads());
            }
            if (args.stream().noneMatch(arg -> arg.startsWith("-b") || arg.equals("--builder"))) {
                args.add("--builder");
                args.add(parameters.builder());
            }
            final Path settings = parameters.settings();
            if (settings != null && args.stream().noneMatch(arg -> arg.equals("-s") || arg.equals("--settings"))) {
                args.add("--settings");
                args.add(settings.toString());
            }
            final Path localMavenRepository = parameters.mavenRepoLocal();
            if (localMavenRepository != null && args.stream().noneMatch(arg -> arg.startsWith("-Dmaven.repo.local="))) {
                args.add("-Dmaven.repo.local=" + localMavenRepository.toString());
            }

            final DaemonConnector connector = new DaemonConnector(parameters, registry);
            try (DaemonClientConnection daemon = connector.connect(output)) {
                output.setDaemonDispatch(daemon::dispatch);
                output.setDaemonReceive(daemon::enqueue);
                output.accept(Message.buildStatus("Connected to daemon"));

                daemon.dispatch(new Message.BuildRequest(
                        args,
                        parameters.userDir().toString(),
                        parameters.multiModuleProjectDirectory().toString(),
                        System.getenv()));

                output.accept(Message.buildStatus("Build request sent"));

                // We've sent the request, so it gives us a bit of time to purge the logs
                AtomicReference<String> purgeMessage = new AtomicReference<>();
                Thread purgeLog = new Thread(() -> {
                    purgeMessage.set(purgeLogs());
                }, "Log purge");
                purgeLog.setDaemon(true);
                purgeLog.start();

                try {
                    while (true) {
                        final List<Message> messages = daemon.receive();
                        output.accept(messages);
                        for (Message m : messages) {
                            switch (m.getType()) {
                            case Message.CANCEL_BUILD:
                                return new DefaultResult(argv,
                                        new InterruptedException("The build was canceled"), 130);
                            case Message.BUILD_EXCEPTION:
                                final BuildException e = (BuildException) m;
                                return new DefaultResult(argv,
                                        new Exception(e.getClassName() + ": " + e.getMessage() + "\n" + e.getStackTrace()),
                                        1);
                            case Message.BUILD_FINISHED:
                                return new DefaultResult(argv, null, ((BuildFinished) m).getExitCode());
                            }
                        }
                    }
                } finally {
                    String msg = purgeMessage.get();
                    if (msg != null) {
                        output.accept(Message.display(msg));
                    }
                }
            }
        }
    }

    private String purgeLogs() {
        Path storage = parameters.daemonStorage();
        Duration purgeLogPeriod = parameters.purgeLogPeriod();
        if (!Files.isDirectory(storage) || !TimeUtils.isPositive(purgeLogPeriod)) {
            return null;
        }
        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault()).format(Instant.now());
        Path log = storage.resolve("purge-" + date + ".log");
        List<Path> deleted = new ArrayList<>();
        List<Throwable> exceptions = new ArrayList<>();
        FileTime limit = FileTime.from(Instant.now().minus(purgeLogPeriod));
        try {
            Files.list(storage)
                    .filter(p -> p.getFileName().toString().endsWith(LOG_EXTENSION))
                    .filter(p -> !log.equals(p))
                    .filter(p -> {
                        try {
                            FileTime lmt = Files.getLastModifiedTime(p);
                            return lmt.compareTo(limit) < 0;
                        } catch (IOException e) {
                            exceptions.add(e);
                            return false;
                        }
                    })
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                            deleted.add(p);
                        } catch (IOException e) {
                            exceptions.add(e);
                        }
                    });
        } catch (Exception e) {
            exceptions.add(e);
        }
        if (exceptions.isEmpty() && deleted.isEmpty()) {
            return null;
        }
        String logMessage;
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(log,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE))) {
            w.printf("Purge executed at %s%n", Instant.now().toString());
            if (deleted.isEmpty()) {
                w.printf("No files deleted.%n");
            } else {
                w.printf("Deleted files:%n");
                for (Path p : deleted) {
                    w.printf("    %s%n", p.toString());
                }
            }
            if (!exceptions.isEmpty()) {
                w.printf("%d exception(s) occurred during the purge", exceptions.size());
                for (Throwable t : exceptions) {
                    t.printStackTrace(w);
                }
            }
            char[] buf = new char[80];
            Arrays.fill(buf, '=');
            w.printf("%s%n", new String(buf));
            logMessage = "log available in " + log.toString();
        } catch (IOException e) {
            logMessage = "an exception occurred when writing log to " + log.toString() + ": " + e.toString();
        }
        if (exceptions.isEmpty()) {
            return String.format("Purged %d log files (%s)", deleted.size(), logMessage);
        } else {
            return String.format("Purged %d log files with %d exceptions (%s)", deleted.size(), exceptions.size(), logMessage);
        }
    }

    private static class DefaultResult implements ExecutionResult {

        private final Exception exception;
        private final List<String> args;
        private final int exitCode;

        public static DefaultResult success(List<String> args) {
            return new DefaultResult(args, null, 0);
        }

        private DefaultResult(List<String> args, Exception exception, int exitCode) {
            super();
            this.args = args;
            this.exception = exception;
            this.exitCode = exitCode;
        }

        @Override
        public ExecutionResult assertSuccess() {
            if (exception != null) {
                throw new AssertionError(ExecutionResult.appendCommand(new StringBuilder("Build failed: "), args).toString(),
                        exception);
            }
            if (exitCode != 0) {
                throw new AssertionError(
                        ExecutionResult.appendCommand(
                                new StringBuilder("Build exited with non-zero exit code " + exitCode + ": "), args).toString(),
                        exception);
            }
            return this;
        }

        @Override
        public ExecutionResult assertFailure() {
            if (exception == null && exitCode == 0) {
                throw new AssertionError(ExecutionResult.appendCommand(new StringBuilder("Build did not fail: "), args));
            }
            return this;
        }

        @Override
        public int getExitCode() {
            return exitCode;
        }

        @Override
        public boolean isSuccess() {
            return exception == null;
        }

    }

}
