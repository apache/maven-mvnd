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
package org.mvndaemon.mvnd.logging.smart;

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.List;

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectExecutionEvent;
import org.apache.maven.execution.ProjectExecutionListener;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.internal.ReactorBuildStatus;
import org.eclipse.sisu.Typed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
@Typed({LoggingExecutionListener.class, ExecutionListener.class, ProjectExecutionListener.class})
public class LoggingExecutionListener implements ExecutionListener, ProjectExecutionListener {

    /** Binds to {@code MvndSimpleLogger} because this class lives in the Maven realm, so the summary is colorized
     *  and routed to the client through the same pipeline as any other Maven console line. */
    private static final Logger LOGGER = LoggerFactory.getLogger("org.mvndaemon.mvnd.testsummary");

    private ExecutionListener delegate;
    private BuildEventListener buildEventListener;

    public void init(ExecutionListener delegate, BuildEventListener buildEventListener) {
        this.delegate = delegate;
        this.buildEventListener = buildEventListener;
    }

    @Override
    public void beforeProjectExecution(ProjectExecutionEvent projectExecutionEvent)
            throws LifecycleExecutionException {}

    @Override
    public void beforeProjectLifecycleExecution(ProjectExecutionEvent projectExecutionEvent)
            throws LifecycleExecutionException {}

    @Override
    public void afterProjectExecutionSuccess(ProjectExecutionEvent projectExecutionEvent)
            throws LifecycleExecutionException {}

    @Override
    public void afterProjectExecutionFailure(ProjectExecutionEvent projectExecutionEvent) {
        MavenSession session = projectExecutionEvent.getSession();
        boolean halted;
        // The ReactorBuildStatus is only available if the SmartBuilder is used
        ReactorBuildStatus status =
                (ReactorBuildStatus) session.getRepositorySession().getData().get(ReactorBuildStatus.class);
        if (status != null) {
            halted = status.isHalted();
        } else {
            // assume sensible default
            Throwable t = projectExecutionEvent.getCause();
            halted = (t instanceof RuntimeException || !(t instanceof Exception))
                    || !MavenExecutionRequest.REACTOR_FAIL_NEVER.equals(session.getReactorFailureBehavior())
                            && !MavenExecutionRequest.REACTOR_FAIL_AT_END.equals(session.getReactorFailureBehavior());
        }
        Throwable cause = projectExecutionEvent.getCause();
        buildEventListener.executionFailure(
                projectExecutionEvent.getProject().getArtifactId(), halted, cause != null ? cause.toString() : null);
    }

    @Override
    public void projectDiscoveryStarted(ExecutionEvent event) {
        setMdc(event);
        delegate.projectDiscoveryStarted(event);
    }

    @Override
    public void sessionStarted(ExecutionEvent event) {
        setMdc(event);
        buildEventListener.sessionStarted(event);
        delegate.sessionStarted(event);
    }

    @Override
    public void sessionEnded(ExecutionEvent event) {
        setMdc(event);
        emitTestSummary();
        delegate.sessionEnded(event);
    }

    /**
     * Logs the collected test summary immediately before Maven's Reactor Summary/BUILD banner (emitted next by
     * {@code delegate.sessionEnded}), through this listener's own {@link #LOGGER} so coloring, the {@code [LEVEL]}
     * prefix, and {@code -q} gating all come from the normal Maven logging pipeline.
     */
    private void emitTestSummary() {
        TestBuildSummary summary = buildEventListener.getTestSummary();
        if (summary == null) {
            return;
        }
        List<TestBuildSummary.SummaryLine> lines = summary.renderLines();
        if (lines.isEmpty()) {
            return;
        }
        ProjectBuildLogAppender.setProjectId(null);
        for (TestBuildSummary.SummaryLine line : lines) {
            switch (line.level) {
                case ERROR:
                    LOGGER.error(line.text);
                    break;
                case WARNING:
                    LOGGER.warn(line.text);
                    break;
                default:
                    LOGGER.info(line.text);
                    break;
            }
        }
    }

    @Override
    public void projectStarted(ExecutionEvent event) {
        setMdc(event);
        buildEventListener.projectStarted(event.getProject().getArtifactId());
        delegate.projectStarted(event);
    }

    @Override
    public void projectSucceeded(ExecutionEvent event) {
        setMdc(event);
        delegate.projectSucceeded(event);
        buildEventListener.projectFinished(event.getProject().getArtifactId());
    }

    @Override
    public void projectFailed(ExecutionEvent event) {
        setMdc(event);
        delegate.projectFailed(event);
        buildEventListener.projectFinished(event.getProject().getArtifactId());
    }

    @Override
    public void projectSkipped(ExecutionEvent event) {
        setMdc(event);
        buildEventListener.projectStarted(event.getProject().getArtifactId());
        delegate.projectSkipped(event);
        buildEventListener.projectFinished(event.getProject().getArtifactId());
    }

    @Override
    public void mojoStarted(ExecutionEvent event) {
        setMdc(event);
        buildEventListener.mojoStarted(event);
        delegate.mojoStarted(event);
        // Folds the previous test-running mojo's snapshots into the reactor totals; a no-op for non-test mojos.
        buildEventListener.foldTestProgress(event.getProject().getArtifactId());
    }

    @Override
    public void mojoSucceeded(ExecutionEvent event) {
        setMdc(event);
        delegate.mojoSucceeded(event);
    }

    @Override
    public void mojoFailed(ExecutionEvent event) {
        setMdc(event);
        delegate.mojoFailed(event);
    }

    @Override
    public void mojoSkipped(ExecutionEvent event) {
        setMdc(event);
        delegate.mojoSkipped(event);
    }

    @Override
    public void forkStarted(ExecutionEvent event) {
        setMdc(event);
        delegate.forkStarted(event);
        ProjectBuildLogAppender.setForkingProjectId(event.getProject().getArtifactId());
    }

    @Override
    public void forkSucceeded(ExecutionEvent event) {
        delegate.forkSucceeded(event);
        ProjectBuildLogAppender.setForkingProjectId(null);
    }

    @Override
    public void forkFailed(ExecutionEvent event) {
        delegate.forkFailed(event);
        ProjectBuildLogAppender.setForkingProjectId(null);
    }

    @Override
    public void forkedProjectStarted(ExecutionEvent event) {
        setMdc(event);
        delegate.forkedProjectStarted(event);
    }

    @Override
    public void forkedProjectSucceeded(ExecutionEvent event) {
        setMdc(event);
        delegate.forkedProjectSucceeded(event);
        ProjectBuildLogAppender.setProjectId(null);
    }

    @Override
    public void forkedProjectFailed(ExecutionEvent event) {
        setMdc(event);
        delegate.forkedProjectFailed(event);
        ProjectBuildLogAppender.setProjectId(null);
    }

    private void setMdc(ExecutionEvent event) {
        if (event.getProject() != null) {
            ProjectBuildLogAppender.setProjectId(event.getProject().getArtifactId());
        }
    }
}
