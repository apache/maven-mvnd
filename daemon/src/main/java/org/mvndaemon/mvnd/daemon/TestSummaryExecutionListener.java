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
package org.mvndaemon.mvnd.daemon;

import java.util.List;

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.mvndaemon.mvnd.logging.smart.TestBuildSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorates the {@link ExecutionListener} chain Maven 4 builds in {@code MavenInvoker.determineExecutionListener}
 * (wired up by {@link DaemonMavenInvoker#determineExecutionListener}) to fold per-project test-progress snapshots
 * into the reactor-wide {@link TestBuildSummary} and log it through this class's own SLF4J logger immediately
 * before {@code delegate.sessionEnded} prints the Reactor Summary. Routing through SLF4J means ANSI coloring and
 * {@code -q} log-level gating come from the normal Maven logging pipeline instead of being reimplemented
 * client-side, matching how every other Maven console line is rendered.
 */
public class TestSummaryExecutionListener implements ExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("org.mvndaemon.mvnd.testsummary");

    private final ExecutionListener delegate;
    private final ClientDispatcher clientDispatcher;

    public TestSummaryExecutionListener(ExecutionListener delegate, ClientDispatcher clientDispatcher) {
        this.delegate = delegate;
        this.clientDispatcher = clientDispatcher;
    }

    @Override
    public void mojoStarted(ExecutionEvent event) {
        delegate.mojoStarted(event);
        // Folds the previous test-running mojo's snapshots into the reactor totals; a no-op for non-test mojos.
        // Needed so a project's failsafe run doesn't overwrite its still-unfolded surefire snapshots (both start
        // fork-channel ids at 0).
        clientDispatcher.foldTestProgress(event.getProject().getArtifactId());
    }

    @Override
    public void sessionEnded(ExecutionEvent event) {
        emitTestSummary();
        delegate.sessionEnded(event);
    }

    private void emitTestSummary() {
        List<TestBuildSummary.SummaryLine> lines =
                clientDispatcher.getTestSummary().renderLines();
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
    public void projectDiscoveryStarted(ExecutionEvent event) {
        delegate.projectDiscoveryStarted(event);
    }

    @Override
    public void sessionStarted(ExecutionEvent event) {
        delegate.sessionStarted(event);
    }

    @Override
    public void projectSkipped(ExecutionEvent event) {
        delegate.projectSkipped(event);
    }

    @Override
    public void projectStarted(ExecutionEvent event) {
        delegate.projectStarted(event);
    }

    @Override
    public void projectSucceeded(ExecutionEvent event) {
        delegate.projectSucceeded(event);
    }

    @Override
    public void projectFailed(ExecutionEvent event) {
        delegate.projectFailed(event);
    }

    @Override
    public void mojoSkipped(ExecutionEvent event) {
        delegate.mojoSkipped(event);
    }

    @Override
    public void mojoSucceeded(ExecutionEvent event) {
        delegate.mojoSucceeded(event);
    }

    @Override
    public void mojoFailed(ExecutionEvent event) {
        delegate.mojoFailed(event);
    }

    @Override
    public void forkStarted(ExecutionEvent event) {
        delegate.forkStarted(event);
    }

    @Override
    public void forkSucceeded(ExecutionEvent event) {
        delegate.forkSucceeded(event);
    }

    @Override
    public void forkFailed(ExecutionEvent event) {
        delegate.forkFailed(event);
    }

    @Override
    public void forkedProjectStarted(ExecutionEvent event) {
        delegate.forkedProjectStarted(event);
    }

    @Override
    public void forkedProjectSucceeded(ExecutionEvent event) {
        delegate.forkedProjectSucceeded(event);
    }

    @Override
    public void forkedProjectFailed(ExecutionEvent event) {
        delegate.forkedProjectFailed(event);
    }
}
