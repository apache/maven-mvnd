/*
 * Copyright (c) 2015-2016 salesforce.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.fuse.mvnd.logging.logback;


import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.jboss.fuse.mvnd.logging.internal.SLF4J;
import org.jboss.fuse.mvnd.logging.internal.SLF4JPrintStream;

/**
 * Maven-specific console appender that buffers individual project build log messages until the end
 * of project build. The implementation guarantees that messages from one project are grouped
 * together. This is useful when using multi threaded build, where several projects are built
 * concurrently.
 *
 * File origin: https://github.com/takari/concurrent-build-logger/blob/concurrent-build-logger-0.1.0/src/main/java/io/takari/maven/logback/ProjectConsoleAppender.java
 */
public class ProjectConsoleAppender extends ch.qos.logback.core.ConsoleAppender<ILoggingEvent>
        implements SLF4J.LifecycleListener {

    private final Multimap<String, ILoggingEvent> queue = ArrayListMultimap.create();

    @Override
    protected void subAppend(ILoggingEvent event) {
        String projectId = event.getMDCPropertyMap().get(SLF4J.KEY_PROJECT_ID);
        if (projectId != null) {
            synchronized (queue) {
                queue.put(projectId, LoggingEventVO.build(event));
            }
        } else {
            privilegedAppend(event);
        }
    }

    @Override
    public void start() {
        super.start();

        SLF4J.addListener(this);
    }

    @Override
    public void stop() {
        SLF4J.removeListener(this);

        flushAll();

        super.stop();
    }

    private void flushAll() {
        ImmutableMultimap<String, ILoggingEvent> copy;
        synchronized (queue) {
            copy = ImmutableMultimap.copyOf(queue);
            queue.clear();
        }

        lock.lock();
        try {
            for (ILoggingEvent queued : copy.values()) {
                privilegedAppend(queued);
            }
        } finally {
            lock.unlock();
        }
    }

    protected void privilegedAppend(ILoggingEvent event) {
        SLF4JPrintStream.enterPrivileged();
        try {
            super.subAppend(event);
        } finally {
            SLF4JPrintStream.leavePrivileged();
        }
    }

    private void flush(String projectId) {
        ImmutableList<ILoggingEvent> events;
        synchronized (queue) {
            events = ImmutableList.copyOf(queue.get(projectId));
            queue.removeAll(projectId);
        }
        lock.lock();
        try {
            for (ILoggingEvent queued : events) {
                privilegedAppend(queued);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onSessionFinish(MavenSession session) {
        flushAll();
    }

    @Override
    public void onProjectBuildFinish(MavenProject project) {
        flush(project.getId());
    }
}
