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

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;

public class LoggingExecutionListener implements ExecutionListener {

    private final ExecutionListener delegate;
    private final AbstractLoggingSpy loggingSpy;

    public LoggingExecutionListener(ExecutionListener delegate, AbstractLoggingSpy loggingSpy) {
        this.delegate = delegate;
        this.loggingSpy = loggingSpy;
    }

    @Override
    public void projectDiscoveryStarted(ExecutionEvent event) {
        setMdc(event);
        delegate.projectDiscoveryStarted(event);
    }

    @Override
    public void sessionStarted(ExecutionEvent event) {
        setMdc(event);
        loggingSpy.sessionStarted(event);
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
        loggingSpy.projectStarted(event);
        delegate.projectStarted(event);
    }

    @Override
    public void projectSucceeded(ExecutionEvent event) {
        setMdc(event);
        delegate.projectSucceeded(event);
        loggingSpy.projectFinished(event);
    }

    @Override
    public void projectFailed(ExecutionEvent event) {
        setMdc(event);
        delegate.projectFailed(event);
        loggingSpy.projectFinished(event);
    }

    @Override
    public void projectSkipped(ExecutionEvent event) {
        setMdc(event);
        delegate.projectSkipped(event);
        loggingSpy.projectFinished(event);
    }

    @Override
    public void mojoStarted(ExecutionEvent event) {
        setMdc(event);
        loggingSpy.mojoStarted(event);
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
    }

    @Override
    public void forkSucceeded(ExecutionEvent event) {
        setMdc(event);
        delegate.forkSucceeded(event);
    }

    @Override
    public void forkFailed(ExecutionEvent event) {
        setMdc(event);
        delegate.forkFailed(event);
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
    }

    @Override
    public void forkedProjectFailed(ExecutionEvent event) {
        setMdc(event);
        delegate.forkedProjectFailed(event);
    }

    private void setMdc(ExecutionEvent event) {
        if (event.getProject() != null) {
            ProjectBuildLogAppender.setProjectId(event.getProject().getArtifactId());
        }
    }
}
