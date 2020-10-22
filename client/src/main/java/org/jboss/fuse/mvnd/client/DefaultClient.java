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
package org.jboss.fuse.mvnd.client;

import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;
import org.fusesource.jansi.Ansi;
import org.jboss.fuse.mvnd.common.BuildProperties;
import org.jboss.fuse.mvnd.common.DaemonCompatibilitySpec;
import org.jboss.fuse.mvnd.common.DaemonInfo;
import org.jboss.fuse.mvnd.common.DaemonRegistry;
import org.jboss.fuse.mvnd.common.Environment;
import org.jboss.fuse.mvnd.common.Message;
import org.jboss.fuse.mvnd.common.Message.BuildEvent;
import org.jboss.fuse.mvnd.common.Message.BuildException;
import org.jboss.fuse.mvnd.common.Message.BuildMessage;
import org.jboss.fuse.mvnd.common.Message.MessageSerializer;
import org.jboss.fuse.mvnd.common.logging.ClientOutput;
import org.jboss.fuse.mvnd.common.logging.TerminalOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultClient implements Client {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClient.class);
    public static final int DEFAULT_PERIODIC_CHECK_INTERVAL_MILLIS = 10 * 1000;
    public static final int CANCEL_TIMEOUT = 10 * 1000;
    private final Supplier<ClientLayout> lazyLayout;
    private final BuildProperties buildProperties;

    public static void main(String[] argv) throws Exception {
        final List<String> args = new ArrayList<>(argv.length);

        Path logFile = null;
        int i = 0;
        while (i < argv.length) {
            final String arg = argv[i++];
            if ("-l".equals(arg) || "--log-file".equals(arg)) {
                if (i < argv.length) {
                    logFile = Paths.get(argv[i++]);
                } else {
                    throw new IllegalArgumentException("-l and --log-file need to be followed by a path");
                }
            } else {
                args.add(arg);
            }
        }

        try (TerminalOutput output = new TerminalOutput(logFile)) {
            new DefaultClient(ClientLayout::getEnvInstance, BuildProperties.getInstance()).execute(output, args);
        }
    }

    public DefaultClient(Supplier<ClientLayout> layout, BuildProperties buildProperties) {
        this.lazyLayout = layout;
        this.buildProperties = buildProperties;
    }

    @Override
    public ExecutionResult execute(ClientOutput output, List<String> argv) {
        LOGGER.debug("Starting client");

        final List<String> args = new ArrayList<>(argv.size());
        boolean version = false;
        boolean showVersion = false;
        boolean debug = false;
        final Properties commandLineProperties = new Properties();
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
            case "--install":
                throw new IllegalStateException("The --install option was removed in mvnd 0.0.2");
            default:
                if (arg.startsWith("-D")) {
                    final int eqPos = arg.indexOf('=');
                    if (eqPos >= 0) {
                        commandLineProperties.setProperty(arg.substring(2, eqPos), arg.substring(eqPos + 1));
                    } else {
                        commandLineProperties.setProperty(arg.substring(2), "");
                    }
                }
                args.add(arg);
                break;
            }
        }

        // Print version if needed
        if (version || showVersion || debug) {
            final String nativeSuffix = Environment.isNative() ? " (native)" : "";
            final String v = Ansi.ansi().bold().a(
                    "Maven Daemon "
                            + buildProperties.getVersion()
                            + "-" + buildProperties.getOsName()
                            + "-" + buildProperties.getOsArch()
                            + nativeSuffix)
                    .reset().toString();
            output.accept(null, v);
            /*
             * Do not return, rather pass -v to the server so that the client module does not need to depend on any
             * Maven artifacts
             */
        }

        final ClientLayout layout = lazyLayout.get();
        final Path javaHome = layout.javaHome();
        try (DaemonRegistry registry = new DaemonRegistry(layout.registry())) {
            boolean status = args.remove("--status");
            if (status) {
                output.accept(null, String.format("    %36s  %7s  %5s  %7s  %23s  %s",
                        "UUID", "PID", "Port", "Status", "Last activity", "Java home"));
                registry.getAll().forEach(d -> output.accept(null, String.format("    %36s  %7s  %5s  %7s  %23s  %s",
                        d.getUid(), d.getPid(), d.getAddress(), d.getState(),
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(Math.max(d.getLastIdle(), d.getLastBusy())),
                                ZoneId.systemDefault()),
                        d.getJavaHome())));
                return new DefaultResult(argv, null);
            }
            boolean stop = args.remove("--stop");
            if (stop) {
                DaemonInfo[] dis = registry.getAll().toArray(new DaemonInfo[0]);
                if (dis.length > 0) {
                    output.accept(null, "Stopping " + dis.length + " running daemons");
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
                return new DefaultResult(argv, null);
            }

            setDefaultArgs(args);
            final Path settings = layout.getSettings();
            if (settings != null && args.stream().noneMatch(arg -> arg.equals("-s") || arg.equals("--settings"))) {
                args.add("-s");
                args.add(settings.toString());
            }

            final Path localMavenRepository = layout.getLocalMavenRepository();
            if (localMavenRepository != null) {
                args.add("-Dmaven.repo.local=" + localMavenRepository.toString());
            }

            final DaemonConnector connector = new DaemonConnector(layout, registry, buildProperties, new MessageSerializer());
            List<String> opts = new ArrayList<>();
            DaemonClientConnection daemon = connector.connect(new DaemonCompatibilitySpec(javaHome, opts),
                    s -> output.accept(null, s));

            daemon.dispatch(new Message.BuildRequest(
                    args,
                    layout.userDir().toString(),
                    layout.multiModuleProjectDirectory().toString()));

            while (true) {
                Message m = daemon.receive();
                if (m instanceof BuildException) {
                    final BuildException e = (BuildException) m;
                    output.error(e.getMessage(), e.getClassName(), e.getStackTrace());
                    return new DefaultResult(argv,
                            new Exception(e.getClassName() + ": " + e.getMessage() + "\n" + e.getStackTrace()));
                } else if (m instanceof BuildEvent) {
                    BuildEvent be = (BuildEvent) m;
                    switch (be.getType()) {
                    case BuildStarted:
                        int projects = 0;
                        int cores = 0;
                        Properties props = new Properties();
                        try {
                            props.load(new StringReader(be.getDisplay()));
                            projects = Integer.parseInt(props.getProperty("projects"));
                            cores = Integer.parseInt(props.getProperty("cores"));
                        } catch (Exception e) {
                            // Ignore
                        }
                        output.startBuild(be.getProjectId(), projects, cores);
                        break;
                    case BuildStopped:
                        return new DefaultResult(argv, null);
                    case ProjectStarted:
                    case MojoStarted:
                        output.projectStateChanged(be.getProjectId(), be.getDisplay());
                        break;
                    case ProjectStopped:
                        output.projectFinished(be.getProjectId());
                        break;
                    }
                } else if (m instanceof BuildMessage) {
                    BuildMessage bm = (BuildMessage) m;
                    output.accept(bm.getProjectId(), bm.getMessage());
                }
            }
        }

    }

    static void setDefaultArgs(List<String> args) {
        if (args.stream().noneMatch(arg -> arg.startsWith("-T") || arg.equals("--threads"))) {
            final int procs = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
            args.add("-T" + procs);
        }
        if (args.stream().noneMatch(arg -> arg.startsWith("-b") || arg.equals("--builder"))) {
            args.add("-bsmart");
        }
    }

    private static class DefaultResult implements ExecutionResult {

        private final Exception exception;
        private final List<String> args;

        private DefaultResult(List<String> args, Exception exception) {
            super();
            this.args = args;
            this.exception = exception;
        }

        @Override
        public ExecutionResult assertSuccess() {
            if (exception != null) {
                throw new AssertionError(appendCommand(new StringBuilder("Build failed: ")).toString(), exception);
            }
            return this;
        }

        @Override
        public ExecutionResult assertFailure() {
            if (exception == null) {
                throw new AssertionError(appendCommand(new StringBuilder("Build did not fail: ")));
            }
            return this;
        }

        @Override
        public boolean isSuccess() {
            return exception == null;
        }

        StringBuilder appendCommand(StringBuilder sb) {
            sb.append("mvnd");
            for (String arg : args) {
                sb.append(" \"").append(arg).append('"');
            }
            return sb;
        }
    }

}
