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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.fusesource.jansi.Ansi;
import org.jboss.fuse.mvnd.client.ClientOutput.TerminalOutput;
import org.jboss.fuse.mvnd.client.Message.BuildEvent;
import org.jboss.fuse.mvnd.client.Message.BuildException;
import org.jboss.fuse.mvnd.client.Message.BuildMessage;
import org.jboss.fuse.mvnd.client.Message.MessageSerializer;
import org.jboss.fuse.mvnd.jpm.Process;
import org.jboss.fuse.mvnd.jpm.ProcessImpl;
import org.jboss.fuse.mvnd.jpm.ScriptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {

    private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);
    public static final String DAEMON_DEBUG = "daemon.debug";
    public static final String DAEMON_IDLE_TIMEOUT = "daemon.idleTimeout";
    public static final int DEFAULT_IDLE_TIMEOUT = (int) TimeUnit.HOURS.toMillis(3);
    public static final int DEFAULT_PERIODIC_CHECK_INTERVAL_MILLIS = 10 * 1000;
    public static final int CANCEL_TIMEOUT = 10 * 1000;
    private final ClientLayout layout;
    private final Properties buildProperties;

    public static void main(String[] argv) throws Exception {
        final List<String> args = new ArrayList<>(Arrays.asList(argv));

        Path logFile = null;
        for (int i = 0; i < args.size() - 2; i++) {
            String arg = args.get(i);
            if ("-l".equals(arg) || "--log-file".equals(arg)) {
                logFile = Paths.get(args.get(i + 1));
                args.remove(i);
                args.remove(i);
                break;
            }
        }

        try (TerminalOutput output = new TerminalOutput(logFile)) {
            new Client(ClientLayout.getEnvInstance()).execute(output, args);
        }
    }

    public Client(ClientLayout layout) {
        this.layout = layout;
        this.buildProperties = new Properties();
        try (InputStream is = Client.class.getResourceAsStream("build.properties")) {
            buildProperties.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Could not read build.properties");
        }
    }

    public <O extends ClientOutput> ClientResult<O> execute(O output, String... argv) throws IOException {
        return execute(output, Arrays.asList(argv));
    }

    public <O extends ClientOutput> ClientResult<O> execute(O output, List<String> argv) throws IOException {
        output.debug("Starting client");

        final List<String> args = new ArrayList<>(argv);

        // Print version if needed
        boolean version = args.contains("-v") || args.contains("-version") || args.contains("--version");
        boolean showVersion = args.contains("-V") || args.contains("--show-version");
        boolean debug = args.contains("-X") || args.contains("--debug");
        if (version || showVersion || debug) {
            final String nativeSuffix = Layout.isNative() ? " (native)" : "";
            final String v = Ansi.ansi().bold().a("Maven Daemon " + buildProperties.getProperty("version") + nativeSuffix).reset().toString();
            output.log(v);
            /* Do not return, rather pass -v to the server so that the client module does not need to depend on any Maven artifacts */
        }

        final Path javaHome = layout.javaHome();
        try (DaemonRegistry registry = new DaemonRegistry(layout.registry())) {
            boolean status = args.remove("--status");
            if (status) {
                output.log(String.format("    %36s  %5s  %5s  %7s  %s",
                        "UUID", "PID", "Port", "Status", "Timestamp"));
                registry.getAll().forEach(d -> output.log(String.format("    %36s  %5s  %5s  %7s  %s",
                        d.getUid(), d.getPid(), d.getAddress(), d.getState(),
                        new Date(Math.max(d.getLastIdle(), d.getLastBusy())).toString())));
                return new ClientResult<O>(argv, true, output);
            }
            boolean stop = args.remove("--stop");
            if (stop) {
                DaemonInfo[] dis = registry.getAll().toArray(new DaemonInfo[0]);
                if (dis.length > 0) {
                    output.log("Stopping " + dis.length + " running daemons");
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
                return new ClientResult<O>(argv, true, output);
            }

            setDefaultArgs(args);
            final Path settings = layout.getSettings();
            if (settings != null && !args.stream().anyMatch(arg -> arg.equals("-s") || arg.equals("--settings"))) {
                args.add("-s");
                args.add(settings.toString());
            }

            final Path localMavenRepository = layout.getLocalMavenRepository();
            if (localMavenRepository != null && !args.stream().anyMatch(arg -> arg.startsWith("-Dmaven.repo.local"))) {
                args.add("-Dmaven.repo.local=" + localMavenRepository.toString());
            }

            DaemonConnector connector = new DaemonConnector(layout, registry, this::startDaemon, new MessageSerializer());
            List<String> opts = new ArrayList<>();
            DaemonClientConnection daemon = connector.connect(new DaemonCompatibilitySpec(javaHome.toString(), opts));

            daemon.dispatch(new Message.BuildRequest(
                    args,
                    layout.userDir().toString(),
                    layout.multiModuleProjectDirectory().toString()));

            while (true) {
                Message m = daemon.receive();
                if (m instanceof BuildException) {
                    output.error((BuildException) m);
                    return new ClientResult<O>(argv, false, output);
                } else if (m instanceof BuildEvent) {
                    BuildEvent be = (BuildEvent) m;
                    switch (be.getType()) {
                        case BuildStarted:
                            break;
                        case BuildStopped:
                            return new ClientResult<O>(argv, true, output);
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
                    output.log(bm.getMessage());
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

    String startDaemon() {
//        DaemonParameters parms = new DaemonParameters();
//        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
//
//        }
//            List<String> args = new ArrayList<>();
//            args.add(javaHome.resolve(java).toString());
//            args.addAll(parms.getEffectiveJvmArgs());
//            args.add("-cp");
//            args.add(classpath);

        final String uid = UUID.randomUUID().toString();
        final Path mavenHome = layout.mavenHome();
        final Path workingDir = layout.userDir();
        String command = "";
        try {
            String classpath = findClientJar(mavenHome).toString();
            final String java = ScriptUtils.isWindows() ? "bin\\java.exe" : "bin/java";
            List<String> args = new ArrayList<>();
            args.add("\"" + layout.javaHome().resolve(java) + "\"");
            args.add("-classpath");
            args.add("\"" + classpath + "\"");
            if (Boolean.getBoolean(DAEMON_DEBUG)) {
                args.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000");
            }
            args.add("-Dmaven.home=\"" + mavenHome + "\"");
            args.add("-Dlogback.configurationFile=logback.xml");
            args.add("-Ddaemon.uid=" + uid);
            args.add("-Xmx4g");
            final String timeout = System.getProperty(DAEMON_IDLE_TIMEOUT);
            if (timeout != null) {
                args.add("-D" + DAEMON_IDLE_TIMEOUT + "=" + timeout);
            }
            args.add("\"-Dmaven.multiModuleProjectDirectory=" + layout.multiModuleProjectDirectory().toString() + "\"");

            args.add(ServerMain.class.getName());
            command = String.join(" ", args);

            LOGGER.debug("Starting daemon process: uid = {}, workingDir = {}, daemonArgs: {}", uid, workingDir, command);
            Process.create(workingDir.toFile(), command);
            return uid;
        } catch (Exception e) {
            throw new DaemonException.StartException(
                    String.format("Error starting daemon: uid = %s, workingDir = %s, daemonArgs: %s",
                            uid, workingDir, command), e);
        }
    }

    Path findClientJar(Path mavenHome) {
        final Path ext = mavenHome.resolve("lib/ext");
        final String clientJarName = "mvnd-client-"+ buildProperties.getProperty("version") + ".jar";
        try (Stream<Path> files = Files.list(ext)) {
            return files
                    .filter(f -> f.getFileName().toString().equals(clientJarName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Could not find " + clientJarName + " in " + ext));

        } catch (IOException e) {
            throw new RuntimeException("Could not find " + clientJarName + " in " + ext, e);
        }
    }

}
