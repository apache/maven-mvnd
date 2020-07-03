/*
 * Copyright (c) 2015-2016 salesforce.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.fuse.mvnd.logging.logback;


import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.util.StatusPrinter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.Lifecycle;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.jboss.fuse.mvnd.logging.internal.SLF4J;

/**
 * This Maven-specific appender outputs project build log messages to per-project build.log files
 * <code>${project.build.directory}/build.log</code>.
 * <p>
 * Typical logback.xml configuration file
 *
 * <pre>
 * {@code
 * <configuration>
 * <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
 * <encoder>
 * <pattern>[%level] %msg%n</pattern>
 * </encoder>
 * </appender>
 *
 * <appender name="PROJECT" class="io.takari.maven.logback.ProjectBuildLogAppender">
 * <pattern>%date %level %msg%n</pattern>
 * </appender>
 *
 * <root level="info">
 * <appender-ref ref="STDOUT" />
 * <appender-ref ref="PROJECT" />
 * </root>
 * </configuration>
 * }
 * </pre>
 *
 * File origin: https://github.com/takari/concurrent-build-logger/blob/concurrent-build-logger-0.1.0/src/main/java/io/takari/maven/logback/ProjectBuildLogAppender.java
 */
public class ProjectBuildLogAppender extends AppenderBase<ILoggingEvent>
        implements SLF4J.LifecycleListener {

    private final Cache<String, FileAppender<ILoggingEvent>> appenders = CacheBuilder.newBuilder() //
            .removalListener(new RemovalListener<String, FileAppender<ILoggingEvent>>() {
                @Override
                public void onRemoval(
                        RemovalNotification<String, FileAppender<ILoggingEvent>> notification) {
                    if (notification.getValue() != null) {
                        notification.getValue().stop();
                    }
                }
            }) //
            .build();
    private final Multimap<String, ILoggingEvent> queues = ArrayListMultimap.create();
    private String fileName;
    private String pattern;

    @Override
    public void start() {
        if (fileName == null) {
            addError("\"File\" property not set for appender named [" + name + "].");
            return;
        }

        if (pattern == null) {
            addError("\"Pattern\" property not set for appender named [" + name + "].");
            return;
        }

        super.start();

        SLF4J.addListener(this);
    }

    @Override
    protected void append(ILoggingEvent event) {
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc == null) {
            return;
        }

        String projectId = mdc.get(SLF4J.KEY_PROJECT_ID);
        if (projectId == null) {
            return;
        }

        FileAppender<ILoggingEvent> appender = appenders.getIfPresent(projectId);

        if (appender != null) {
            appender.doAppend(event);
        } else {
            synchronized (queues) {
                queues.put(projectId, LoggingEventVO.build(event));
            }
            return;
        }
    }

    private FileAppender<ILoggingEvent> getOrCreateAppender(String projectId, String projectLogdir) {
        long timestamp = System.currentTimeMillis();

        Callable<? extends FileAppender<ILoggingEvent>> valueLoader = () -> {
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern(pattern);
            encoder.start();

            FileAppender<ILoggingEvent> appender = new FileAppender<>();
            appender.setContext(context);
            appender.setName(projectId);
            appender.setAppend(false);
            appender.setEncoder(encoder);
            appender.setFile(getLogfile(projectLogdir).getAbsolutePath());
            appender.start();

            if (!appender.isStarted()) {
                StatusPrinter.printInCaseOfErrorsOrWarnings(context, timestamp);
            }

            return appender;
        };

        try {
            return appenders.get(projectId, valueLoader);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause()); // can't really happen
        }
    }

    private void cleanOldLogFiles(MavenSession session) {
        for (MavenProject project : session.getAllProjects()) {
            File logfile = getLogfile(SLF4J.getLogdir(project));
            logfile.delete();
        }
    }

    private File getLogfile(String logdir) {
        return new File(logdir, fileName);
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public void setFile(String fileName) {
        this.fileName = fileName;
    }

    public void stop() {
        SLF4J.removeListener(this);
        appenders.invalidateAll();
        appenders.cleanUp();
        super.stop();
    }

    @Override
    public void onSessionStart(MavenSession session) {
        cleanOldLogFiles(session);
    }

    @Override
    public void onProjectBuildFinish(MavenProject project) {
        synchronized (queues) {
            queues.removeAll(project.getId());
        }
        appenders.invalidate(project.getId());
    }

    @Override
    public void onMojoExecutionStart(MavenProject project, Lifecycle lifecycle,
                                     MojoExecution execution) {
        if (lifecycle == null || "clean".equals(lifecycle.getId())) {
            return;
        }

        String projectId = project.getId();
        String projectLogdir = SLF4J.getLogdir(project);

        FileAppender<ILoggingEvent> appender = getOrCreateAppender(projectId, projectLogdir);

        Collection<ILoggingEvent> events;
        synchronized (queues) {
            events = queues.removeAll(projectId);
        }

        events.forEach(e -> appender.doAppend(e));
    }
}
