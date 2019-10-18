package org.apache.maven.cli;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.inject.AbstractModule;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.apache.maven.InternalErrorException;
import org.apache.maven.Maven;
import org.apache.maven.building.FileSource;
import org.apache.maven.building.Problem;
import org.apache.maven.building.Source;
import org.apache.maven.cli.configuration.ConfigurationProcessor;
import org.apache.maven.cli.configuration.SettingsXmlConfigurationProcessor;
import org.apache.maven.cli.event.ExecutionEventLogger;
import org.apache.maven.cli.logging.Slf4jLoggerManager;
import org.apache.maven.cli.transfer.ConsoleMavenTransferListener;
import org.apache.maven.cli.transfer.QuietMavenTransferListener;
import org.apache.maven.cli.transfer.Slf4jMavenTransferListener;
import org.apache.maven.eventspy.internal.EventSpyDispatcher;
import org.apache.maven.exception.DefaultExceptionHandler;
import org.apache.maven.exception.ExceptionHandler;
import org.apache.maven.exception.ExceptionSummary;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.scope.internal.MojoExecutionScopeModule;
import org.apache.maven.extension.internal.CoreExports;
import org.apache.maven.extension.internal.CoreExtensionEntry;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.properties.internal.EnvironmentUtils;
import org.apache.maven.properties.internal.SystemProperties;
import org.apache.maven.session.scope.internal.SessionScopeModule;
import org.apache.maven.shared.utils.logging.MessageBuilder;
import org.apache.maven.shared.utils.logging.MessageUtils;
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
import org.codehaus.plexus.logging.LoggerManager;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.transfer.TransferListener;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static org.apache.maven.shared.utils.logging.MessageUtils.buffer;

// TODO push all common bits back to plexus cli and prepare for transition to Guice. We don't need 50 ways to make CLIs

/**
 * @author Jason van Zyl
 */
public class DaemonMavenCli
{
    public static final String LOCAL_REPO_PROPERTY = "maven.repo.local";

    public static final String MULTIMODULE_PROJECT_DIRECTORY = "maven.multiModuleProjectDirectory";

    public static final String USER_HOME = System.getProperty( "user.home" );

    public static final File USER_MAVEN_CONFIGURATION_HOME = new File( USER_HOME, ".m2" );

    public static final File DEFAULT_USER_TOOLCHAINS_FILE = new File( USER_MAVEN_CONFIGURATION_HOME, "toolchains.xml" );

    public static final File DEFAULT_GLOBAL_TOOLCHAINS_FILE =
        new File( System.getProperty( "maven.conf" ), "toolchains.xml" );

    private static final String MVN_MAVEN_CONFIG = ".mvn/maven.config";

    public static final String STYLE_COLOR_PROPERTY = "style.color";

    private final LoggerManager plexusLoggerManager;

    private final ILoggerFactory slf4jLoggerFactory;

    private final Logger slf4jLogger;

    private final ClassWorld classWorld;

    private DefaultPlexusContainer container;

    private EventSpyDispatcher eventSpyDispatcher;

    private ModelProcessor modelProcessor;

    private Maven maven;

    private MavenExecutionRequestPopulator executionRequestPopulator;

    private ToolchainsBuilder toolchainsBuilder;

    private DefaultSecDispatcher dispatcher;

    private Map<String, ConfigurationProcessor> configurationProcessors;

    public DaemonMavenCli() throws Exception {
        slf4jLoggerFactory = LoggerFactory.getILoggerFactory();
        slf4jLogger = slf4jLoggerFactory.getLogger( this.getClass().getName() );
        plexusLoggerManager = new Slf4jLoggerManager();

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        classWorld = new ClassWorld("plexus.core", cl);

        container();
    }

    // TODO need to externalize CliRequest
    public int doMain(CliRequest cliRequest) throws Exception
    {
        Properties props = (Properties) System.getProperties().clone();
        try {
            initialize(cliRequest);
            cli(cliRequest);
            properties(cliRequest);
            logging(cliRequest);
            version( cliRequest );
            configure(cliRequest);
            toolchains(cliRequest);
            populateRequest( cliRequest );
            encryption( cliRequest );
            repository( cliRequest );
            return execute( cliRequest);
        }
        catch ( ExitException e )
        {
            return e.exitCode;
        }
        finally
        {
            System.setProperties(props);
            eventSpyDispatcher.close();
        }
    }

    void initialize( CliRequest cliRequest )
        throws ExitException
    {
        cliRequest.classWorld = classWorld;

        if ( cliRequest.workingDirectory == null )
        {
            cliRequest.workingDirectory = System.getProperty( "user.dir" );
        }

        if ( cliRequest.multiModuleProjectDirectory == null )
        {
            System.err.format(
                "-D%s system property is not set.", MULTIMODULE_PROJECT_DIRECTORY );
            throw new ExitException( 1 );
        }
        System.setProperty( "maven.multiModuleProjectDirectory", cliRequest.multiModuleProjectDirectory.toString() );

        //
        // Make sure the Maven home directory is an absolute path to save us from confusion with say drive-relative
        // Windows paths.
        //
        String mavenHome = System.getProperty( "maven.home" );

        if ( mavenHome != null )
        {
            System.setProperty( "maven.home", new File( mavenHome ).getAbsolutePath() );
        }
    }

    void cli( CliRequest cliRequest )
        throws Exception
    {
        CLIManager cliManager = new CLIManager();

        List<String> args = new ArrayList<>();
        CommandLine mavenConfig = null;
        try
        {
            File configFile = new File( cliRequest.multiModuleProjectDirectory, MVN_MAVEN_CONFIG );

            if ( configFile.isFile() )
            {
                for ( String arg : new String( Files.readAllBytes( configFile.toPath() ) ).split( "\\s+" ) )
                {
                    if ( !arg.isEmpty() )
                    {
                        args.add( arg );
                    }
                }

                mavenConfig = cliManager.parse( args.toArray( new String[0] ) );
                List<?> unrecongized = mavenConfig.getArgList();
                if ( !unrecongized.isEmpty() )
                {
                    throw new ParseException( "Unrecognized maven.config entries: " + unrecongized );
                }
            }
        }
        catch ( ParseException e )
        {
            System.err.println( "Unable to parse maven.config: " + e.getMessage() );
            cliManager.displayHelp( System.out );
            throw e;
        }

        try
        {
            if ( mavenConfig == null )
            {
                cliRequest.commandLine = cliManager.parse( cliRequest.args );
            }
            else
            {
                cliRequest.commandLine = cliMerge( cliManager.parse( cliRequest.args ), mavenConfig );
            }
        }
        catch ( ParseException e )
        {
            System.err.println( "Unable to parse command line options: " + e.getMessage() );
            cliManager.displayHelp( System.out );
            throw e;
        }

        if ( cliRequest.commandLine.hasOption( CLIManager.HELP ) )
        {
            cliManager.displayHelp( System.out );
            throw new ExitException( 0 );
        }

        if ( cliRequest.commandLine.hasOption( CLIManager.VERSION ) )
        {
            System.out.println( CLIReportingUtils.showVersion() );
            throw new ExitException( 0 );
        }
    }

    private CommandLine cliMerge( CommandLine mavenArgs, CommandLine mavenConfig )
    {
        CommandLine.Builder commandLineBuilder = new CommandLine.Builder();

        // the args are easy, cli first then config file
        for ( String arg : mavenArgs.getArgs() )
        {
            commandLineBuilder.addArg( arg );
        }
        for ( String arg : mavenConfig.getArgs() )
        {
            commandLineBuilder.addArg( arg );
        }

        // now add all options, except for -D with cli first then config file
        List<Option> setPropertyOptions = new ArrayList<>();
        for ( Option opt : mavenArgs.getOptions() )
        {
            if ( String.valueOf( CLIManager.SET_SYSTEM_PROPERTY ).equals( opt.getOpt() ) )
            {
                setPropertyOptions.add( opt );
            }
            else
            {
                commandLineBuilder.addOption( opt );
            }
        }
        for ( Option opt : mavenConfig.getOptions() )
        {
            commandLineBuilder.addOption( opt );
        }
        // finally add the CLI system properties
        for ( Option opt : setPropertyOptions )
        {
            commandLineBuilder.addOption( opt );
        }
        return commandLineBuilder.build();
    }

    /**
     * configure logging
     */
    void logging( CliRequest cliRequest )
    {
        // LOG LEVEL
        cliRequest.debug = cliRequest.commandLine.hasOption( CLIManager.DEBUG );
        cliRequest.quiet = !cliRequest.debug && cliRequest.commandLine.hasOption( CLIManager.QUIET );
        cliRequest.showErrors = cliRequest.debug || cliRequest.commandLine.hasOption( CLIManager.ERRORS );

        java.util.logging.Level level;
        if ( cliRequest.debug )
        {
            cliRequest.request.setLoggingLevel( MavenExecutionRequest.LOGGING_LEVEL_DEBUG );
            level = Level.FINER;
        }
        else if ( cliRequest.quiet )
        {
            cliRequest.request.setLoggingLevel( MavenExecutionRequest.LOGGING_LEVEL_ERROR );
            level = Level.SEVERE;
        }
        else
        {
            cliRequest.request.setLoggingLevel( MavenExecutionRequest.LOGGING_LEVEL_INFO );
            level = Level.INFO;
        }

        // LOG COLOR
        String styleColor = cliRequest.getUserProperties().getProperty( STYLE_COLOR_PROPERTY, "auto" );
        if ( "always".equals( styleColor ) )
        {
            MessageUtils.setColorEnabled( true );
        }
        else if ( "never".equals( styleColor ) )
        {
            MessageUtils.setColorEnabled( false );
        }
        else if ( !"auto".equals( styleColor ) )
        {
            throw new IllegalArgumentException( "Invalid color configuration option [" + styleColor
                + "]. Supported values are (auto|always|never)." );
        }
        else if ( cliRequest.commandLine.hasOption( CLIManager.BATCH_MODE )
            || cliRequest.commandLine.hasOption( CLIManager.LOG_FILE ) )
        {
            MessageUtils.setColorEnabled( false );
        }

        // LOG STREAMS
        if ( cliRequest.commandLine.hasOption( CLIManager.LOG_FILE ) )
        {
            File logFile = new File( cliRequest.commandLine.getOptionValue( CLIManager.LOG_FILE ) );
            logFile = resolveFile( logFile, cliRequest.workingDirectory );

            // redirect stdout and stderr to file
            try
            {
                PrintStream ps = new PrintStream( new FileOutputStream( logFile ) );
                System.setOut( ps );
                System.setErr( ps );
            }
            catch ( FileNotFoundException e )
            {
                //
                // Ignore
                //
            }
        }

    }

    private void version( CliRequest cliRequest )
    {
        if ( cliRequest.debug || cliRequest.commandLine.hasOption( CLIManager.SHOW_VERSION ) )
        {
            System.out.println( CLIReportingUtils.showVersion() );
        }
    }

    private void commands( CliRequest cliRequest )
    {
        if ( cliRequest.showErrors )
        {
            slf4jLogger.info( "Error stacktraces are turned on." );
        }

        if ( MavenExecutionRequest.CHECKSUM_POLICY_WARN.equals( cliRequest.request.getGlobalChecksumPolicy() ) )
        {
            slf4jLogger.info( "Disabling strict checksum verification on all artifact downloads." );
        }
        else if ( MavenExecutionRequest.CHECKSUM_POLICY_FAIL.equals( cliRequest.request.getGlobalChecksumPolicy() ) )
        {
            slf4jLogger.info( "Enabling strict checksum verification on all artifact downloads." );
        }

        if ( slf4jLogger.isDebugEnabled() )
        {
            slf4jLogger.debug( "Message scheme: {}", ( MessageUtils.isColorEnabled() ? "color" : "plain" ) );
            if ( MessageUtils.isColorEnabled() )
            {
                MessageBuilder buff = MessageUtils.buffer();
                buff.a( "Message styles: " );
                buff.a( MessageUtils.level().debug( "debug" ) ).a( ' ' );
                buff.a( MessageUtils.level().info( "info" ) ).a( ' ' );
                buff.a( MessageUtils.level().warning( "warning" ) ).a( ' ' );
                buff.a( MessageUtils.level().error( "error" ) ).a( ' ' );

                buff.success( "success" ).a( ' ' );
                buff.failure( "failure" ).a( ' ' );
                buff.strong( "strong" ).a( ' ' );
                buff.mojo( "mojo" ).a( ' ' );
                buff.project( "project" );
                slf4jLogger.debug( buff.toString() );
            }
        }
    }

    //Needed to make this method package visible to make writing a unit test possible
    //Maybe it's better to move some of those methods to separate class (SoC).
    void properties( CliRequest cliRequest )
    {
        populateProperties( cliRequest.commandLine, cliRequest.systemProperties, cliRequest.userProperties );
    }

    void container()
        throws Exception
    {
        ClassRealm coreRealm = classWorld.getClassRealm( "plexus.core" );
        if ( coreRealm == null )
        {
            coreRealm = classWorld.getRealms().iterator().next();
        }

//        List<File> extClassPath = parseExtClasspath( cliRequest );

        CoreExtensionEntry coreEntry = CoreExtensionEntry.discoverFrom( coreRealm );
        List<CoreExtensionEntry> extensions = Collections.emptyList();
//            loadCoreExtensions( cliRequest, coreRealm, coreEntry.getExportedArtifacts() );

        ClassRealm containerRealm = coreRealm;

        ContainerConfiguration cc = new DefaultContainerConfiguration().setClassWorld( classWorld )
            .setRealm( containerRealm ).setClassPathScanning( PlexusConstants.SCANNING_INDEX ).setAutoWiring( true )
            .setJSR250Lifecycle( true ).setName( "maven" );

        Set<String> exportedArtifacts = new HashSet<>( coreEntry.getExportedArtifacts() );
        Set<String> exportedPackages = new HashSet<>( coreEntry.getExportedPackages() );
//        for ( CoreExtensionEntry extension : extensions )
//        {
//            exportedArtifacts.addAll( extension.getExportedArtifacts() );
//            exportedPackages.addAll( extension.getExportedPackages() );
//        }

        final CoreExports exports = new CoreExports( containerRealm, exportedArtifacts, exportedPackages );

        container = new DefaultPlexusContainer( cc, new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind( ILoggerFactory.class ).toInstance( slf4jLoggerFactory );
                bind( CoreExports.class ).toInstance( exports );
            }
        } );

        // NOTE: To avoid inconsistencies, we'll use the TCCL exclusively for lookups
        container.setLookupRealm( null );
        Thread.currentThread().setContextClassLoader( container.getContainerRealm() );

        container.setLoggerManager( plexusLoggerManager );

        for ( CoreExtensionEntry extension : extensions )
        {
            container.discoverComponents( extension.getClassRealm(), new SessionScopeModule( container ),
                                          new MojoExecutionScopeModule( container ) );
        }

//        container.getLoggerManager().setThresholds( cliRequest.request.getLoggingLevel() );

        eventSpyDispatcher = container.lookup( EventSpyDispatcher.class );

        maven = container.lookup( Maven.class );

        executionRequestPopulator = container.lookup( MavenExecutionRequestPopulator.class );

        modelProcessor = createModelProcessor( container );

        configurationProcessors = container.lookupMap( ConfigurationProcessor.class );

        toolchainsBuilder = container.lookup( ToolchainsBuilder.class );

        dispatcher = (DefaultSecDispatcher) container.lookup( SecDispatcher.class, "maven" );

    }

    //
    // This should probably be a separate tool and not be baked into Maven.
    //
    private void encryption( CliRequest cliRequest )
        throws Exception
    {
        if ( cliRequest.commandLine.hasOption( CLIManager.ENCRYPT_MASTER_PASSWORD ) )
        {
            throw new UnsupportedOperationException( "Unsupported option: " + CLIManager.ENCRYPT_MASTER_PASSWORD );
        }
        else if ( cliRequest.commandLine.hasOption( CLIManager.ENCRYPT_PASSWORD ) )
        {
            throw new UnsupportedOperationException( "Unsupported option: " + CLIManager.ENCRYPT_PASSWORD );
        }
    }

    private void repository( CliRequest cliRequest )
        throws Exception
    {
        if ( cliRequest.commandLine.hasOption( CLIManager.LEGACY_LOCAL_REPOSITORY ) || Boolean.getBoolean(
            "maven.legacyLocalRepo" ) )
        {
            cliRequest.request.setUseLegacyLocalRepository( true );
        }
    }

    private int execute(CliRequest cliRequest)
        throws MavenExecutionRequestPopulationException
    {
        commands(cliRequest);

        MavenExecutionRequest request = executionRequestPopulator.populateDefaults( cliRequest.request );

        eventSpyDispatcher.onEvent( request );

        MavenExecutionResult result = maven.execute( request );

        eventSpyDispatcher.onEvent( result );

        if ( result.hasExceptions() )
        {
            ExceptionHandler handler = new DefaultExceptionHandler();

            Map<String, String> references = new LinkedHashMap<>();

            MavenProject project = null;

            for ( Throwable exception : result.getExceptions() )
            {
                ExceptionSummary summary = handler.handleException( exception );

                logSummary( summary, references, "", cliRequest.showErrors );

                if ( project == null && exception instanceof LifecycleExecutionException )
                {
                    project = ( (LifecycleExecutionException) exception ).getProject();
                }
            }

            slf4jLogger.error( "" );

            if ( !cliRequest.showErrors )
            {
                slf4jLogger.error( "To see the full stack trace of the errors, re-run Maven with the {} switch.",
                        buffer().strong( "-e" ) );
            }
            if ( !slf4jLogger.isDebugEnabled() )
            {
                slf4jLogger.error( "Re-run Maven using the {} switch to enable full debug logging.",
                        buffer().strong( "-X" ) );
            }

            if ( !references.isEmpty() )
            {
                slf4jLogger.error( "" );
                slf4jLogger.error( "For more information about the errors and possible solutions"
                                       + ", please read the following articles:" );

                for ( Entry<String, String> entry : references.entrySet() )
                {
                    slf4jLogger.error( "{} {}", buffer().strong( entry.getValue() ), entry.getKey() );
                }
            }

            if ( project != null && !project.equals( result.getTopologicallySortedProjects().get( 0 ) ) )
            {
                slf4jLogger.error( "" );
                slf4jLogger.error( "After correcting the problems, you can resume the build with the command" );
                slf4jLogger.error( buffer().a( "  " ).strong( "mvn <args> -rf "
                    + getResumeFrom( result.getTopologicallySortedProjects(), project ) ).toString() );
            }

            if ( MavenExecutionRequest.REACTOR_FAIL_NEVER.equals( cliRequest.request.getReactorFailureBehavior() ) )
            {
                slf4jLogger.info( "Build failures were ignored." );

                return 0;
            }
            else
            {
                return 1;
            }
        }
        else
        {
            return 0;
        }
    }

    /**
     * A helper method to determine the value to resume the build with {@code -rf} taking into account the
     * edge case where multiple modules in the reactor have the same artifactId.
     * <p>
     * {@code -rf :artifactId} will pick up the first module which matches, but when multiple modules in the
     * reactor have the same artifactId, effective failed module might be later in build reactor.
     * This means that developer will either have to type groupId or wait for build execution of all modules
     * which were fine, but they are still before one which reported errors.
     * <p>Then the returned value is {@code groupId:artifactId} when there is a name clash and
     * {@code :artifactId} if there is no conflict.
     *
     * @param mavenProjects Maven projects which are part of build execution.
     * @param failedProject Project which has failed.
     * @return Value for -rf flag to resume build exactly from place where it failed ({@code :artifactId} in
     *    general and {@code groupId:artifactId} when there is a name clash).
     */
    private String getResumeFrom(List<MavenProject> mavenProjects, MavenProject failedProject )
    {
        for ( MavenProject buildProject : mavenProjects )
        {
            if ( failedProject.getArtifactId().equals( buildProject.getArtifactId() ) && !failedProject.equals(
                    buildProject ) )
            {
                return failedProject.getGroupId() + ":" + failedProject.getArtifactId();
            }
        }
        return ":" + failedProject.getArtifactId();
    }

    private void logSummary(ExceptionSummary summary, Map<String, String> references, String indent,
                            boolean showErrors )
    {
        String referenceKey = "";

        if ( StringUtils.isNotEmpty( summary.getReference() ) )
        {
            referenceKey = references.get( summary.getReference() );
            if ( referenceKey == null )
            {
                referenceKey = "[Help " + ( references.size() + 1 ) + "]";
                references.put( summary.getReference(), referenceKey );
            }
        }

        String msg = summary.getMessage();

        if ( StringUtils.isNotEmpty( referenceKey ) )
        {
            if ( msg.indexOf( '\n' ) < 0 )
            {
                msg += " -> " + buffer().strong( referenceKey );
            }
            else
            {
                msg += "\n-> " + buffer().strong( referenceKey );
            }
        }

        String[] lines = msg.split( "(\r\n)|(\r)|(\n)" );
        String currentColor = "";

        for ( int i = 0; i < lines.length; i++ )
        {
            // add eventual current color inherited from previous line
            String line = currentColor + lines[i];

            // look for last ANSI escape sequence to check if nextColor
            Matcher matcher = LAST_ANSI_SEQUENCE.matcher( line );
            String nextColor = "";
            if ( matcher.find() )
            {
                nextColor = matcher.group( 1 );
                if ( ANSI_RESET.equals( nextColor ) )
                {
                    // last ANSI escape code is reset: no next color
                    nextColor = "";
                }
            }

            // effective line, with indent and reset if end is colored
            line = indent + line + ( "".equals( nextColor ) ? "" : ANSI_RESET );

            if ( ( i == lines.length - 1 ) && ( showErrors
                || ( summary.getException() instanceof InternalErrorException ) ) )
            {
                slf4jLogger.error( line, summary.getException() );
            }
            else
            {
                slf4jLogger.error( line );
            }

            currentColor = nextColor;
        }

        indent += "  ";

        for ( ExceptionSummary child : summary.getChildren() )
        {
            logSummary( child, references, indent, showErrors );
        }
    }

    private static final Pattern LAST_ANSI_SEQUENCE = Pattern.compile( "(\u001B\\[[;\\d]*[ -/]*[@-~])[^\u001B]*$" );

    private static final String ANSI_RESET = "\u001B\u005Bm";

    private void configure(CliRequest cliRequest)
        throws Exception
    {
        //
        // This is not ideal but there are events specifically for configuration from the CLI which I don't
        // believe are really valid but there are ITs which assert the right events are published so this
        // needs to be supported so the EventSpyDispatcher needs to be put in the CliRequest so that
        // it can be accessed by configuration processors.
        //
        cliRequest.request.setEventSpyDispatcher( eventSpyDispatcher );

        Map<String, Object> data = new HashMap<>();
        data.put( "plexus", container );
        data.put( "workingDirectory", cliRequest.workingDirectory );
        data.put( "systemProperties", cliRequest.systemProperties );
        data.put( "userProperties", cliRequest.userProperties );
        data.put( "versionProperties", CLIReportingUtils.getBuildProperties() );
        eventSpyDispatcher.init( () -> data );

        //
        // We expect at most 2 implementations to be available. The SettingsXmlConfigurationProcessor implementation
        // is always available in the core and likely always will be, but we may have another ConfigurationProcessor
        // present supplied by the user. The rule is that we only allow the execution of one ConfigurationProcessor.
        // If there is more than one then we execute the one supplied by the user, otherwise we execute the
        // the default SettingsXmlConfigurationProcessor.
        //
        int userSuppliedConfigurationProcessorCount = configurationProcessors.size() - 1;

        if ( userSuppliedConfigurationProcessorCount == 0 )
        {
            //
            // Our settings.xml source is historically how we have configured Maven from the CLI so we are going to
            // have to honour its existence forever. So let's run it.
            //
            configurationProcessors.get( SettingsXmlConfigurationProcessor.HINT ).process( cliRequest );
        }
        else if ( userSuppliedConfigurationProcessorCount == 1 )
        {
            //
            // Run the user supplied ConfigurationProcessor
            //
            for ( Entry<String, ConfigurationProcessor> entry : configurationProcessors.entrySet() )
            {
                String hint = entry.getKey();
                if ( !hint.equals( SettingsXmlConfigurationProcessor.HINT ) )
                {
                    ConfigurationProcessor configurationProcessor = entry.getValue();
                    configurationProcessor.process( cliRequest );
                }
            }
        }
        else if ( userSuppliedConfigurationProcessorCount > 1 )
        {
            //
            // There are too many ConfigurationProcessors so we don't know which one to run so report the error.
            //
            StringBuilder sb = new StringBuilder(
                String.format( "\nThere can only be one user supplied ConfigurationProcessor, there are %s:\n\n",
                               userSuppliedConfigurationProcessorCount ) );
            for ( Entry<String, ConfigurationProcessor> entry : configurationProcessors.entrySet() )
            {
                String hint = entry.getKey();
                if ( !hint.equals( SettingsXmlConfigurationProcessor.HINT ) )
                {
                    ConfigurationProcessor configurationProcessor = entry.getValue();
                    sb.append( String.format( "%s\n", configurationProcessor.getClass().getName() ) );
                }
            }
            sb.append( "\n" );
            throw new Exception( sb.toString() );
        }
    }

    void toolchains( CliRequest cliRequest )
        throws Exception
    {
        File userToolchainsFile;

        if ( cliRequest.commandLine.hasOption( CLIManager.ALTERNATE_USER_TOOLCHAINS ) )
        {
            userToolchainsFile =
                new File( cliRequest.commandLine.getOptionValue( CLIManager.ALTERNATE_USER_TOOLCHAINS ) );
            userToolchainsFile = resolveFile( userToolchainsFile, cliRequest.workingDirectory );

            if ( !userToolchainsFile.isFile() )
            {
                throw new FileNotFoundException(
                    "The specified user toolchains file does not exist: " + userToolchainsFile );
            }
        }
        else
        {
            userToolchainsFile = DEFAULT_USER_TOOLCHAINS_FILE;
        }

        File globalToolchainsFile;

        if ( cliRequest.commandLine.hasOption( CLIManager.ALTERNATE_GLOBAL_TOOLCHAINS ) )
        {
            globalToolchainsFile =
                new File( cliRequest.commandLine.getOptionValue( CLIManager.ALTERNATE_GLOBAL_TOOLCHAINS ) );
            globalToolchainsFile = resolveFile( globalToolchainsFile, cliRequest.workingDirectory );

            if ( !globalToolchainsFile.isFile() )
            {
                throw new FileNotFoundException(
                    "The specified global toolchains file does not exist: " + globalToolchainsFile );
            }
        }
        else
        {
            globalToolchainsFile = DEFAULT_GLOBAL_TOOLCHAINS_FILE;
        }

        cliRequest.request.setGlobalToolchainsFile( globalToolchainsFile );
        cliRequest.request.setUserToolchainsFile( userToolchainsFile );

        DefaultToolchainsBuildingRequest toolchainsRequest = new DefaultToolchainsBuildingRequest();
        if ( globalToolchainsFile.isFile() )
        {
            toolchainsRequest.setGlobalToolchainsSource( new FileSource( globalToolchainsFile ) );
        }
        if ( userToolchainsFile.isFile() )
        {
            toolchainsRequest.setUserToolchainsSource( new FileSource( userToolchainsFile ) );
        }

        eventSpyDispatcher.onEvent( toolchainsRequest );

        slf4jLogger.debug( "Reading global toolchains from {}",
                getLocation( toolchainsRequest.getGlobalToolchainsSource(), globalToolchainsFile ) );
        slf4jLogger.debug( "Reading user toolchains from {}",
                getLocation( toolchainsRequest.getUserToolchainsSource(), userToolchainsFile ) );

        ToolchainsBuildingResult toolchainsResult = toolchainsBuilder.build( toolchainsRequest );

        eventSpyDispatcher.onEvent( toolchainsResult );

        executionRequestPopulator.populateFromToolchains( cliRequest.request,
                                                          toolchainsResult.getEffectiveToolchains() );

        if ( !toolchainsResult.getProblems().isEmpty() && slf4jLogger.isWarnEnabled() )
        {
            slf4jLogger.warn( "" );
            slf4jLogger.warn( "Some problems were encountered while building the effective toolchains" );

            for ( Problem problem : toolchainsResult.getProblems() )
            {
                slf4jLogger.warn( "{} @ {}", problem.getMessage(), problem.getLocation() );
            }

            slf4jLogger.warn( "" );
        }
    }

    private Object getLocation(Source source, File defaultLocation )
    {
        if ( source != null )
        {
            return source.getLocation();
        }
        return defaultLocation;
    }

    private void populateRequest( CliRequest cliRequest )
    {
        MavenExecutionRequest request = cliRequest.request;
        CommandLine commandLine = cliRequest.commandLine;
        String workingDirectory = cliRequest.workingDirectory;
        boolean quiet = cliRequest.quiet;
        boolean showErrors = cliRequest.showErrors;

        String[] deprecatedOptions = { "up", "npu", "cpu", "npr" };
        for ( String deprecatedOption : deprecatedOptions )
        {
            if ( commandLine.hasOption( deprecatedOption ) )
            {
                slf4jLogger.warn( "Command line option -{} is deprecated and will be removed in future Maven versions.",
                        deprecatedOption );
            }
        }

        // ----------------------------------------------------------------------
        // Now that we have everything that we need we will fire up plexus and
        // bring the maven component to life for use.
        // ----------------------------------------------------------------------

        if ( commandLine.hasOption( CLIManager.BATCH_MODE ) )
        {
            request.setInteractiveMode( false );
        }

        boolean noSnapshotUpdates = false;
        if ( commandLine.hasOption( CLIManager.SUPRESS_SNAPSHOT_UPDATES ) )
        {
            noSnapshotUpdates = true;
        }

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        List<String> goals = commandLine.getArgList();

        boolean recursive = true;

        // this is the default behavior.
        String reactorFailureBehaviour = MavenExecutionRequest.REACTOR_FAIL_FAST;

        if ( commandLine.hasOption( CLIManager.NON_RECURSIVE ) )
        {
            recursive = false;
        }

        if ( commandLine.hasOption( CLIManager.FAIL_FAST ) )
        {
            reactorFailureBehaviour = MavenExecutionRequest.REACTOR_FAIL_FAST;
        }
        else if ( commandLine.hasOption( CLIManager.FAIL_AT_END ) )
        {
            reactorFailureBehaviour = MavenExecutionRequest.REACTOR_FAIL_AT_END;
        }
        else if ( commandLine.hasOption( CLIManager.FAIL_NEVER ) )
        {
            reactorFailureBehaviour = MavenExecutionRequest.REACTOR_FAIL_NEVER;
        }

        if ( commandLine.hasOption( CLIManager.OFFLINE ) )
        {
            request.setOffline( true );
        }

        boolean updateSnapshots = false;

        if ( commandLine.hasOption( CLIManager.UPDATE_SNAPSHOTS ) )
        {
            updateSnapshots = true;
        }

        String globalChecksumPolicy = null;

        if ( commandLine.hasOption( CLIManager.CHECKSUM_FAILURE_POLICY ) )
        {
            globalChecksumPolicy = MavenExecutionRequest.CHECKSUM_POLICY_FAIL;
        }
        else if ( commandLine.hasOption( CLIManager.CHECKSUM_WARNING_POLICY ) )
        {
            globalChecksumPolicy = MavenExecutionRequest.CHECKSUM_POLICY_WARN;
        }

        File baseDirectory = new File( workingDirectory, "" ).getAbsoluteFile();

        // ----------------------------------------------------------------------
        // Profile Activation
        // ----------------------------------------------------------------------

        List<String> activeProfiles = new ArrayList<>();

        List<String> inactiveProfiles = new ArrayList<>();

        if ( commandLine.hasOption( CLIManager.ACTIVATE_PROFILES ) )
        {
            String[] profileOptionValues = commandLine.getOptionValues( CLIManager.ACTIVATE_PROFILES );
            if ( profileOptionValues != null )
            {
                for ( String profileOptionValue : profileOptionValues )
                {
                    StringTokenizer profileTokens = new StringTokenizer( profileOptionValue, "," );

                    while ( profileTokens.hasMoreTokens() )
                    {
                        String profileAction = profileTokens.nextToken().trim();

                        if ( profileAction.startsWith( "-" ) || profileAction.startsWith( "!" ) )
                        {
                            inactiveProfiles.add( profileAction.substring( 1 ) );
                        }
                        else if ( profileAction.startsWith( "+" ) )
                        {
                            activeProfiles.add( profileAction.substring( 1 ) );
                        }
                        else
                        {
                            activeProfiles.add( profileAction );
                        }
                    }
                }
            }
        }

        TransferListener transferListener;

        if ( quiet || cliRequest.commandLine.hasOption( CLIManager.NO_TRANSFER_PROGRESS ) )
        {
            transferListener = new QuietMavenTransferListener();
        }
        else if ( request.isInteractiveMode() && !cliRequest.commandLine.hasOption( CLIManager.LOG_FILE ) )
        {
            //
            // If we're logging to a file then we don't want the console transfer listener as it will spew
            // download progress all over the place
            //
            transferListener = getConsoleTransferListener( cliRequest.commandLine.hasOption( CLIManager.DEBUG ) );
        }
        else
        {
            transferListener = getBatchTransferListener();
        }

        ExecutionListener executionListener = new ExecutionEventLogger();
        if ( eventSpyDispatcher != null )
        {
            executionListener = eventSpyDispatcher.chainListener( executionListener );
        }

        String alternatePomFile = null;
        if ( commandLine.hasOption( CLIManager.ALTERNATE_POM_FILE ) )
        {
            alternatePomFile = commandLine.getOptionValue( CLIManager.ALTERNATE_POM_FILE );
        }

        request.setBaseDirectory( baseDirectory ).setGoals( goals ).setSystemProperties(
            cliRequest.systemProperties ).setUserProperties( cliRequest.userProperties ).setReactorFailureBehavior(
            reactorFailureBehaviour ) // default: fail fast
            .setRecursive( recursive ) // default: true
            .setShowErrors( showErrors ) // default: false
            .addActiveProfiles( activeProfiles ) // optional
            .addInactiveProfiles( inactiveProfiles ) // optional
            .setExecutionListener( executionListener ).setTransferListener(
            transferListener ) // default: batch mode which goes along with interactive
            .setUpdateSnapshots( updateSnapshots ) // default: false
            .setNoSnapshotUpdates( noSnapshotUpdates ) // default: false
            .setGlobalChecksumPolicy( globalChecksumPolicy ) // default: warn
            .setMultiModuleProjectDirectory( cliRequest.getMultiModuleProjectDirectory() );

        if ( alternatePomFile != null )
        {
            File pom = resolveFile( new File( alternatePomFile ), workingDirectory );
            if ( pom.isDirectory() )
            {
                pom = new File( pom, "pom.xml" );
            }

            request.setPom( pom );
        }
        else if ( modelProcessor != null )
        {
            File pom = modelProcessor.locatePom( baseDirectory );

            if ( pom.isFile() )
            {
                request.setPom( pom );
            }
        }

        if ( ( request.getPom() != null ) && ( request.getPom().getParentFile() != null ) )
        {
            request.setBaseDirectory( request.getPom().getParentFile() );
        }

        if ( commandLine.hasOption( CLIManager.RESUME_FROM ) )
        {
            request.setResumeFrom( commandLine.getOptionValue( CLIManager.RESUME_FROM ) );
        }

        if ( commandLine.hasOption( CLIManager.PROJECT_LIST ) )
        {
            String[] projectOptionValues = commandLine.getOptionValues( CLIManager.PROJECT_LIST );

            List<String> inclProjects = new ArrayList<>();
            List<String> exclProjects = new ArrayList<>();

            if ( projectOptionValues != null )
            {
                for ( String projectOptionValue : projectOptionValues )
                {
                    StringTokenizer projectTokens = new StringTokenizer( projectOptionValue, "," );

                    while ( projectTokens.hasMoreTokens() )
                    {
                        String projectAction = projectTokens.nextToken().trim();

                        if ( projectAction.startsWith( "-" ) || projectAction.startsWith( "!" ) )
                        {
                            exclProjects.add( projectAction.substring( 1 ) );
                        }
                        else if ( projectAction.startsWith( "+" ) )
                        {
                            inclProjects.add( projectAction.substring( 1 ) );
                        }
                        else
                        {
                            inclProjects.add( projectAction );
                        }
                    }
                }
            }

            request.setSelectedProjects( inclProjects );
            request.setExcludedProjects( exclProjects );
        }

        if ( commandLine.hasOption( CLIManager.ALSO_MAKE ) && !commandLine.hasOption(
            CLIManager.ALSO_MAKE_DEPENDENTS ) )
        {
            request.setMakeBehavior( MavenExecutionRequest.REACTOR_MAKE_UPSTREAM );
        }
        else if ( !commandLine.hasOption( CLIManager.ALSO_MAKE ) && commandLine.hasOption(
            CLIManager.ALSO_MAKE_DEPENDENTS ) )
        {
            request.setMakeBehavior( MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM );
        }
        else if ( commandLine.hasOption( CLIManager.ALSO_MAKE ) && commandLine.hasOption(
            CLIManager.ALSO_MAKE_DEPENDENTS ) )
        {
            request.setMakeBehavior( MavenExecutionRequest.REACTOR_MAKE_BOTH );
        }

        String localRepoProperty = request.getUserProperties().getProperty( MavenCli.LOCAL_REPO_PROPERTY );

        if ( localRepoProperty == null )
        {
            localRepoProperty = request.getSystemProperties().getProperty( MavenCli.LOCAL_REPO_PROPERTY );
        }

        if ( localRepoProperty != null )
        {
            request.setLocalRepositoryPath( localRepoProperty );
        }

        request.setCacheNotFound( true );
        request.setCacheTransferError( false );

        //
        // Builder, concurrency and parallelism
        //
        // We preserve the existing methods for builder selection which is to look for various inputs in the threading
        // configuration. We don't have an easy way to allow a pluggable builder to provide its own configuration
        // parameters but this is sufficient for now. Ultimately we want components like Builders to provide a way to
        // extend the command line to accept its own configuration parameters.
        //
        final String threadConfiguration = commandLine.hasOption( CLIManager.THREADS )
            ? commandLine.getOptionValue( CLIManager.THREADS )
            : null;

        if ( threadConfiguration != null )
        {
            //
            // Default to the standard multithreaded builder
            //
            request.setBuilderId( "multithreaded" );

            if ( threadConfiguration.contains( "C" ) )
            {
                request.setDegreeOfConcurrency( calculateDegreeOfConcurrencyWithCoreMultiplier( threadConfiguration ) );
            }
            else
            {
                request.setDegreeOfConcurrency( Integer.valueOf( threadConfiguration ) );
            }
        }

        //
        // Allow the builder to be overridden by the user if requested. The builders are now pluggable.
        //
        if ( commandLine.hasOption( CLIManager.BUILDER ) )
        {
            request.setBuilderId( commandLine.getOptionValue( CLIManager.BUILDER ) );
        }
    }

    int calculateDegreeOfConcurrencyWithCoreMultiplier( String threadConfiguration )
    {
        int procs = Runtime.getRuntime().availableProcessors();
        return (int) ( Float.valueOf( threadConfiguration.replace( "C", "" ) ) * procs );
    }

    static File resolveFile(File file, String workingDirectory )
    {
        if ( file == null )
        {
            return null;
        }
        else if ( file.isAbsolute() )
        {
            return file;
        }
        else if ( file.getPath().startsWith( File.separator ) )
        {
            // drive-relative Windows path
            return file.getAbsoluteFile();
        }
        else
        {
            return new File( workingDirectory, file.getPath() ).getAbsoluteFile();
        }
    }

    // ----------------------------------------------------------------------
    // System properties handling
    // ----------------------------------------------------------------------

    static void populateProperties(CommandLine commandLine, Properties systemProperties, Properties userProperties )
    {
        EnvironmentUtils.addEnvVars( systemProperties );

        // ----------------------------------------------------------------------
        // Options that are set on the command line become system properties
        // and therefore are set in the session properties. System properties
        // are most dominant.
        // ----------------------------------------------------------------------

        if ( commandLine.hasOption( CLIManager.SET_SYSTEM_PROPERTY ) )
        {
            String[] defStrs = commandLine.getOptionValues( CLIManager.SET_SYSTEM_PROPERTY );

            if ( defStrs != null )
            {
                for ( String defStr : defStrs )
                {
                    setCliProperty( defStr, userProperties );
                }
            }
        }

        SystemProperties.addSystemProperties( systemProperties );

        // ----------------------------------------------------------------------
        // Properties containing info about the currently running version of Maven
        // These override any corresponding properties set on the command line
        // ----------------------------------------------------------------------

        Properties buildProperties = CLIReportingUtils.getBuildProperties();

        String mavenVersion = buildProperties.getProperty( CLIReportingUtils.BUILD_VERSION_PROPERTY );
        systemProperties.setProperty( "maven.version", mavenVersion );

        String mavenBuildVersion = CLIReportingUtils.createMavenVersionString( buildProperties );
        systemProperties.setProperty( "maven.build.version", mavenBuildVersion );
    }

    private static void setCliProperty(String property, Properties properties )
    {
        String name;

        String value;

        int i = property.indexOf( '=' );

        if ( i <= 0 )
        {
            name = property.trim();

            value = "true";
        }
        else
        {
            name = property.substring( 0, i ).trim();

            value = property.substring( i + 1 );
        }

        properties.setProperty( name, value );

        // ----------------------------------------------------------------------
        // I'm leaving the setting of system properties here as not to break
        // the SystemPropertyProfileActivator. This won't harm embedding. jvz.
        // ----------------------------------------------------------------------

        System.setProperty( name, value );
    }

    static class ExitException
        extends Exception
    {
        static final long serialVersionUID = 1L;
        int exitCode;

        ExitException( int exitCode )
        {
            this.exitCode = exitCode;
        }
    }

    //
    // Customizations available via the CLI
    //

    protected TransferListener getConsoleTransferListener( boolean printResourceNames )
    {
        return new ConsoleMavenTransferListener( System.out, printResourceNames );
    }

    protected TransferListener getBatchTransferListener()
    {
        return new Slf4jMavenTransferListener();
    }

    protected ModelProcessor createModelProcessor( PlexusContainer container )
        throws ComponentLookupException
    {
        return container.lookup( ModelProcessor.class );
    }
}
