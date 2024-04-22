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
package org.apache.maven.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.inject.AbstractModule;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.UnrecognizedOptionException;
import org.apache.maven.InternalErrorException;
import org.apache.maven.Maven;
import org.apache.maven.api.services.MessageBuilder;
import org.apache.maven.api.services.MessageBuilderFactory;
import org.apache.maven.building.FileSource;
import org.apache.maven.building.Problem;
import org.apache.maven.building.Source;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.apache.maven.cli.event.ExecutionEventLogger;
import org.apache.maven.cli.internal.BootstrapCoreExtensionManager;
import org.apache.maven.cli.internal.extension.model.CoreExtension;
import org.apache.maven.cli.logging.Slf4jConfiguration;
import org.apache.maven.cli.logging.Slf4jConfigurationFactory;
import org.apache.maven.cli.transfer.QuietMavenTransferListener;
import org.apache.maven.cli.transfer.Slf4jMavenTransferListener;
import org.apache.maven.eventspy.internal.EventSpyDispatcher;
import org.apache.maven.exception.DefaultExceptionHandler;
import org.apache.maven.exception.ExceptionHandler;
import org.apache.maven.exception.ExceptionSummary;
import org.apache.maven.execution.*;
import org.apache.maven.execution.scope.internal.MojoExecutionScope;
import org.apache.maven.execution.scope.internal.MojoExecutionScopeModule;
import org.apache.maven.extension.internal.CoreExports;
import org.apache.maven.extension.internal.CoreExportsProvider;
import org.apache.maven.extension.internal.CoreExtensionEntry;
import org.apache.maven.jline.MessageUtils;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.root.RootLocator;
import org.apache.maven.plugin.ExtensionRealmCache;
import org.apache.maven.plugin.PluginArtifactsCache;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactsCache;
import org.apache.maven.properties.internal.SystemProperties;
import org.apache.maven.session.scope.internal.SessionScope;
import org.apache.maven.session.scope.internal.SessionScopeModule;
import org.apache.maven.toolchain.building.DefaultToolchainsBuildingRequest;
import org.apache.maven.toolchain.building.ToolchainsBuilder;
import org.apache.maven.toolchain.building.ToolchainsBuildingResult;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.interpolation.AbstractValueSource;
import org.codehaus.plexus.interpolation.BasicInterpolator;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.eclipse.aether.transfer.TransferListener;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.impl.ExternalTerminal;
import org.mvndaemon.mvnd.cache.invalidating.InvalidatingExtensionRealmCache;
import org.mvndaemon.mvnd.cache.invalidating.InvalidatingPluginArtifactsCache;
import org.mvndaemon.mvnd.cache.invalidating.InvalidatingProjectArtifactsCache;
import org.mvndaemon.mvnd.cli.EnvHelper;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.Os;
import org.mvndaemon.mvnd.logging.internal.Slf4jLoggerManager;
import org.mvndaemon.mvnd.logging.slf4j.MvndSimpleLogger;
import org.mvndaemon.mvnd.logging.smart.BuildEventListener;
import org.mvndaemon.mvnd.logging.smart.LoggingExecutionListener;
import org.mvndaemon.mvnd.logging.smart.LoggingOutputStream;
import org.mvndaemon.mvnd.transfer.DaemonMavenTransferListener;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static java.util.Comparator.comparing;
import static org.apache.maven.cli.CLIManager.BATCH_MODE;
import static org.apache.maven.cli.CLIManager.FORCE_INTERACTIVE;
import static org.apache.maven.cli.CLIManager.NON_INTERACTIVE;

/**
 * File origin:
 * https://github.com/apache/maven/blob/maven-3.6.2/maven-embedder/src/main/java/org/apache/maven/cli/MavenCli.java
 *
 * @author Jason van Zyl
 */
public class DaemonMavenCli implements DaemonCli {
    public static final String LOCAL_REPO_PROPERTY = "maven.repo.local";

    public static final String MULTIMODULE_PROJECT_DIRECTORY = "maven.multiModuleProjectDirectory";

    public static final String USER_HOME = System.getProperty("user.home");

    public static final File USER_MAVEN_CONFIGURATION_HOME = new File(USER_HOME, ".m2");

    public static final File DEFAULT_USER_TOOLCHAINS_FILE = new File(USER_MAVEN_CONFIGURATION_HOME, "toolchains.xml");

    public static final File DEFAULT_GLOBAL_TOOLCHAINS_FILE =
            new File(System.getProperty("maven.conf"), "toolchains.xml");

    private static final String EXT_CLASS_PATH = "maven.ext.class.path";

    private static final String EXTENSIONS_FILENAME = ".mvn/extensions.xml";

    private static final String MVN_MAVEN_CONFIG = ".mvn/maven.config";

    public static final String STYLE_COLOR_PROPERTY = "style.color";

    public static final String RAW_STREAMS = "raw-streams";

    private final Slf4jLoggerManager plexusLoggerManager;

    private final ILoggerFactory slf4jLoggerFactory;

    private final Logger slf4jLogger;

    private final ClassWorld classWorld;

    private final DefaultPlexusContainer container;

    private final EventSpyDispatcher eventSpyDispatcher;

    private final ModelProcessor modelProcessor;

    private final Maven maven;

    private final MavenExecutionRequestPopulator executionRequestPopulator;

    private final ToolchainsBuilder toolchainsBuilder;

    private final DefaultSecDispatcher dispatcher;

    private final Map<String, ConfigurationProcessor> configurationProcessors;

    private final LoggingExecutionListener executionListener;

    /**
     * Non-volatile, assuming that it is accessed only from the main thread
     */
    private BuildEventListener buildEventListener = BuildEventListener.dummy();

    private MessageBuilderFactory messageBuilderFactory;

    public DaemonMavenCli() throws Exception {
        slf4jLoggerFactory = LoggerFactory.getILoggerFactory();
        slf4jLogger = slf4jLoggerFactory.getLogger(this.getClass().getName());
        plexusLoggerManager = new Slf4jLoggerManager();

        this.classWorld = ((ClassRealm) Thread.currentThread().getContextClassLoader()).getWorld();
        this.messageBuilderFactory = new DaemonMessageBuilderFactory();

        container = container();

        eventSpyDispatcher = container.lookup(EventSpyDispatcher.class);
        maven = container.lookup(Maven.class);
        executionRequestPopulator = container.lookup(MavenExecutionRequestPopulator.class);
        modelProcessor = createModelProcessor(container);
        configurationProcessors = container.lookupMap(ConfigurationProcessor.class);
        toolchainsBuilder = container.lookup(ToolchainsBuilder.class);
        dispatcher = (DefaultSecDispatcher) container.lookup(SecDispatcher.class, "maven");
        executionListener = container.lookup(LoggingExecutionListener.class);
    }

    public int main(
            List<String> arguments,
            String workingDirectory,
            String projectDirectory,
            Map<String, String> clientEnv,
            BuildEventListener buildEventListener)
            throws Exception {
        Terminal terminal = new ExternalTerminal(
                "Maven",
                "ansi",
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                StandardCharsets.UTF_8);
        TerminalBuilder.setTerminalOverride(terminal);
        MessageUtils.systemInstall();
        this.buildEventListener = buildEventListener;
        try {
            CliRequest req = new CliRequest(null, null);
            req.args = arguments.toArray(new String[0]);
            req.workingDirectory = new File(workingDirectory).getCanonicalPath();
            req.multiModuleProjectDirectory = new File(projectDirectory);
            return doMain(req, clientEnv);
        } finally {
            this.buildEventListener = BuildEventListener.dummy();
            MessageUtils.systemUninstall();
        }
    }

    public int doMain(CliRequest cliRequest, Map<String, String> clientEnv) throws Exception {
        Properties props = (Properties) System.getProperties().clone();
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(container.getContainerRealm());
            initialize(cliRequest);
            environment(cliRequest.workingDirectory, clientEnv);
            cli(cliRequest);
            properties(cliRequest);
            logging(cliRequest);
            informativeCommands(cliRequest);
            version(cliRequest);
            container(cliRequest);
            configure(cliRequest, eventSpyDispatcher, configurationProcessors);
            toolchains(cliRequest);
            populateRequest(cliRequest);
            encryption(cliRequest);
            return execute(cliRequest);
        } catch (ExitException e) {
            return e.exitCode;
        } finally {
            eventSpyDispatcher.close();
            System.setProperties(props);
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    void initialize(CliRequest cliRequest) throws ExitException {
        cliRequest.classWorld = classWorld;

        if (cliRequest.workingDirectory == null) {
            cliRequest.workingDirectory = System.getProperty("user.dir");
        }

        if (cliRequest.multiModuleProjectDirectory == null) {
            buildEventListener.log(String.format("-D%s system property is not set.", MULTIMODULE_PROJECT_DIRECTORY));
            throw new ExitException(1);
        }
        System.setProperty("maven.multiModuleProjectDirectory", cliRequest.multiModuleProjectDirectory.toString());

        // We need to locate the top level project which may be pointed at using
        // the -f/--file option.  However, the command line isn't parsed yet, so
        // we need to iterate through the args to find it and act upon it.
        Path topDirectory = Paths.get(cliRequest.workingDirectory);
        boolean isAltFile = false;
        for (String arg : cliRequest.args) {
            if (isAltFile) {
                // this is the argument following -f/--file
                Path path = topDirectory.resolve(arg);
                if (Files.isDirectory(path)) {
                    topDirectory = path;
                } else if (Files.isRegularFile(path)) {
                    topDirectory = path.getParent();
                    if (!Files.isDirectory(topDirectory)) {
                        System.err.println("Directory " + topDirectory
                                + " extracted from the -f/--file command-line argument " + arg + " does not exist");
                        throw new ExitException(1);
                    }
                } else {
                    System.err.println(
                            "POM file " + arg + " specified with the -f/--file command line argument does not exist");
                    throw new ExitException(1);
                }
                break;
            } else {
                // Check if this is the -f/--file option
                isAltFile = arg.equals(String.valueOf(CLIManager.ALTERNATE_POM_FILE)) || arg.equals("file");
            }
        }
        topDirectory = getCanonicalPath(topDirectory);
        cliRequest.topDirectory = topDirectory;
        // We're very early in the process, and we don't have the container set up yet,
        // so we rely on the JDK services to eventually look up a custom RootLocator.
        // This is used to compute {@code session.rootDirectory} but all {@code project.rootDirectory}
        // properties will be computed through the RootLocator found in the container.
        RootLocator rootLocator =
                ServiceLoader.load(RootLocator.class).iterator().next();
        Path rootDirectory = rootLocator.findRoot(topDirectory);
        if (rootDirectory == null) {
            System.err.println(RootLocator.UNABLE_TO_FIND_ROOT_PROJECT_MESSAGE);
        }
        cliRequest.rootDirectory = rootDirectory;
    }

    void cli(CliRequest cliRequest) throws Exception {
        CLIManager cliManager = newCLIManager();

        CommandLine mavenConfig = null;
        try {
            File configFile = new File(cliRequest.multiModuleProjectDirectory, MVN_MAVEN_CONFIG);

            if (configFile.isFile()) {
                try (Stream<String> lines = Files.lines(configFile.toPath(), Charset.defaultCharset())) {
                    String[] args = lines.filter(arg -> !arg.isEmpty() && !arg.startsWith("#"))
                            .toArray(String[]::new);
                    mavenConfig = cliManager.parse(args);
                    List<?> unrecognized = mavenConfig.getArgList();
                    if (!unrecognized.isEmpty()) {
                        // This file can only contain options, not args (goals or phases)
                        throw new ParseException("Unrecognized maven.config file entries: " + unrecognized);
                    }
                }
            }
        } catch (ParseException e) {
            buildEventListener.log("Unable to parse maven.config: " + e.getMessage());
            buildEventListener.log("Run 'mvnd --help' for available options.");
            throw new ExitException(1);
        }

        try {
            if (mavenConfig == null) {
                cliRequest.commandLine = cliManager.parse(cliRequest.args);
            } else {
                cliRequest.commandLine = cliMerge(cliManager.parse(cliRequest.args), mavenConfig);
            }
        } catch (ParseException e) {
            buildEventListener.log("Unable to parse command line options: " + e.getMessage());
            buildEventListener.log("Run 'mvnd --help' for available options.");
            throw new ExitException(1);
        }

        // check for presence of unsupported command line options
        try {
            if (cliRequest.commandLine.hasOption("llr")) {
                throw new UnrecognizedOptionException("Option '-llr' is not supported starting with Maven 3.9.1");
            }
        } catch (ParseException e) {
            System.err.println("Unsupported options: " + e.getMessage());
            cliManager.displayHelp(System.out);
            throw e;
        }
    }

    private void informativeCommands(CliRequest cliRequest) throws Exception {
        if (cliRequest.commandLine.hasOption(CLIManager.HELP)) {
            buildEventListener.log(MvndHelpFormatter.displayHelp(newCLIManager()));
            throw new ExitException(0);
        }

        if (cliRequest.commandLine.hasOption(CLIManager.VERSION)) {
            if (cliRequest.commandLine.hasOption(CLIManager.QUIET)) {
                buildEventListener.log(CLIReportingUtils.showVersionMinimal());
            } else {
                buildEventListener.log(CLIReportingUtils.showVersion());
            }
            throw new ExitException(0);
        }
    }

    private CLIManager newCLIManager() {
        CLIManager cliManager = new CLIManager();
        cliManager.options.addOption(Option.builder()
                .longOpt(RAW_STREAMS)
                .desc("Do not decorate output and error streams")
                .build());
        return cliManager;
    }

    private CommandLine cliMerge(CommandLine mavenArgs, CommandLine mavenConfig) {
        CommandLine.Builder commandLineBuilder = new CommandLine.Builder();

        // the args are easy, cli first then config file
        for (String arg : mavenArgs.getArgs()) {
            commandLineBuilder.addArg(arg);
        }
        for (String arg : mavenConfig.getArgs()) {
            commandLineBuilder.addArg(arg);
        }

        // now add all options, except for -D with cli first then config file
        List<Option> setPropertyOptions = new ArrayList<>();
        for (Option opt : mavenArgs.getOptions()) {
            if (String.valueOf(CLIManager.SET_SYSTEM_PROPERTY).equals(opt.getOpt())) {
                setPropertyOptions.add(opt);
            } else {
                commandLineBuilder.addOption(opt);
            }
        }
        for (Option opt : mavenConfig.getOptions()) {
            commandLineBuilder.addOption(opt);
        }
        // finally add the CLI system properties
        for (Option opt : setPropertyOptions) {
            commandLineBuilder.addOption(opt);
        }
        return commandLineBuilder.build();
    }

    /**
     * configure logging
     */
    void logging(CliRequest cliRequest) {
        // LOG LEVEL
        CommandLine commandLine = cliRequest.commandLine;
        cliRequest.verbose = commandLine.hasOption(CLIManager.VERBOSE) || commandLine.hasOption(CLIManager.DEBUG);
        cliRequest.quiet = !cliRequest.verbose && commandLine.hasOption(CLIManager.QUIET);
        cliRequest.showErrors = cliRequest.verbose || commandLine.hasOption(CLIManager.ERRORS);

        Slf4jConfiguration slf4jConfiguration = Slf4jConfigurationFactory.getConfiguration(slf4jLoggerFactory);

        if (cliRequest.verbose) {
            cliRequest.request.setLoggingLevel(MavenExecutionRequest.LOGGING_LEVEL_DEBUG);
            slf4jConfiguration.setRootLoggerLevel(Slf4jConfiguration.Level.DEBUG);
        } else if (cliRequest.quiet) {
            cliRequest.request.setLoggingLevel(MavenExecutionRequest.LOGGING_LEVEL_ERROR);
            slf4jConfiguration.setRootLoggerLevel(Slf4jConfiguration.Level.ERROR);
        }
        // else fall back to default log level specified in conf
        // see https://issues.apache.org/jira/browse/MNG-2570

        // LOG COLOR
        String styleColor = cliRequest.getUserProperties().getProperty(STYLE_COLOR_PROPERTY, "auto");
        if ("always".equals(styleColor)) {
            MessageUtils.setColorEnabled(true);
        } else if ("never".equals(styleColor)) {
            MessageUtils.setColorEnabled(false);
        } else if (!"auto".equals(styleColor)) {
            throw new IllegalArgumentException("Invalid color configuration option [" + styleColor
                    + "]. Supported values are (auto|always|never).");
        } else {
            boolean isBatchMode = !commandLine.hasOption(FORCE_INTERACTIVE)
                    && (commandLine.hasOption(BATCH_MODE) || commandLine.hasOption(NON_INTERACTIVE));
            if (isBatchMode || commandLine.hasOption(CLIManager.LOG_FILE)) {
                MessageUtils.setColorEnabled(false);
            }
        }

        // LOG STREAMS
        if (commandLine.hasOption(CLIManager.LOG_FILE)) {
            File logFile = new File(commandLine.getOptionValue(CLIManager.LOG_FILE));
            logFile = resolveFile(logFile, cliRequest.workingDirectory);

            // redirect stdout and stderr to file
            try {
                PrintStream ps = new PrintStream(new FileOutputStream(logFile), true);
                System.setOut(ps);
                System.setErr(ps);
            } catch (FileNotFoundException e) {
                //
                // Ignore
                //
            }
        } else if (!Environment.MVND_RAW_STREAMS
                .asOptional()
                .map(Boolean::parseBoolean)
                .orElse(Boolean.FALSE)) {
            MvndSimpleLogger stdout = (MvndSimpleLogger) slf4jLoggerFactory.getLogger("stdout");
            MvndSimpleLogger stderr = (MvndSimpleLogger) slf4jLoggerFactory.getLogger("stderr");
            stdout.setLogLevel(LocationAwareLogger.INFO_INT);
            stderr.setLogLevel(LocationAwareLogger.INFO_INT);
            System.setOut(new LoggingOutputStream(s -> stdout.info("[stdout] " + s)).printStream());
            System.setErr(new LoggingOutputStream(s -> stderr.warn("[stderr] " + s)).printStream());
        }

        slf4jConfiguration.activate();
    }

    private void version(CliRequest cliRequest) throws ExitException {
        if (cliRequest.verbose || cliRequest.commandLine.hasOption(CLIManager.SHOW_VERSION)) {
            buildEventListener.log(CLIReportingUtils.showVersion());
        }
    }

    private void commands(CliRequest cliRequest) {
        if (cliRequest.showErrors) {
            slf4jLogger.info("Error stacktraces are turned on.");
        }

        if (MavenExecutionRequest.CHECKSUM_POLICY_WARN.equals(cliRequest.request.getGlobalChecksumPolicy())) {
            slf4jLogger.info("Disabling strict checksum verification on all artifact downloads.");
        } else if (MavenExecutionRequest.CHECKSUM_POLICY_FAIL.equals(cliRequest.request.getGlobalChecksumPolicy())) {
            slf4jLogger.info("Enabling strict checksum verification on all artifact downloads.");
        }

        if (slf4jLogger.isDebugEnabled()) {
            slf4jLogger.debug("Message scheme: {}", (MessageUtils.isColorEnabled() ? "color" : "plain"));
            if (MessageUtils.isColorEnabled()) {
                MessageBuilder buff = MessageUtils.builder();
                buff.a("Message styles: ");
                buff.trace("trace").a(' ');
                buff.debug("debug").a(' ');
                buff.info("info").a(' ');
                buff.warning("warning").a(' ');
                buff.error("error").a(' ');

                buff.success("success").a(' ');
                buff.failure("failure").a(' ');
                buff.strong("strong").a(' ');
                buff.mojo("mojo").a(' ');
                buff.project("project");
                slf4jLogger.debug(buff.toString());
            }
        }
    }

    // Needed to make this method package visible to make writing a unit test possible
    // Maybe it's better to move some of those methods to separate class (SoC).
    void properties(CliRequest cliRequest) throws Exception {
        Properties paths = new Properties();
        if (cliRequest.topDirectory != null) {
            paths.put("session.topDirectory", cliRequest.topDirectory.toString());
        }
        if (cliRequest.rootDirectory != null) {
            paths.put("session.rootDirectory", cliRequest.rootDirectory.toString());
        }

        populateProperties(cliRequest.commandLine, paths, cliRequest.systemProperties, cliRequest.userProperties);

        // now that we have properties, interpolate all arguments
        BasicInterpolator interpolator =
                createInterpolator(paths, cliRequest.systemProperties, cliRequest.userProperties);
        CommandLine.Builder commandLineBuilder = new CommandLine.Builder();
        for (Option option : cliRequest.commandLine.getOptions()) {
            if (!String.valueOf(CLIManager.SET_USER_PROPERTY).equals(option.getOpt())) {
                List<String> values = option.getValuesList();
                for (ListIterator<String> it = values.listIterator(); it.hasNext(); ) {
                    it.set(interpolator.interpolate(it.next()));
                }
            }
            commandLineBuilder.addOption(option);
        }
        for (String arg : cliRequest.commandLine.getArgList()) {
            commandLineBuilder.addArg(interpolator.interpolate(arg));
        }
        cliRequest.commandLine = commandLineBuilder.build();
    }

    void container(CliRequest cliRequest) {
        Map<String, Object> data = new HashMap<>();
        data.put("plexus", container);
        data.put("workingDirectory", cliRequest.workingDirectory);
        data.put("systemProperties", cliRequest.systemProperties);
        data.put("userProperties", cliRequest.userProperties);
        data.put("versionProperties", CLIReportingUtils.getBuildProperties());
        eventSpyDispatcher.init(() -> data);
    }

    DefaultPlexusContainer container() throws Exception {
        ClassRealm coreRealm = classWorld.getClassRealm("plexus.core");
        if (coreRealm == null) {
            coreRealm = classWorld.getRealms().iterator().next();
        }

        List<File> extClassPath = Stream.of(
                        Environment.MVND_EXT_CLASSPATH.asString().split(","))
                .filter(s -> s != null && !s.isEmpty())
                .map(File::new)
                .collect(Collectors.toList());

        CoreExtensionEntry coreEntry = CoreExtensionEntry.discoverFrom(coreRealm);

        List<CoreExtension> extensions = Stream.of(
                        Environment.MVND_CORE_EXTENSIONS.asString().split(";"))
                .filter(s -> s != null && !s.isEmpty())
                .map(s -> {
                    String[] parts = s.split(":");
                    CoreExtension ce = CoreExtension.newBuilder()
                            .groupId(parts[0])
                            .artifactId(parts[1])
                            .version(parts[2])
                            .build();
                    return ce;
                })
                .collect(Collectors.toList());
        List<CoreExtensionEntry> extensionsEntries =
                loadCoreExtensions(extensions, coreRealm, coreEntry.getExportedArtifacts());
        ClassRealm containerRealm = setupContainerRealm(classWorld, coreRealm, extClassPath, extensionsEntries);

        ContainerConfiguration cc = new DefaultContainerConfiguration()
                .setClassWorld(classWorld)
                .setRealm(containerRealm)
                .setClassPathScanning(PlexusConstants.SCANNING_INDEX)
                .setAutoWiring(true)
                .setJSR250Lifecycle(true)
                .setName("maven");

        Set<String> exportedArtifacts = new HashSet<>(coreEntry.getExportedArtifacts());
        Set<String> exportedPackages = new HashSet<>(coreEntry.getExportedPackages());
        for (CoreExtensionEntry extension : extensionsEntries) {
            exportedArtifacts.addAll(extension.getExportedArtifacts());
            exportedPackages.addAll(extension.getExportedPackages());
        }
        exportedPackages.add("org.codehaus.plexus.components.interactivity");
        exportedPackages.add("org.mvndaemon.mvnd.interactivity");
        exportedArtifacts.add("org.codehaus.plexus:plexus-interactivity-api");

        final CoreExports exports = new CoreExports(containerRealm, exportedArtifacts, exportedPackages);

        final DefaultPlexusContainer container = new DefaultPlexusContainer(cc, new AbstractModule() {
            @Override
            protected void configure() {
                bind(ILoggerFactory.class).toInstance(slf4jLoggerFactory);
                bind(CoreExports.class).toInstance(exports);
                bind(CoreExportsProvider.class).toInstance(new CoreExportsProvider(exports));
                bind(ExtensionRealmCache.class).to(InvalidatingExtensionRealmCache.class);
                bind(PluginArtifactsCache.class).to(InvalidatingPluginArtifactsCache.class);
                // bind(PluginRealmCache.class).to(InvalidatingPluginRealmCache.class);
                bind(ProjectArtifactsCache.class).to(InvalidatingProjectArtifactsCache.class);
                // bind(PluginVersionResolver.class).to(CachingPluginVersionResolver.class);
                bind(MessageBuilderFactory.class).toInstance(messageBuilderFactory);
            }
        });

        // NOTE: To avoid inconsistencies, we'll use the TCCL exclusively for lookups
        container.setLookupRealm(null);
        Thread.currentThread().setContextClassLoader(container.getContainerRealm());

        container.setLoggerManager(plexusLoggerManager);

        AbstractValueSource extensionSource = new AbstractValueSource(false) {
            @Override
            public Object getValue(String expression) {
                return null;
            }
        };
        for (CoreExtensionEntry extension : extensionsEntries) {
            container.discoverComponents(
                    extension.getClassRealm(),
                    new SessionScopeModule(container.lookup(SessionScope.class)),
                    new MojoExecutionScopeModule(container.lookup(MojoExecutionScope.class)),
                    new ExtensionConfigurationModule(extension, extensionSource));
        }
        return container;
    }

    private List<CoreExtensionEntry> loadCoreExtensions(
            List<CoreExtension> extensions, ClassRealm containerRealm, Set<String> providedArtifacts) {
        try {
            if (extensions.isEmpty()) {
                return Collections.emptyList();
            }
            ContainerConfiguration cc = new DefaultContainerConfiguration() //
                    .setClassWorld(classWorld) //
                    .setRealm(containerRealm) //
                    .setClassPathScanning(PlexusConstants.SCANNING_INDEX) //
                    .setAutoWiring(true) //
                    .setJSR250Lifecycle(true) //
                    .setName("maven");

            DefaultPlexusContainer container = new DefaultPlexusContainer(cc, new AbstractModule() {
                @Override
                protected void configure() {
                    bind(ILoggerFactory.class).toInstance(slf4jLoggerFactory);
                }
            });
            MavenExecutionRequestPopulator executionRequestPopulator = null;
            try {
                CliRequest cliRequest = new CliRequest(new String[0], classWorld);
                cliRequest.commandLine = new CommandLine.Builder().build();
                container.setLookupRealm(null);
                container.setLoggerManager(plexusLoggerManager);
                container.getLoggerManager().setThresholds(cliRequest.request.getLoggingLevel());
                Thread.currentThread().setContextClassLoader(container.getContainerRealm());
                executionRequestPopulator = container.lookup(MavenExecutionRequestPopulator.class);
                final Map<String, ConfigurationProcessor> configurationProcessors =
                        container.lookupMap(ConfigurationProcessor.class);
                final EventSpyDispatcher eventSpyDispatcher = container.lookup(EventSpyDispatcher.class);
                properties(cliRequest);
                configure(cliRequest, eventSpyDispatcher, configurationProcessors);
                LoggingExecutionListener executionListener = container.lookup(LoggingExecutionListener.class);
                populateRequest(
                        cliRequest,
                        cliRequest.request,
                        eventSpyDispatcher,
                        container.lookup(ModelProcessor.class),
                        createTransferListener(cliRequest),
                        buildEventListener,
                        executionListener);
                executionRequestPopulator.populateDefaults(cliRequest.request);
                BootstrapCoreExtensionManager resolver = container.lookup(BootstrapCoreExtensionManager.class);
                return Collections.unmodifiableList(
                        resolver.loadCoreExtensions(cliRequest.request, providedArtifacts, extensions));
            } finally {
                executionRequestPopulator = null;
                container.dispose();
            }
        } catch (RuntimeException e) {
            // runtime exceptions are most likely bugs in maven, let them bubble up to the user
            throw e;
        } catch (Exception e) {
            slf4jLogger.warn("Failed to load extensions descriptor {}: {}", extensions, e.getMessage());
        }
        return Collections.emptyList();
    }

    private ClassRealm setupContainerRealm(
            ClassWorld classWorld, ClassRealm coreRealm, List<File> extClassPath, List<CoreExtensionEntry> extensions)
            throws Exception {
        if (!extClassPath.isEmpty() || !extensions.isEmpty()) {
            ClassRealm extRealm = classWorld.newRealm("maven.ext", null);
            extRealm.setParentRealm(coreRealm);
            slf4jLogger.debug("Populating class realm {}", extRealm.getId());
            for (File file : extClassPath) {

                extRealm.addURL(file.toURI().toURL());
            }
            for (CoreExtensionEntry entry : reverse(extensions)) {
                Set<String> exportedPackages = entry.getExportedPackages();
                ClassRealm realm = entry.getClassRealm();
                for (String exportedPackage : exportedPackages) {
                    extRealm.importFrom(realm, exportedPackage);
                }
                if (exportedPackages.isEmpty()) {
                    // sisu uses realm imports to establish component visibility
                    extRealm.importFrom(realm, realm.getId());
                }
            }
            return extRealm;
        }
        return coreRealm;
    }

    private static <T> List<T> reverse(List<T> list) {
        List<T> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }

    private List<File> parseExtClasspath(CliRequest cliRequest) {
        String extClassPath = cliRequest.userProperties.getProperty(EXT_CLASS_PATH);
        if (extClassPath == null) {
            extClassPath = cliRequest.systemProperties.getProperty(EXT_CLASS_PATH);
        }

        List<File> jars = new ArrayList<>();

        if (extClassPath != null) {
            for (String jar : extClassPath.split(File.pathSeparator)) {
                File file = resolveFile(new File(jar), cliRequest.workingDirectory);
                slf4jLogger.debug("  Included {}", file);
                jars.add(file);
            }
        }

        return jars;
    }

    //
    // This should probably be a separate tool and not be baked into Maven.
    //
    private void encryption(CliRequest cliRequest) throws Exception {
        if (cliRequest.commandLine.hasOption(CLIManager.ENCRYPT_MASTER_PASSWORD)) {
            throw new UnsupportedOperationException("Unsupported option: " + CLIManager.ENCRYPT_MASTER_PASSWORD);
        } else if (cliRequest.commandLine.hasOption(CLIManager.ENCRYPT_PASSWORD)) {
            throw new UnsupportedOperationException("Unsupported option: " + CLIManager.ENCRYPT_PASSWORD);
        }
    }

    private void environment(String workingDir, Map<String, String> clientEnv) {
        EnvHelper.environment(workingDir, clientEnv);
    }

    private int execute(CliRequest cliRequest) throws MavenExecutionRequestPopulationException {
        commands(cliRequest);

        MavenExecutionRequest request = executionRequestPopulator.populateDefaults(cliRequest.request);

        eventSpyDispatcher.onEvent(request);

        slf4jLogger.info(MessageUtils.builder()
                .a("Processing build on daemon ")
                .strong(Environment.MVND_ID.asString())
                .toString());

        MavenExecutionResult result = maven.execute(request);

        LoggingOutputStream.forceFlush(System.out);
        LoggingOutputStream.forceFlush(System.err);

        eventSpyDispatcher.onEvent(result);

        if (result.hasExceptions()) {
            ExceptionHandler handler = new DefaultExceptionHandler();

            Map<String, String> references = new LinkedHashMap<>();

            List<MavenProject> failedProjects = new ArrayList<>();

            for (Throwable exception : result.getExceptions()) {
                ExceptionSummary summary = handler.handleException(exception);

                logSummary(summary, references, "", cliRequest.showErrors);

                if (exception instanceof LifecycleExecutionException) {
                    MavenProject project = ((LifecycleExecutionException) exception).getProject();
                    if (project != null) {
                        failedProjects.add(project);
                    }
                }
            }

            slf4jLogger.error("");

            if (!cliRequest.showErrors) {
                slf4jLogger.error(
                        "To see the full stack trace of the errors, re-run Maven with the {} switch.",
                        MessageUtils.builder().strong("-e"));
            }
            if (!slf4jLogger.isDebugEnabled()) {
                slf4jLogger.error(
                        "Re-run Maven using the {} switch to enable full debug logging.",
                        MessageUtils.builder().strong("-X"));
            }

            if (!references.isEmpty()) {
                slf4jLogger.error("");
                slf4jLogger.error("For more information about the errors and possible solutions"
                        + ", please read the following articles:");

                for (Entry<String, String> entry : references.entrySet()) {
                    slf4jLogger.error("{} {}", MessageUtils.builder().strong(entry.getValue()), entry.getKey());
                }
            }

            if (result.canResume()) {
                logBuildResumeHint("mvn <args> -r");
            } else if (!failedProjects.isEmpty()) {
                List<MavenProject> sortedProjects = result.getTopologicallySortedProjects();

                // Sort the failedProjects list in the topologically sorted order.
                failedProjects.sort(comparing(sortedProjects::indexOf));

                MavenProject firstFailedProject = failedProjects.get(0);
                if (!firstFailedProject.equals(sortedProjects.get(0))) {
                    String resumeFromSelector = getResumeFromSelector(sortedProjects, firstFailedProject);
                    logBuildResumeHint("mvn <args> -rf " + resumeFromSelector);
                }
            }

            if (MavenExecutionRequest.REACTOR_FAIL_NEVER.equals(cliRequest.request.getReactorFailureBehavior())) {
                slf4jLogger.info("Build failures were ignored.");

                return 0;
            } else {
                return 1;
            }
        } else {
            return 0;
        }
    }

    private void logBuildResumeHint(String resumeBuildHint) {
        slf4jLogger.error("");
        slf4jLogger.error("After correcting the problems, you can resume the build with the command");
        slf4jLogger.error(MessageUtils.builder().a("  ").strong(resumeBuildHint).toString());
    }

    /**
     * A helper method to determine the value to resume the build with {@code -rf} taking into account the
     * edge case where multiple modules in the reactor have the same artifactId.
     * <p>
     * {@code -rf :artifactId} will pick up the first module which matches, but when multiple modules in the
     * reactor have the same artifactId, effective failed module might be later in build reactor.
     * This means that developer will either have to type groupId or wait for build execution of all modules
     * which were fine, but they are still before one which reported errors.
     * <p>
     * Then the returned value is {@code groupId:artifactId} when there is a name clash and
     * {@code :artifactId} if there is no conflict.
     *
     * @param  mavenProjects Maven projects which are part of build execution.
     * @param  failedProject Project which has failed.
     * @return               Value for -rf flag to resume build exactly from place where it failed ({@code :artifactId} in
     *                       general and {@code groupId:artifactId} when there is a name clash).
     */
    private String getResumeFromSelector(List<MavenProject> mavenProjects, MavenProject failedProject) {
        for (MavenProject buildProject : mavenProjects) {
            if (failedProject.getArtifactId().equals(buildProject.getArtifactId())
                    && !failedProject.equals(buildProject)) {
                return failedProject.getGroupId() + ":" + failedProject.getArtifactId();
            }
        }
        return ":" + failedProject.getArtifactId();
    }

    private void logSummary(
            ExceptionSummary summary, Map<String, String> references, String indent, boolean showErrors) {
        String msg = summary.getMessage();

        if (!summary.getReference().isEmpty()) {
            String referenceKey =
                    references.computeIfAbsent(summary.getReference(), k -> "[Help " + (references.size() + 1) + "]");
            if (msg.indexOf('\n') < 0) {
                msg += " -> " + MessageUtils.builder().strong(referenceKey);
            } else {
                msg += "\n-> " + MessageUtils.builder().strong(referenceKey);
            }
        }

        String[] lines = msg.split("(\r\n)|(\r)|(\n)");
        String currentColor = "";

        for (int i = 0; i < lines.length; i++) {
            // add eventual current color inherited from previous line
            String line = currentColor + lines[i];

            // look for last ANSI escape sequence to check if nextColor
            Matcher matcher = LAST_ANSI_SEQUENCE.matcher(line);
            String nextColor = "";
            if (matcher.find()) {
                nextColor = matcher.group(1);
                if (ANSI_RESET.equals(nextColor)) {
                    // last ANSI escape code is reset: no next color
                    nextColor = "";
                }
            }

            // effective line, with indent and reset if end is colored
            line = indent + line + ("".equals(nextColor) ? "" : ANSI_RESET);

            if ((i == lines.length - 1) && (showErrors || (summary.getException() instanceof InternalErrorException))) {
                slf4jLogger.error(line, summary.getException());
            } else {
                slf4jLogger.error(line);
            }

            currentColor = nextColor;
        }

        indent += "  ";

        for (ExceptionSummary child : summary.getChildren()) {
            logSummary(child, references, indent, showErrors);
        }
    }

    private static final Pattern LAST_ANSI_SEQUENCE = Pattern.compile("(\u001B\\[[;\\d]*[ -/]*[@-~])[^\u001B]*$");

    private static final String ANSI_RESET = "\u001B\u005Bm";

    private static void configure(
            CliRequest cliRequest,
            EventSpyDispatcher eventSpyDispatcher,
            Map<String, ConfigurationProcessor> configurationProcessors)
            throws Exception {
        //
        // This is not ideal but there are events specifically for configuration from the CLI which I don't
        // believe are really valid but there are ITs which assert the right events are published so this
        // needs to be supported so the EventSpyDispatcher needs to be put in the CliRequest so that
        // it can be accessed by configuration processors.
        //
        cliRequest.request.setEventSpyDispatcher(eventSpyDispatcher);

        //
        // We expect at most 2 implementations to be available. The SettingsXmlConfigurationProcessor implementation
        // is always available in the core and likely always will be, but we may have another ConfigurationProcessor
        // present supplied by the user. The rule is that we only allow the execution of one ConfigurationProcessor.
        // If there is more than one then we execute the one supplied by the user, otherwise we execute the
        // the default SettingsXmlConfigurationProcessor.
        //
        int userSuppliedConfigurationProcessorCount = configurationProcessors.size() - 1;

        if (userSuppliedConfigurationProcessorCount == 0) {
            //
            // Our settings.xml source is historically how we have configured Maven from the CLI so we are going to
            // have to honour its existence forever. So let's run it.
            //
            configurationProcessors.get(SettingsXmlConfigurationProcessor.HINT).process(cliRequest);
        } else if (userSuppliedConfigurationProcessorCount == 1) {
            //
            // Run the user supplied ConfigurationProcessor
            //
            for (Entry<String, ConfigurationProcessor> entry : configurationProcessors.entrySet()) {
                String hint = entry.getKey();
                if (!hint.equals(SettingsXmlConfigurationProcessor.HINT)) {
                    ConfigurationProcessor configurationProcessor = entry.getValue();
                    configurationProcessor.process(cliRequest);
                }
            }
        } else if (userSuppliedConfigurationProcessorCount > 1) {
            //
            // There are too many ConfigurationProcessors so we don't know which one to run so report the error.
            //
            StringBuilder sb = new StringBuilder(String.format(
                    "\nThere can only be one user supplied ConfigurationProcessor, there are %s:\n\n",
                    userSuppliedConfigurationProcessorCount));
            for (Entry<String, ConfigurationProcessor> entry : configurationProcessors.entrySet()) {
                String hint = entry.getKey();
                if (!hint.equals(SettingsXmlConfigurationProcessor.HINT)) {
                    ConfigurationProcessor configurationProcessor = entry.getValue();
                    sb.append(String.format(
                            "%s\n", configurationProcessor.getClass().getName()));
                }
            }
            sb.append("\n");
            throw new Exception(sb.toString());
        }
    }

    void toolchains(CliRequest cliRequest) throws Exception {
        File userToolchainsFile;

        if (cliRequest.commandLine.hasOption(CLIManager.ALTERNATE_USER_TOOLCHAINS)) {
            userToolchainsFile = new File(cliRequest.commandLine.getOptionValue(CLIManager.ALTERNATE_USER_TOOLCHAINS));
            userToolchainsFile = resolveFile(userToolchainsFile, cliRequest.workingDirectory);

            if (!userToolchainsFile.isFile()) {
                throw new FileNotFoundException(
                        "The specified user toolchains file does not exist: " + userToolchainsFile);
            }
        } else {
            userToolchainsFile = DEFAULT_USER_TOOLCHAINS_FILE;
        }

        File globalToolchainsFile;

        if (cliRequest.commandLine.hasOption(CLIManager.ALTERNATE_GLOBAL_TOOLCHAINS)) {
            globalToolchainsFile =
                    new File(cliRequest.commandLine.getOptionValue(CLIManager.ALTERNATE_GLOBAL_TOOLCHAINS));
            globalToolchainsFile = resolveFile(globalToolchainsFile, cliRequest.workingDirectory);

            if (!globalToolchainsFile.isFile()) {
                throw new FileNotFoundException(
                        "The specified global toolchains file does not exist: " + globalToolchainsFile);
            }
        } else {
            globalToolchainsFile = DEFAULT_GLOBAL_TOOLCHAINS_FILE;
        }

        cliRequest.request.setGlobalToolchainsFile(globalToolchainsFile);
        cliRequest.request.setUserToolchainsFile(userToolchainsFile);

        DefaultToolchainsBuildingRequest toolchainsRequest = new DefaultToolchainsBuildingRequest();
        if (globalToolchainsFile.isFile()) {
            toolchainsRequest.setGlobalToolchainsSource(new FileSource(globalToolchainsFile));
        }
        if (userToolchainsFile.isFile()) {
            toolchainsRequest.setUserToolchainsSource(new FileSource(userToolchainsFile));
        }

        eventSpyDispatcher.onEvent(toolchainsRequest);

        slf4jLogger.debug(
                "Reading global toolchains from {}",
                getLocation(toolchainsRequest.getGlobalToolchainsSource(), globalToolchainsFile));
        slf4jLogger.debug(
                "Reading user toolchains from {}",
                getLocation(toolchainsRequest.getUserToolchainsSource(), userToolchainsFile));

        ToolchainsBuildingResult toolchainsResult = toolchainsBuilder.build(toolchainsRequest);

        eventSpyDispatcher.onEvent(toolchainsResult);

        executionRequestPopulator.populateFromToolchains(cliRequest.request, toolchainsResult.getEffectiveToolchains());

        if (!toolchainsResult.getProblems().isEmpty() && slf4jLogger.isWarnEnabled()) {
            slf4jLogger.warn("");
            slf4jLogger.warn("Some problems were encountered while building the effective toolchains");

            for (Problem problem : toolchainsResult.getProblems()) {
                slf4jLogger.warn("{} @ {}", problem.getMessage(), problem.getLocation());
            }

            slf4jLogger.warn("");
        }
    }

    private Object getLocation(Source source, File defaultLocation) {
        if (source != null) {
            return source.getLocation();
        }
        return defaultLocation;
    }

    private void populateRequest(CliRequest cliRequest) {
        populateRequest(
                cliRequest,
                cliRequest.request,
                eventSpyDispatcher,
                modelProcessor,
                createTransferListener(cliRequest),
                buildEventListener,
                executionListener);
    }

    private void populateRequest(
            CliRequest cliRequest,
            MavenExecutionRequest request,
            EventSpyDispatcher eventSpyDispatcher,
            ModelProcessor modelProcessor,
            TransferListener transferListener,
            BuildEventListener buildEventListener,
            LoggingExecutionListener executionListener) {
        CommandLine commandLine = cliRequest.commandLine;
        String workingDirectory = cliRequest.workingDirectory;
        boolean quiet = cliRequest.quiet;
        boolean verbose = cliRequest.verbose;
        request.setShowErrors(cliRequest.showErrors); // default: false
        File baseDirectory = new File(workingDirectory, "").getAbsoluteFile();

        disableInteractiveModeIfNeeded(cliRequest, request);
        enableOnPresentOption(commandLine, CLIManager.SUPPRESS_SNAPSHOT_UPDATES, request::setNoSnapshotUpdates);
        request.setGoals(commandLine.getArgList());
        request.setReactorFailureBehavior(determineReactorFailureBehaviour(commandLine));
        disableOnPresentOption(commandLine, CLIManager.NON_RECURSIVE, request::setRecursive);
        enableOnPresentOption(commandLine, CLIManager.OFFLINE, request::setOffline);
        enableOnPresentOption(commandLine, CLIManager.UPDATE_SNAPSHOTS, request::setUpdateSnapshots);
        request.setGlobalChecksumPolicy(determineGlobalCheckPolicy(commandLine));
        request.setBaseDirectory(baseDirectory);
        request.setSystemProperties(cliRequest.systemProperties);
        request.setUserProperties(cliRequest.userProperties);
        request.setMultiModuleProjectDirectory(cliRequest.multiModuleProjectDirectory);
        request.setRootDirectory(cliRequest.rootDirectory);
        request.setTopDirectory(cliRequest.topDirectory);
        request.setPom(determinePom(modelProcessor, commandLine, workingDirectory, baseDirectory));
        request.setTransferListener(transferListener);
        request.setExecutionListener(executionListener);

        ExecutionEventLogger executionEventLogger = new ExecutionEventLogger(messageBuilderFactory);
        executionListener.init(eventSpyDispatcher.chainListener(executionEventLogger), buildEventListener);

        if ((request.getPom() != null) && (request.getPom().getParentFile() != null)) {
            request.setBaseDirectory(request.getPom().getParentFile());
        }

        request.setResumeFrom(commandLine.getOptionValue(CLIManager.RESUME_FROM));
        enableOnPresentOption(commandLine, CLIManager.RESUME, request::setResume);
        request.setMakeBehavior(determineMakeBehavior(commandLine));
        request.setCacheNotFound(true);
        request.setCacheTransferError(false);

        performProjectActivation(commandLine, request.getProjectActivation());
        performProfileActivation(commandLine, request.getProfileActivation());

        final String localRepositoryPath = determineLocalRepositoryPath(request);
        if (localRepositoryPath != null) {
            request.setLocalRepositoryPath(localRepositoryPath);
        }

        //
        // Builder, concurrency and parallelism
        //
        // We preserve the existing methods for builder selection which is to look for various inputs in the threading
        // configuration. We don't have an easy way to allow a pluggable builder to provide its own configuration
        // parameters but this is sufficient for now. Ultimately we want components like Builders to provide a way to
        // extend the command line to accept its own configuration parameters.
        //
        final String threadConfiguration = commandLine.getOptionValue(CLIManager.THREADS);

        if (threadConfiguration != null) {
            int degreeOfConcurrency = calculateDegreeOfConcurrency(threadConfiguration);
            if (degreeOfConcurrency > 1) {
                request.setBuilderId("multithreaded");
                request.setDegreeOfConcurrency(degreeOfConcurrency);
            }
        }

        //
        // Allow the builder to be overridden by the user if requested. The builders are now pluggable.
        //
        request.setBuilderId(commandLine.getOptionValue(CLIManager.BUILDER, request.getBuilderId()));
    }

    private void disableInteractiveModeIfNeeded(final CliRequest cliRequest, final MavenExecutionRequest request) {
        CommandLine commandLine = cliRequest.getCommandLine();
        if (commandLine.hasOption(FORCE_INTERACTIVE)) {
            return;
        }

        boolean runningOnCI = isRunningOnCI(cliRequest.getSystemProperties());
        if (runningOnCI) {
            slf4jLogger.info("Making this build non-interactive, because the environment variable CI equals \"true\"."
                    + " Disable this detection by removing that variable or adding --force-interactive.");
            request.setInteractiveMode(false);
        } else if (commandLine.hasOption(BATCH_MODE) || commandLine.hasOption(NON_INTERACTIVE)) {
            request.setInteractiveMode(false);
        }
    }

    private static boolean isRunningOnCI(Properties systemProperties) {
        String ciEnv = systemProperties.getProperty("env.CI");
        return ciEnv != null && !"false".equals(ciEnv);
    }

    private String determineLocalRepositoryPath(final MavenExecutionRequest request) {
        String userDefinedLocalRepo = request.getUserProperties().getProperty(DaemonMavenCli.LOCAL_REPO_PROPERTY);
        if (userDefinedLocalRepo != null) {
            return userDefinedLocalRepo;
        }

        return request.getSystemProperties().getProperty(DaemonMavenCli.LOCAL_REPO_PROPERTY);
    }

    private File determinePom(
            ModelProcessor modelProcessor,
            final CommandLine commandLine,
            final String workingDirectory,
            final File baseDirectory) {
        String alternatePomFile = null;
        if (commandLine.hasOption(CLIManager.ALTERNATE_POM_FILE)) {
            alternatePomFile = commandLine.getOptionValue(CLIManager.ALTERNATE_POM_FILE);
        }

        File current = baseDirectory;
        if (alternatePomFile != null) {
            current = resolveFile(new File(alternatePomFile), workingDirectory);
        }

        if (modelProcessor != null) {
            return modelProcessor.locateExistingPom(current);
        } else {
            return current.isFile() ? current : null;
        }
    }

    // Visible for testing
    static void performProjectActivation(final CommandLine commandLine, final ProjectActivation projectActivation) {
        if (commandLine.hasOption(CLIManager.PROJECT_LIST)) {
            final String[] optionValues = commandLine.getOptionValues(CLIManager.PROJECT_LIST);

            if (optionValues == null || optionValues.length == 0) {
                return;
            }

            for (final String optionValue : optionValues) {
                for (String token : optionValue.split(",")) {
                    String selector = token.trim();
                    boolean active = true;
                    if (selector.charAt(0) == '-' || selector.charAt(0) == '!') {
                        active = false;
                        selector = selector.substring(1);
                    } else if (token.charAt(0) == '+') {
                        selector = selector.substring(1);
                    }

                    boolean optional = selector.charAt(0) == '?';
                    selector = selector.substring(optional ? 1 : 0);

                    projectActivation.addProjectActivation(selector, active, optional);
                }
            }
        }
    }

    // Visible for testing
    static void performProfileActivation(final CommandLine commandLine, final ProfileActivation profileActivation) {
        if (commandLine.hasOption(CLIManager.ACTIVATE_PROFILES)) {
            final String[] optionValues = commandLine.getOptionValues(CLIManager.ACTIVATE_PROFILES);

            if (optionValues == null || optionValues.length == 0) {
                return;
            }

            for (final String optionValue : optionValues) {
                for (String token : optionValue.split(",")) {
                    String profileId = token.trim();
                    boolean active = true;
                    if (profileId.charAt(0) == '-' || profileId.charAt(0) == '!') {
                        active = false;
                        profileId = profileId.substring(1);
                    } else if (token.charAt(0) == '+') {
                        profileId = profileId.substring(1);
                    }

                    boolean optional = profileId.charAt(0) == '?';
                    profileId = profileId.substring(optional ? 1 : 0);

                    profileActivation.addProfileActivation(profileId, active, optional);
                }
            }
        }
    }

    private ExecutionListener determineExecutionListener(EventSpyDispatcher eventSpyDispatcher) {
        ExecutionListener executionListener = new ExecutionEventLogger(messageBuilderFactory);
        if (eventSpyDispatcher != null) {
            return eventSpyDispatcher.chainListener(executionListener);
        } else {
            return executionListener;
        }
    }

    private String determineReactorFailureBehaviour(final CommandLine commandLine) {
        if (commandLine.hasOption(CLIManager.FAIL_FAST)) {
            return MavenExecutionRequest.REACTOR_FAIL_FAST;
        } else if (commandLine.hasOption(CLIManager.FAIL_AT_END)) {
            return MavenExecutionRequest.REACTOR_FAIL_AT_END;
        } else if (commandLine.hasOption(CLIManager.FAIL_NEVER)) {
            return MavenExecutionRequest.REACTOR_FAIL_NEVER;
        } else {
            // this is the default behavior.
            return MavenExecutionRequest.REACTOR_FAIL_FAST;
        }
    }

    private String determineMakeBehavior(final CommandLine cl) {
        if (cl.hasOption(CLIManager.ALSO_MAKE) && !cl.hasOption(CLIManager.ALSO_MAKE_DEPENDENTS)) {
            return MavenExecutionRequest.REACTOR_MAKE_UPSTREAM;
        } else if (!cl.hasOption(CLIManager.ALSO_MAKE) && cl.hasOption(CLIManager.ALSO_MAKE_DEPENDENTS)) {
            return MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM;
        } else if (cl.hasOption(CLIManager.ALSO_MAKE) && cl.hasOption(CLIManager.ALSO_MAKE_DEPENDENTS)) {
            return MavenExecutionRequest.REACTOR_MAKE_BOTH;
        } else {
            return null;
        }
    }

    private String determineGlobalCheckPolicy(final CommandLine commandLine) {
        if (commandLine.hasOption(CLIManager.CHECKSUM_FAILURE_POLICY)) {
            return MavenExecutionRequest.CHECKSUM_POLICY_FAIL;
        } else if (commandLine.hasOption(CLIManager.CHECKSUM_WARNING_POLICY)) {
            return MavenExecutionRequest.CHECKSUM_POLICY_WARN;
        } else {
            return null;
        }
    }

    private void disableOnPresentOption(
            final CommandLine commandLine, final String option, final Consumer<Boolean> setting) {
        if (commandLine.hasOption(option)) {
            setting.accept(false);
        }
    }

    private void disableOnPresentOption(
            final CommandLine commandLine, final char option, final Consumer<Boolean> setting) {
        disableOnPresentOption(commandLine, String.valueOf(option), setting);
    }

    private void enableOnPresentOption(
            final CommandLine commandLine, final String option, final Consumer<Boolean> setting) {
        if (commandLine.hasOption(option)) {
            setting.accept(true);
        }
    }

    private void enableOnPresentOption(
            final CommandLine commandLine, final char option, final Consumer<Boolean> setting) {
        enableOnPresentOption(commandLine, String.valueOf(option), setting);
    }

    private void enableOnAbsentOption(
            final CommandLine commandLine, final char option, final Consumer<Boolean> setting) {
        if (!commandLine.hasOption(option)) {
            setting.accept(true);
        }
    }

    int calculateDegreeOfConcurrency(String threadConfiguration) {
        try {
            if (threadConfiguration.endsWith("C")) {
                String str = threadConfiguration.substring(0, threadConfiguration.length() - 1);
                float coreMultiplier = Float.parseFloat(str);

                if (coreMultiplier <= 0.0f) {
                    throw new IllegalArgumentException("Invalid threads core multiplier value: '" + threadConfiguration
                            + "C'. Value must be positive.");
                }

                int procs = Runtime.getRuntime().availableProcessors();
                int threads = (int) (coreMultiplier * procs);
                return threads == 0 ? 1 : threads;
            } else {
                int threads = Integer.parseInt(threadConfiguration);

                if (threads <= 0) {
                    throw new IllegalArgumentException(
                            "Invalid threads value: '" + threadConfiguration + "'. Value must be positive.");
                }

                return threads;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid threads value: '" + threadConfiguration
                    + "'. Supported are int and float values ending with C.");
        }
    }

    static File resolveFile(File file, String workingDirectory) {
        if (file == null) {
            return null;
        } else if (file.isAbsolute()) {
            return file;
        } else if (file.getPath().startsWith(File.separator)) {
            // drive-relative Windows path
            return file.getAbsoluteFile();
        } else {
            return new File(workingDirectory, file.getPath()).getAbsoluteFile();
        }
    }

    // ----------------------------------------------------------------------
    // System properties handling
    // ----------------------------------------------------------------------

    static void populateProperties(
            CommandLine commandLine, Properties paths, Properties systemProperties, Properties userProperties)
            throws Exception {
        addEnvVars(systemProperties);

        // ----------------------------------------------------------------------
        // Options that are set on the command line become system properties
        // and therefore are set in the session properties. System properties
        // are most dominant.
        // ----------------------------------------------------------------------

        final Properties userSpecifiedProperties =
                commandLine.getOptionProperties(String.valueOf(CLIManager.SET_SYSTEM_PROPERTY));

        SystemProperties.addSystemProperties(systemProperties);

        // ----------------------------------------------------------------------
        // Properties containing info about the currently running version of Maven
        // These override any corresponding properties set on the command line
        // ----------------------------------------------------------------------

        Properties buildProperties = CLIReportingUtils.getBuildProperties();

        String mavenVersion = buildProperties.getProperty(CLIReportingUtils.BUILD_VERSION_PROPERTY);
        systemProperties.setProperty("maven.version", mavenVersion);

        String mavenBuildVersion = CLIReportingUtils.createMavenVersionString(buildProperties);
        systemProperties.setProperty("maven.build.version", mavenBuildVersion);

        BasicInterpolator interpolator =
                createInterpolator(paths, systemProperties, userProperties, userSpecifiedProperties);
        for (Map.Entry<Object, Object> e : userSpecifiedProperties.entrySet()) {
            String name = (String) e.getKey();
            String value = interpolator.interpolate((String) e.getValue());
            userProperties.setProperty(name, value);
            // ----------------------------------------------------------------------
            // I'm leaving the setting of system properties here as not to break
            // the SystemPropertyProfileActivator. This won't harm embedding. jvz.
            // ----------------------------------------------------------------------
            if (System.getProperty(name) == null) {
                System.setProperty(name, value);
            }
        }
    }

    public static void addEnvVars(Properties props) {
        if (props != null) {
            boolean caseSensitive = Os.current() == Os.WINDOWS;
            for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
                String key = "env."
                        + (caseSensitive ? entry.getKey() : entry.getKey().toUpperCase(Locale.ENGLISH));
                props.setProperty(key, entry.getValue());
            }
        }
    }

    private static void setCliProperty(String name, String value, Properties properties) {
        properties.setProperty(name, value);

        // ----------------------------------------------------------------------
        // I'm leaving the setting of system properties here as not to break
        // the SystemPropertyProfileActivator. This won't harm embedding. jvz.
        // ----------------------------------------------------------------------

        System.setProperty(name, value);
    }

    private static Path getCanonicalPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return getCanonicalPath(path.getParent()).resolve(path.getFileName());
        }
    }

    private static BasicInterpolator createInterpolator(Properties... properties) {
        StringSearchInterpolator interpolator = new StringSearchInterpolator();
        interpolator.addValueSource(new AbstractValueSource(false) {
            @Override
            public Object getValue(String expression) {
                for (Properties props : properties) {
                    Object val = props.getProperty(expression);
                    if (val != null) {
                        return val;
                    }
                }
                return null;
            }
        });
        return interpolator;
    }

    static class ExitException extends Exception {
        static final long serialVersionUID = 1L;
        int exitCode;

        ExitException(int exitCode) {
            this.exitCode = exitCode;
        }
    }

    //
    // Customizations available via the CLI
    //

    protected TransferListener createTransferListener(CliRequest cliRequest) {
        if (cliRequest.quiet || cliRequest.commandLine.hasOption(CLIManager.NO_TRANSFER_PROGRESS)) {
            return new QuietMavenTransferListener();
        } else if (cliRequest.request.isInteractiveMode() && !cliRequest.commandLine.hasOption(CLIManager.LOG_FILE)) {
            //
            // If we're logging to a file then we don't want the console transfer listener as it will spew
            // download progress all over the place
            //
            return getConsoleTransferListener();
        } else {
            return getBatchTransferListener();
        }
    }

    protected TransferListener getConsoleTransferListener() {
        return new DaemonMavenTransferListener(buildEventListener, new Slf4jMavenTransferListener());
    }

    protected TransferListener getBatchTransferListener() {
        return new Slf4jMavenTransferListener();
    }

    protected ModelProcessor createModelProcessor(PlexusContainer container) throws ComponentLookupException {
        return container.lookup(ModelProcessor.class);
    }
}
