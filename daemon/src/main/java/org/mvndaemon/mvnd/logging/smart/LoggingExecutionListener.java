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
package org.mvndaemon.mvnd.logging.smart;

import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectExecutionEvent;
import org.apache.maven.execution.ProjectExecutionListener;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.internal.ReactorBuildStatus;
import org.eclipse.sisu.Typed;

@Singleton
@Named
@Typed({ LoggingExecutionListener.class, ExecutionListener.class, ProjectExecutionListener.class })
public class LoggingExecutionListener implements ExecutionListener, ProjectExecutionListener {

    private ExecutionListener delegate;
    private BuildEventListener buildEventListener;

    public void init(ExecutionListener delegate, BuildEventListener buildEventListener) {
        this.delegate = delegate;
        this.buildEventListener = buildEventListener;
    }

    @Override
    public void beforeProjectExecution(ProjectExecutionEvent projectExecutionEvent) throws LifecycleExecutionException {
    }

    @Override
    public void beforeProjectLifecycleExecution(ProjectExecutionEvent projectExecutionEvent)
            throws LifecycleExecutionException {
    }

    @Override
    public void afterProjectExecutionSuccess(ProjectExecutionEvent projectExecutionEvent) throws LifecycleExecutionException {
    }

    @Override
    public void afterProjectExecutionFailure(ProjectExecutionEvent projectExecutionEvent) {
        MavenSession session = projectExecutionEvent.getSession();
        ReactorBuildStatus status = (ReactorBuildStatus) session.getRepositorySession().getData().get(ReactorBuildStatus.class);
        Throwable cause = projectExecutionEvent.getCause();
        buildEventListener.executionFailure(projectExecutionEvent.getProject().getArtifactId(),
                status.isHalted(), cause != null ? cause.toString() : null);
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
        delegate.sessionEnded(event);
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
