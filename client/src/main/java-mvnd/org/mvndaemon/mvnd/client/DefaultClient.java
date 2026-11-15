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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jline.terminal.spi.SystemStream;
import org.jline.terminal.spi.TerminalExt;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.mvndaemon.mvnd.common.DaemonException;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.common.DaemonRegistry;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.Environment.Color;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.common.Message.BuildException;
import org.mvndaemon.mvnd.common.Message.BuildFinished;
import org.mvndaemon.mvnd.common.OsUtils;
import org.mvndaemon.mvnd.common.logging.ClientOutput;
import org.mvndaemon.mvnd.common.logging.TerminalOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.impl.MvndLoggerFactory;

public class DefaultClient implements Client {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClient.class);

    private final DaemonParameters parameters;
    private final List<String> userArgs;

    public static void main(String[] argv) throws Exception {
        final List<String> args = new ArrayList<>(Arrays.asList(argv));
        ClientArguments.removeBlankArguments(args);
        // What the user typed, before mvnd adds or removes options; MAVEN_CMD_LINE_ARGS is built from it
        final List<String> userArgs = new ArrayList<>(args);

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

        // Diag
        if (Environment.DIAG.removeCommandLineOption(args) != null) {
            org.jline.terminal.impl.Diag.diag(System.out);
            return;
        }

        // Serial
        if (Environment.SERIAL.removeCommandLineOption(args) != null) {
            System.setProperty(Environment.SERIAL.getProperty(), Boolean.toString(true));
        }

        // Batch mode
        final boolean batchMode = Environment.MAVEN_BATCH_MODE.hasCommandLineOption(args)
                || Environment.COMPLETION.hasCommandLineOption(args);

        String userJdkJavaOpts = System.getenv(Environment.JDK_JAVA_OPTIONS.getEnvironmentVariable());
        if (userJdkJavaOpts != null) {
            Environment.JDK_JAVA_OPTIONS.addCommandLineOption(args, userJdkJavaOpts);
        }

        // System properties
        setSystemPropertiesFromCommandLine(args);

        if (LoggerFactory.getILoggerFactory() instanceof MvndLoggerFactory) {
            ((MvndLoggerFactory) LoggerFactory.getILoggerFactory()).reconfigure();
        }

        DaemonParameters parameters = new DaemonParameters();
        if (parameters.serial()) {
            System.setProperty(Environment.MVND_THREADS.getProperty(), Integer.toString(1));
            System.setProperty(Environment.MVND_BUILDER.getProperty(), "singlethreaded");
            System.setProperty(Environment.MVND_NO_BUFERING.getProperty(), Boolean.toString(true));
        }

        System.setProperty(
                Environment.MVND_HOME.getProperty(), parameters.mvndHome().toString());

        Path dir;
        if (Environment.MAVEN_FILE.hasCommandLineOption(args)) {
            dir = parameters.userDir().resolve(Environment.MAVEN_FILE.getCommandLineOption(args));
            if (Files.isRegularFile(dir)) {
                dir = dir.getParent();
            }
            dir = dir.normalize();
        } else {
            dir = parameters.userDir();
        }
        Path multiModuleProjectDirectory = parameters.multiModuleProjectDirectory(dir);
        System.setProperty(
                Environment.MAVEN_MULTIMODULE_PROJECT_DIRECTORY.getProperty(), multiModuleProjectDirectory.toString());
        Environment.MAVEN_MULTIMODULE_PROJECT_DIRECTORY.addCommandLineOption(
                args, multiModuleProjectDirectory.toString());

        // .mvn/jvm.config
        if (Files.isRegularFile(parameters.jvmConfigPath())) {
            try (Stream<String> jvmArgs = Files.lines(parameters.jvmConfigPath())) {
                String jvmArgsStr = jvmArgs.collect(Collectors.joining(" "));
                parameters = parameters.withJvmArgs(jvmArgsStr, false);
            }
        }

        // Install JUL -> SLF4j bridge
        SLF4JBridgeHandler.install();

        int exitCode = 0;
        boolean noBuffering = batchMode || parameters.noBuffering();
        try (TerminalOutput output = new TerminalOutput(
                noBuffering, parameters.hideBannedProjectSkips(), parameters.rollingWindowSize(), logFile)) {
            try {
                // Color
                // We need to defer this part until the terminal is created
                Color styleColor = Color.of(Environment.MAVEN_COLOR.removeCommandLineOption(args))
                        .orElse(Color.auto);
                if (styleColor == Color.auto) {
                    /* Translate from auto to either always or never */
                    /* stdout is not a terminal e.g. when stdout is redirected to a file */
                    final boolean stdoutIsTerminal =
                            ((TerminalExt) output.getTerminal()).getProvider().isSystemStream(SystemStream.Output);
                    styleColor = (batchMode || logFile != null || !stdoutIsTerminal) ? Color.never : Color.always;
                }
                /* We cannot use Environment.addCommandLineOption() because that one would pass --color to the daemon
                 * and --color is not supported there yet. */
                args.add("-D" + Environment.MAVEN_COLOR.getProperty() + "=" + styleColor.name());

                final ExecutionResult result = new DefaultClient(parameters, userArgs).execute(output, args);
                exitCode = result.getExitCode();
            } catch (DaemonException.InterruptedException e) {
                final AttributedStyle s = new AttributedStyle().bold().foreground(AttributedStyle.RED);
                String str = new AttributedString(System.lineSeparator() + "Canceled by user", s).toAnsi();
                output.accept(Message.err(str));
                exitCode = 130;
            }
        }
        System.exit(exitCode);
    }

    public static Map<String, String> setSystemPropertiesFromCommandLine(List<String> args) {
        final HashMap<String, String> prevState = new HashMap<>();
        final Iterator<String> iterator = args.iterator();
        boolean defineIsEmpty = false;
        while (iterator.hasNext()) {
            final String arg = iterator.next();
            String val =
                    Environment.MAVEN_DEFINE.removeCommandLineOption(new ArrayList<>(Collections.singletonList(arg)));
            /* not -D or --define and pre define is empty */
            if (val == null && defineIsEmpty) {
                defineIsEmpty = false;
                /* not all of Environment, use arg as pre define value */
                val = maybeDefineCommandLineOption(arg) ? arg : "";
            }
            if (val != null) {
                /* empty -D or --define, next arg is value */
                if (val.isEmpty() && iterator.hasNext()) {
                    defineIsEmpty = true;
                    continue;
                }
                if (val.isEmpty()) {
                    throw new IllegalArgumentException("Missing argument for option " + arg);
                }
                /* This needs to be done very early, otherwise various DaemonParameters do not work properly */
                final int eqPos = val.indexOf('=');
                if (eqPos >= 0) {
                    String k = val.substring(0, eqPos);
                    String v = val.substring(eqPos + 1);
                    String old = System.setProperty(k, v);
                    prevState.put(k, old);
                    LOGGER.trace("Setting system property {} to {}", k, v);
                } else {
                    String old = System.setProperty(val, "");
                    prevState.put(val, old);
                    LOGGER.trace("Setting system property {}", val);
                }
            }
        }
        return prevState;
    }

    private static boolean maybeDefineCommandLineOption(String arg) {
        // if arg maybe MAVEN_DEFINE value
        return EnumSet.allOf(Environment.class).stream()
                .filter(e -> e != Environment.MAVEN_DEFINE)
                .noneMatch(e -> e.hasCommandLineOption(Collections.singletonList(arg)));
    }

    public DefaultClient(DaemonParameters parameters) {
        this(parameters, null);
    }

    DefaultClient(DaemonParameters parameters, List<String> userArgs) {
        this.userArgs = userArgs;
        // Those options are needed in order to be able to set the environment correctly
        this.parameters = parameters.withJdkJavaOpts(
                "--add-opens java.base/java.io=ALL-UNNAMED "
                        + "--add-opens java.base/java.lang=ALL-UNNAMED "
                        + "--add-opens java.base/java.util=ALL-UNNAMED "
                        + "--add-opens java.base/sun.net.www.protocol.jar=ALL-UNNAMED "
                        + "--add-opens java.base/sun.nio.fs=ALL-UNNAMED",
                true);
    }

    /**
     * The environment forwarded to the daemon in the {@link Message.BuildRequest}. Defaults to the
     * client's own environment; overridable so tests can run hermetically (e.g. without inheriting
     * an ambient {@code MAVEN_ARGS}).
     */
    protected Map<String, String> buildRequestEnvironment() {
        return System.getenv();
    }

    @Override
    public ExecutionResult execute(ClientOutput output, List<String> argv) {
        LOGGER.debug("Starting client");

        final List<String> args = new ArrayList<>(argv);
        final String completionShell = Environment.COMPLETION.removeCommandLineOption(args);
        if (completionShell != null) {
            output.accept(Message.out(Completion.getCompletion(completionShell, parameters)));
            return DefaultResult.success(argv);
        }

        boolean version = Environment.MAVEN_VERSION.hasCommandLineOption(args);
        boolean showVersion = Environment.MAVEN_SHOW_VERSION.hasCommandLineOption(args);
        boolean debug = Environment.MAVEN_DEBUG.hasCommandLineOption(args);

        // Print version if needed
        if (version || showVersion || debug) {
            // Print mvnd version
            BuildProperties buildProperties = BuildProperties.getInstance();
            final String mvndVersionString = "Apache Maven Daemon (mvnd) " + buildProperties.getVersion() + " "
                    + (Environment.isNative()
                            ? buildProperties.getOsName() + "-" + buildProperties.getOsArch() + " native client"
                            : "JVM client")
                    + " (" + buildProperties.getRevision() + ")";

            boolean isColored = !"never".equals(Environment.MAVEN_COLOR.getCommandLineOption(args));
            final String v = isColored
                    ? new AttributedStringBuilder()
                            .style(AttributedStyle.BOLD)
                            .append(mvndVersionString)
                            .toAnsi()
                    : mvndVersionString;
            output.accept(Message.log(v));
            // Print terminal information
            output.describeTerminal();
            if (VersionInfo.isPlainVersionRequest(args)) {
                /*
                 * A plain -v/--version makes Maven print its own version and return without running a build, so
                 * a daemon round trip cannot change the answer. Print the same lines client-side instead of
                 * connecting to (and possibly spawning) a daemon, still without the client module depending on
                 * any Maven artifact. With -q, --help or an mvnd command such as --status the request continues
                 * to the daemon or to the handling below.
                 */
                Path javaHome;
                try {
                    javaHome = parameters.javaHome();
                } catch (RuntimeException e) {
                    javaHome = null; // no JDK found: report the client JVM instead
                }
                for (String line : VersionInfo.lines(
                        parameters.mvndHome().resolve("mvn").resolve("lib"),
                        buildProperties.getMavenVersion(),
                        parameters.mvndHome().resolve("mvn"),
                        javaHome,
                        System.getProperties())) {
                    output.accept(Message.log(line));
                }
                return DefaultResult.success(argv);
            }
            /*
             * Do not return, rather pass -v/-V/-X to the server so that the client module does not need to depend
             * on any Maven artifacts
             */
        }

        try (DaemonRegistry registry = new DaemonRegistry(parameters.registry())) {
            if (Environment.STATUS.removeCommandLineOption(args) != null) {
                final String template = "%8s  %7s  %24s  %7s  %5s  %23s  %s";
                output.accept(Message.out(String.format(
                        template, "ID", "PID", "Address", "Status", "RSS", "Last activity", "Java home")));
                for (DaemonInfo d : registry.getAll()) {
                    if (ProcessHandle.of(d.getPid()).isEmpty()) {
                        /* The process does not exist anymore - remove it from the registry */
                        registry.remove(d.getId());
                    } else {
                        output.accept(Message.out(String.format(
                                template,
                                d.getId(),
                                d.getPid(),
                                d.getAddress(),
                                d.getState(),
                                OsUtils.kbToHumanReadable(OsUtils.findProcessRssInKb(d.getPid())),
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
                    output.accept(Message.out("Stopping " + dis.length + " running daemons"));
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
                String result = purgeLogs(activeDaemons(registry));
                output.accept(Message.out(result != null ? result : "Nothing to purge"));
                return DefaultResult.success(argv);
            }

            // Raw streams
            if (Environment.MVND_RAW_STREAMS.removeCommandLineOption(args) != null) {
                args.add("-D" + Environment.MVND_RAW_STREAMS.getProperty());
            }

            // Serial mode forces one thread and the singlethreaded builder, also over .mvn/maven.config
            List<String> mavenConfig = parameters.serial()
                    ? Collections.emptyList()
                    : DaemonParameters.readMavenConfig(parameters.mavenConfigPath());
            ClientArguments.addDefaultOption(Environment.MVND_THREADS, args, mavenConfig, parameters::threads);
            ClientArguments.addDefaultOption(Environment.MVND_BUILDER, args, mavenConfig, parameters::builder);

            Optional<String> settings = Optional.ofNullable(Environment.MAVEN_SETTINGS.removeCommandLineOption(args))
                    .or(() -> Optional.ofNullable(parameters.settings()).map(Path::toString));
            settings.ifPresent(s -> Environment.MAVEN_SETTINGS.addCommandLineOption(args, s));

            Optional<String> repo = Optional.ofNullable(Environment.MAVEN_REPO_LOCAL.removeCommandLineOption(args))
                    .or(() -> Optional.ofNullable(parameters.mavenRepoLocal()).map(Path::toString));
            repo.ifPresent(r -> Environment.MAVEN_REPO_LOCAL.addCommandLineOption(args, r));

            String width = Optional.ofNullable(Environment.MVND_TERMINAL_WIDTH.removeCommandLineOption(args))
                    .orElseGet(() -> {
                        int w = output.getTerminalWidth();
                        return Integer.toString(w > 0 ? Math.max(w, 80) : 120);
                    });
            Environment.MVND_TERMINAL_WIDTH.addCommandLineOption(args, width);

            if (parameters.property(Environment.MVND_DEBUG).asBoolean()) {
                if (Environment.MVND_KEEP_ALIVE.getCommandLineOption(args) == null) {
                    System.setProperty(Environment.MVND_KEEP_ALIVE.getProperty(), "1h");
                }
                if (Environment.MVND_CONNECT_TIMEOUT.getCommandLineOption(args) == null) {
                    System.setProperty(Environment.MVND_CONNECT_TIMEOUT.getProperty(), "1h");
                }
            }

            final DaemonConnector connector = new DaemonConnector(parameters, registry);
            try (DaemonClientConnection daemon = connector.connect(output)) {
                output.setDaemonId(daemon.getDaemon().getId());
                output.setDaemonDispatch(daemon::dispatch);
                output.setDaemonReceive(daemon::enqueue);

                daemon.dispatch(new Message.BuildRequest(
                        args,
                        parameters.userDir().toString(),
                        parameters.multiModuleProjectDirectory().toString(),
                        MavenCmdLineArgs.addTo(buildRequestEnvironment(), userArgs != null ? userArgs : argv)));

                output.accept(Message.buildStatus(
                        "Connected to daemon " + daemon.getDaemon().getId() + ", scanning for projects..."));

                // We've sent the request, so it gives us a bit of time to purge the logs
                AtomicReference<String> purgeMessage = new AtomicReference<>();
                Set<String> activeDaemons = activeDaemons(registry);
                Thread purgeLog = new Thread(
                        () -> {
                            purgeMessage.set(purgeLogs(activeDaemons));
                        },
                        "Log purge");
                purgeLog.setDaemon(true);
                purgeLog.start();

                try {
                    while (true) {
                        final List<Message> messages = daemon.receive();
                        output.accept(messages);
                        for (Message m : messages) {
                            switch (m.getType()) {
                                case Message.CANCEL_BUILD:
                                    return new DefaultResult(
                                            argv, new InterruptedException("The build was canceled"), 130);
                                case Message.BUILD_EXCEPTION:
                                    final BuildException e = (BuildException) m;
                                    return new DefaultResult(
                                            argv,
                                            new Exception(e.getClassName() + ": " + e.getMessage() + "\n"
                                                    + e.getStackTrace()),
                                            1);
                                case Message.BUILD_FINISHED:
                                    return new DefaultResult(argv, null, ((BuildFinished) m).getExitCode());
                            }
                        }
                    }
                } finally {
                    String msg = purgeMessage.get();
                    if (msg != null) {
                        output.accept(Message.err(msg));
                    }
                }
            }
        }
    }

    private String purgeLogs(Set<String> activeDaemons) {
        return LogPurger.purge(parameters.daemonStorage(), parameters.purgeLogPeriod(), activeDaemons);
    }

    private static Set<String> activeDaemons(DaemonRegistry registry) {
        return registry.getAll().stream().map(DaemonInfo::getId).collect(Collectors.toSet());
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
                throw new AssertionError(
                        ExecutionResult.appendCommand(new StringBuilder("Build failed: "), args)
                                .toString(),
                        exception);
            }
            if (exitCode != 0) {
                throw new AssertionError(
                        ExecutionResult.appendCommand(
                                        new StringBuilder("Build exited with non-zero exit code " + exitCode + ": "),
                                        args)
                                .toString(),
                        exception);
            }
            return this;
        }

        @Override
        public ExecutionResult assertFailure() {
            if (exception == null && exitCode == 0) {
                throw new AssertionError(
                        ExecutionResult.appendCommand(new StringBuilder("Build did not fail: "), args));
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
