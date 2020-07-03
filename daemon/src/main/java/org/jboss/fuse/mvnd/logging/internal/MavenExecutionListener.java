/*
 * Copyright (c) 2015-2016 salesforce.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.fuse.mvnd.logging.internal;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.Lifecycle;

/**
 * File origin: https://github.com/takari/concurrent-build-logger/blob/concurrent-build-logger-0.1.0/src/main/java/io/takari/maven/logging/internal/MavenExecutionListener.java
 */
@Named
public class MavenExecutionListener implements EventSpy {

    private final DefaultLifecycles lifecycles;

    @Inject
    public MavenExecutionListener(DefaultLifecycles lifecycles) {
        this.lifecycles = lifecycles;
    }

    @Override
    public void init(Context context) throws Exception {
    }

    @Override
    public void onEvent(Object event) throws Exception {
        if (event instanceof ExecutionEvent) {
            ExecutionEvent executionEvent = (ExecutionEvent) event;
            MavenSession session = executionEvent.getSession();

            switch (executionEvent.getType()) {
                case SessionStarted:
                    SLF4J.notifySessionStart(session);
                    break;
                case SessionEnded:
                    SLF4J.notifySessionFinish(session);
                    break;
                case ProjectStarted:
                    SLF4J.notifyProjectBuildStart(executionEvent.getProject());
                    break;
                case ProjectSucceeded:
                case ProjectFailed:
                case ProjectSkipped:
                    SLF4J.notifyProjectBuildFinish(executionEvent.getProject());
                    break;
                case MojoStarted:
                    SLF4J.notifyMojoExecutionStart(executionEvent.getProject(),
                            getLifecycle(executionEvent.getMojoExecution().getLifecyclePhase()),
                            executionEvent.getMojoExecution());
                    break;
                case MojoSucceeded:
                case MojoSkipped:
                case MojoFailed:
                    SLF4J.notifyMojoExecutionFinish(executionEvent.getProject(),
                            executionEvent.getMojoExecution());
                    break;
                default:
                    break;
            }
        }
    }

    private Lifecycle getLifecycle(String phase) {
        return lifecycles.getPhaseToLifecycleMap().get(phase);
    }

    @Override
    public void close() throws Exception {
    }

}
