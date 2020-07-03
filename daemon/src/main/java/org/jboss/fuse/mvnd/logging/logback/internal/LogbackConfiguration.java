/*
 * Copyright (c) 2015-2016 salesforce.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.fuse.mvnd.logging.logback.internal;

import java.net.URL;

import javax.inject.Named;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.jboss.fuse.mvnd.logging.internal.SLF4JPrintStream;
import org.slf4j.LoggerFactory;

/**
 * File origin: https://github.com/takari/concurrent-build-logger/blob/concurrent-build-logger-0.1.0/src/main/java/io/takari/maven/logback/internal/LogbackConfiguration.java
 */
@Named
public class LogbackConfiguration extends AbstractMavenLifecycleParticipant {

    /**
     * Logback configuration file name classifier, as in logback[-classifier].xml. The configuration
     * files are loaded from classpath, but as of Maven 3.3.9 $M2_HOME/conf/logging is conventional
     * location.
     */
    public static final String PROP_LOGGING = "maven.logging";

    private static Level toLogbackLevel(int level) {
        switch (level) {
            case MavenExecutionRequest.LOGGING_LEVEL_DISABLED:
                return Level.OFF;
            case MavenExecutionRequest.LOGGING_LEVEL_DEBUG:
                return Level.DEBUG;
            case MavenExecutionRequest.LOGGING_LEVEL_INFO:
                return Level.INFO;
            case MavenExecutionRequest.LOGGING_LEVEL_WARN:
                return Level.WARN;
            case MavenExecutionRequest.LOGGING_LEVEL_ERROR:
            case MavenExecutionRequest.LOGGING_LEVEL_FATAL:
            default:
                return Level.ERROR;
        }
    }

    // maven api sucks
    private static String getProperty(MavenExecutionRequest request, String property) {
        String value = request.getUserProperties().getProperty(property);
        if (value == null) {
            request.getSystemProperties().getProperty(property);
        }
        return value;
    }

    @Override
    public void afterSessionStart(MavenSession session) throws MavenExecutionException {
        // this is the earliest callback able to tell if the build is running in batch mode
        // TODO extend Maven Slf4jConfiguration API to support this usecase

        final MavenExecutionRequest request = session.getRequest();
        final String classifier = getProperty(request, PROP_LOGGING);
        final int loglevel = request.getLoggingLevel();

        if (classifier != null || loglevel != MavenExecutionRequest.LOGGING_LEVEL_INFO) {
            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

            URL url;
            if (classifier == null) {
                ContextInitializer ci = new ContextInitializer(lc);
                url = ci.findURLOfDefaultConfigurationFile(true);
            } else {
                String resourceName = "logback-" + classifier + ".xml";
                url = getClass().getClassLoader().getResource(resourceName.toString());
                if (url == null) {
                    String msg =
                            String.format("Invalid -D%s=%s property value, %s configuration file is not found",
                                    PROP_LOGGING, classifier, resourceName);
                    throw new MavenExecutionException(msg, (Throwable) null);
                }
            }

            lc.reset();
            lc.putProperty("consoleLevel", toLogbackLevel(loglevel).levelStr);

            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(lc);
            try {
                configurator.doConfigure(url);
            } catch (JoranException e) {
                // StatusPrinter will handle this, see logback documentation for details
            }
            StatusPrinter.printInCaseOfErrorsOrWarnings(lc);
        }

        // "interactive mode" means plugins can communicate with the user using console
        // release plugin asks the user to provide release information, for example
        // no need to redirect such interactions to slf4j.

        if (!request.isInteractiveMode()) {
            // funnel System out/err message to slf4j when in batch mode
            System.setOut(new SLF4JPrintStream(System.out, false));
            System.setErr(new SLF4JPrintStream(System.err, true));
        }
    }
}
