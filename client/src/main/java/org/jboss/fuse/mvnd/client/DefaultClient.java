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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.fusesource.jansi.Ansi;
import org.jboss.fuse.mvnd.client.ClientOutput.TerminalOutput;
import org.jboss.fuse.mvnd.client.Message.BuildEvent;
import org.jboss.fuse.mvnd.client.Message.BuildException;
import org.jboss.fuse.mvnd.client.Message.BuildMessage;
import org.jboss.fuse.mvnd.client.Message.MessageSerializer;
import org.jboss.fuse.mvnd.jpm.ProcessImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultClient implements Client {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClient.class);
    public static final int DEFAULT_IDLE_TIMEOUT = (int) TimeUnit.HOURS.toMillis(3);
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
                    throw new IllegalArgumentException("-l and --log-file need to befollowed by a path");
                }
            } else {
                args.add(arg);
            }
        }

        try (TerminalOutput output = new TerminalOutput(logFile)) {
            new DefaultClient(() -> ClientLayout.getEnvInstance(), BuildProperties.getInstance()).execute(output, args);
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
            final String v = Ansi.ansi().bold().a("Maven Daemon " + buildProperties.getVersion() + nativeSuffix)
                    .reset().toString();
            output.accept(v);
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
                output.accept(String.format("    %36s  %7s  %5s  %7s  %s",
                        "UUID", "PID", "Port", "Status", "Last activity"));
                registry.getAll().forEach(d -> output.accept(String.format("    %36s  %7s  %5s  %7s  %s",
                        d.getUid(), d.getPid(), d.getAddress(), d.getState(),
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(Math.max(d.getLastIdle(), d.getLastBusy())),
                                ZoneId.systemDefault()))));
                return new DefaultResult(argv, null);
            }
            boolean stop = args.remove("--stop");
            if (stop) {
                DaemonInfo[] dis = registry.getAll().toArray(new DaemonInfo[0]);
                if (dis.length > 0) {
                    output.accept("Stopping " + dis.length + " running daemons");
                    for (DaemonInfo di : dis) {
                        try {
                            new ProcessImpl(di.getPid()).destroy();
                        } catch (IOException t) {
                            System.out.println("Daemon " + di.getUid() + ": " + t.getMessage());
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
            if (settings != null && !args.stream().anyMatch(arg -> arg.equals("-s") || arg.equals("--settings"))) {
                args.add("-s");
                args.add(settings.toString());
            }

            final Path localMavenRepository = layout.getLocalMavenRepository();
            if (localMavenRepository != null) {
                args.add("-Dmaven.repo.local=" + localMavenRepository.toString());
            }

            final DaemonConnector connector = new DaemonConnector(layout, registry, buildProperties, new MessageSerializer());
            List<String> opts = new ArrayList<>();
            DaemonClientConnection daemon = connector.connect(new DaemonCompatibilitySpec(javaHome, opts));

            daemon.dispatch(new Message.BuildRequest(
                    args,
                    layout.userDir().toString(),
                    layout.multiModuleProjectDirectory().toString()));

            while (true) {
                Message m = daemon.receive();
                if (m instanceof BuildException) {
                    final BuildException e = (BuildException) m;
                    output.error(e);
                    return new DefaultResult(argv,
                            new Exception(e.getClassName() + ": " + e.getMessage() + "\n" + e.getStackTrace()));
                } else if (m instanceof BuildEvent) {
                    BuildEvent be = (BuildEvent) m;
                    switch (be.getType()) {
                    case BuildStarted:
                        break;
                    case BuildStopped:
                        return new DefaultResult(argv, null);
                    case ProjectStarted:
                    case MojoStarted:
                    case MojoStopped:
                        output.projectStateChanged(be.projectId, be.display);
                        break;
                    case ProjectStopped:
                        output.projectFinished(be.projectId);
                    }
                } else if (m instanceof BuildMessage) {
                    BuildMessage bm = (BuildMessage) m;
                    output.accept(bm.getMessage());
                }
            }
        }

    }

    static void setDefaultArgs(List<String> args) {
        if (!args.stream().anyMatch(arg -> arg.startsWith("-T") || arg.equals("--threads"))) {
            args.add("-T1C");
        }
        if (!args.stream().anyMatch(arg -> arg.startsWith("-b") || arg.equals("--builder"))) {
            args.add("-bsmart");
        }
    }

    private class DefaultResult implements ExecutionResult {

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
