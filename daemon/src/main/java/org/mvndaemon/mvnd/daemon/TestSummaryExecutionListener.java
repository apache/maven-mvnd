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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.jline.MessageUtils;
import org.mvndaemon.mvnd.logging.smart.TestBuildSummary;

/**
 * Folds per-project test-progress snapshots into the reactor-wide {@link TestBuildSummary} and sends it to the client.
 * {@link #decorate} puts this listener in front of the {@link ExecutionListener} chain Maven 4 builds in
 * {@code MavenInvoker.determineExecutionListener} (wired up by {@link DaemonMavenInvoker#determineExecutionListener}),
 * so the summary is sent immediately before {@code delegate.sessionEnded} prints the Reactor Summary.
 */
public class TestSummaryExecutionListener extends AbstractExecutionListener {
    private final ClientDispatcher clientDispatcher;

    private TestSummaryExecutionListener(ClientDispatcher clientDispatcher) {
        this.clientDispatcher = clientDispatcher;
    }

    /**
     * Returns a listener that calls a {@link TestSummaryExecutionListener} and then {@code delegate} for every
     * {@link ExecutionListener} method. It is a proxy so that a method Maven adds to the interface still reaches
     * {@code delegate}: a default method would otherwise be skipped without a compile error.
     */
    public static ExecutionListener decorate(ExecutionListener delegate, ClientDispatcher clientDispatcher) {
        ExecutionListener summary = new TestSummaryExecutionListener(clientDispatcher);
        return (ExecutionListener) Proxy.newProxyInstance(
                ExecutionListener.class.getClassLoader(),
                new Class<?>[] {ExecutionListener.class},
                (proxy, method, args) -> {
                    try {
                        if (method.getDeclaringClass() != Object.class) {
                            method.invoke(summary, args);
                        }
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Override
    public void mojoStarted(ExecutionEvent event) {
        // Folds the previous test-running mojo's snapshots into the reactor totals; a no-op for non-test mojos.
        // Needed so a project's failsafe run doesn't overwrite its still-unfolded surefire snapshots (both start
        // fork-channel ids at 0).
        clientDispatcher.foldTestProgress(event.getProject().getArtifactId());
    }

    @Override
    public void sessionEnded(ExecutionEvent event) {
        for (TestBuildSummary.SummaryLine line :
                clientDispatcher.getTestSummary().renderLines()) {
            clientDispatcher.log(styled(line));
        }
    }

    /** Colors a line the way Maven colors its own output, which stays plain text when color is disabled. */
    private static String styled(TestBuildSummary.SummaryLine line) {
        return switch (line.level) {
            case ERROR -> MessageUtils.builder().failure(line.text).build();
            case WARNING -> MessageUtils.builder().warning(line.text).build();
            case INFO -> line.text;
        };
    }
}
