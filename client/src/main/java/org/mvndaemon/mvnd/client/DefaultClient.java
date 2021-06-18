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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.internal.CLibrary;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.mvndaemon.mvnd.common.BuildProperties;
import org.mvndaemon.mvnd.common.DaemonException;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.common.DaemonRegistry;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.Environment.Color;
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
        final List<String> args = new ArrayList<>(Arrays.asList(argv));

        // Log file
        Path logFile = null;
        String sLogFile = Environment.MAVEN_LOG_FILE.removeCommandLineOption(args);
        if (sLogFile != null) {
            if (sLogFile.isEmpty()) {
                throw new IllegalArgumentException("-l and --log-file need to be followed by a path");
            } else {
                logFile = Paths.get(sLogFile);
            }
        }

        // Serial
        if (Environment.SERIAL.removeCommandLineOption(args) != null) {
            System.setProperty(Environment.SERIAL.getProperty(), Boolean.toString(true));
        }

        // Batch mode
        final boolean batchMode = Environment.MAVEN_BATCH_MODE.hasCommandLineOption(args)
                || Environment.COMPLETION.hasCommandLineOption(args);

        // Color
        Color styleColor = Color.of(Environment.MAVEN_COLOR.removeCommandLineOption(args)).orElse(Color.auto);
        if (styleColor == Color.auto) {
            /* Translate from auto to either always or never */
            /* stdout is not a terminal e.g. when stdout is redirected to a file */
            final boolean stdoutIsTerminal = CLibrary.isatty(1) != 0;
            styleColor = (batchMode || logFile != null || !stdoutIsTerminal) ? Color.never : Color.always;
        }
        /* We cannot use Environment.addCommandLineOption() because that one would pass --color to the daemon
         * and --color is not supported there yet. */
        args.add("-D" + Environment.MAVEN_COLOR.getProperty() + "=" + styleColor.name());

        String userJdkJavaOpts = System.getenv(Environment.JDK_JAVA_OPTIONS.getEnvironmentVariable());
        if (userJdkJavaOpts != null) {
            Environment.JDK_JAVA_OPTIONS.addCommandLineOption(args, userJdkJavaOpts);
        }

        // System properties
        setSystemPropertiesFromCommandLine(args);

        DaemonParameters parameters = new DaemonParameters();
        if (parameters.serial()) {
            System.setProperty(Environment.MVND_THREADS.getProperty(), Integer.toString(1));
            System.setProperty(Environment.MVND_BUILDER.getProperty(), "singlethreaded");
            System.setProperty(Environment.MVND_NO_BUFERING.getProperty(), Boolean.toString(true));
        }

        int exitCode = 0;
        boolean noBuffering = batchMode || parameters.noBuffering();
        try (TerminalOutput output = new TerminalOutput(noBuffering, parameters.rollingWindowSize(), logFile)) {
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

    public static void setSystemPropertiesFromCommandLine(List<String> args) {
        for (String arg : args) {
            String val = Environment.MAVEN_DEFINE.removeCommandLineOption(new ArrayList<>(Collections.singletonList(arg)));
            if (val != null) {
                if (val.isEmpty()) {
                    throw new IllegalArgumentException("Missing argument for option " + arg);
                }
                /* This needs to be done very early, otherwise various DaemonParameters do not work properly */
                final int eqPos = val.indexOf('=');
                if (eqPos >= 0) {
                    System.setProperty(val.substring(0, eqPos), val.substring(eqPos + 1));
                } else {
                    System.setProperty(val, "");
                }
            }
        }
    }

    public DefaultClient(DaemonParameters parameters) {
        // Those options are needed in order to be able to set the environment correctly
        this.parameters = parameters.withJdkJavaOpts(
                " --add-opens java.base/java.io=ALL-UNNAMED"
                        + " --add-opens java.base/java.lang=ALL-UNNAMED"
                        + " --add-opens java.base/java.util=ALL-UNNAMED"
                        + " --add-opens java.base/sun.nio.fs=ALL-UNNAMED");
    }

    @Override
    public ExecutionResult execute(ClientOutput output, List<String> argv) {
        LOGGER.debug("Starting client");

        final List<String> args = new ArrayList<>(argv);
        final String completionShell = Environment.COMPLETION.removeCommandLineOption(args);
        if (completionShell != null) {
            output.accept(Message.log(Completion.getCompletion(completionShell, parameters)));
            return DefaultResult.success(argv);
        }

        boolean version = Environment.MAVEN_VERSION.hasCommandLineOption(args);
        boolean showVersion = Environment.MAVEN_SHOW_VERSION.hasCommandLineOption(args);
        boolean debug = Environment.MAVEN_DEBUG.hasCommandLineOption(args);

        // Print version if needed
        if (version || showVersion || debug) {
            // Print mvnd version
            BuildProperties buildProperties = BuildProperties.getInstance();
            final String mvndVersionString = "mvnd "
                    + (Environment.isNative() ? "native client " : "JVM client ")
                    + buildProperties.getVersion()
                    + "-" + buildProperties.getOsName()
                    + "-" + buildProperties.getOsArch()
                    + " (" + buildProperties.getRevision() + ")";

            boolean isColored = !"never".equals(Environment.MAVEN_COLOR.getCommandLineOption(args));
            final String v = isColored
                    ? mvndVersionString
                    : Ansi.ansi().bold().a(mvndVersionString).reset().toString();
            output.accept(Message.log(v));
            // Print terminal information
            output.describeTerminal();
            /*
             * Do not return, rather pass -v to the server so that the client module does not need to depend on any
             * Maven artifacts
             */
        }

        try (DaemonRegistry registry = new DaemonRegistry(parameters.registry())) {
            if (Environment.STATUS.removeCommandLineOption(args) != null) {
                final String template = "%8s  %7s  %5s  %7s  %5s  %23s  %s";
                output.accept(Message.log(String.format(template,
                        "ID", "PID", "Port", "Status", "RSS", "Last activity", "Java home")));
                for (DaemonInfo d : registry.getAll()) {
                    if (ProcessHandle.of(d.getPid()).isEmpty()) {
                        /* The process does not exist anymore - remove it from the registry */
                        registry.remove(d.getId());
                    } else {
                        output.accept(Message.log(String.format(template,
                                d.getId(), d.getPid(), d.getAddress(), d.getState(),
                                OsUtils.kbTohumanReadable(OsUtils.findProcessRssInKb(d.getPid())),
                                LocalDateTime.ofInstant(
                                        Instant.ofEpochMilli(Math.max(d.getLastIdle(), d.getLastBusy())),
                                        ZoneId.systemDefault()),
                                d.getJavaHome())));
                    }
                }
                return DefaultResult.success(argv);
            }
            if (Environment.STOP.removeCommandLineOption(args) != null) {
                DaemonInfo[] dis = registry.getAll().toArray(new DaemonInfo[0]);
                if (dis.length > 0) {
                    output.accept(Message.display("Stopping " + dis.length + " running daemons"));
                    for (DaemonInfo di : dis) {
                        try {
                            ProcessHandle.of(di.getPid()).ifPresent(ProcessHandle::destroyForcibly);
                        } catch (Exception t) {
                            System.out.println("Daemon " + di.getId() + ": " + t);
                        } finally {
                            registry.remove(di.getId());
                        }
                    }
                }
                return DefaultResult.success(argv);
            }
            if (Environment.PURGE.removeCommandLineOption(args) != null) {
                String result = purgeLogs();
                output.accept(Message.display(result != null ? result : "Nothing to purge"));
                return DefaultResult.success(argv);
            }

            if (!Environment.MVND_THREADS.hasCommandLineOption(args)) {
                Environment.MVND_THREADS.addCommandLineOption(args, parameters.threads());
            }
            if (!Environment.MVND_BUILDER.hasCommandLineOption(args)) {
                Environment.MVND_BUILDER.addCommandLineOption(args, parameters.builder());
            }
            final Path settings = parameters.settings();
            if (settings != null && !Environment.MAVEN_SETTINGS.hasCommandLineOption(args)) {
                Environment.MAVEN_SETTINGS.addCommandLineOption(args, settings.toString());
            }
            final Path localMavenRepository = parameters.mavenRepoLocal();
            if (localMavenRepository != null && !Environment.MAVEN_REPO_LOCAL.hasCommandLineOption(args)) {
                Environment.MAVEN_REPO_LOCAL.addCommandLineOption(args, localMavenRepository.toString());
            }
            Environment.MVND_TERMINAL_WIDTH.addCommandLineOption(args, Integer.toString(output.getTerminalWidth()));

            final DaemonConnector connector = new DaemonConnector(parameters, registry);
            try (DaemonClientConnection daemon = connector.connect(output)) {
                output.setDaemonId(daemon.getDaemon().getId());
                output.setDaemonDispatch(daemon::dispatch);
                output.setDaemonReceive(daemon::enqueue);

                daemon.dispatch(new Message.BuildRequest(
                        args,
                        parameters.userDir().toString(),
                        parameters.multiModuleProjectDirectory().toString(),
                        System.getenv()));

                output.accept(Message
                        .buildStatus("Connected to daemon " + daemon.getDaemon().getId() + ", scanning for projects..."));

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
