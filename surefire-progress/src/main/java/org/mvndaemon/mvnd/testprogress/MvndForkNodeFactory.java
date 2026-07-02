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
package org.mvndaemon.mvnd.testprogress;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.maven.plugin.surefire.extensions.SurefireForkNodeFactory;
import org.apache.maven.surefire.api.event.Event;
import org.apache.maven.surefire.api.event.TestErrorEvent;
import org.apache.maven.surefire.api.event.TestFailedEvent;
import org.apache.maven.surefire.api.event.TestSkippedEvent;
import org.apache.maven.surefire.api.event.TestStartingEvent;
import org.apache.maven.surefire.api.event.TestSucceededEvent;
import org.apache.maven.surefire.api.event.TestsetCompletedEvent;
import org.apache.maven.surefire.api.event.TestsetStartingEvent;
import org.apache.maven.surefire.api.fork.ForkNodeArguments;
import org.apache.maven.surefire.api.report.ReportEntry;
import org.apache.maven.surefire.extensions.CommandReader;
import org.apache.maven.surefire.extensions.EventHandler;
import org.apache.maven.surefire.extensions.ForkChannel;
import org.apache.maven.surefire.extensions.util.CountdownCloseable;

/**
 * A {@link org.apache.maven.surefire.extensions.ForkNodeFactory} that delegates channel creation to Surefire's
 * default ({@link SurefireForkNodeFactory}) and decorates the {@link EventHandler} so mvnd can observe per-test
 * events. Injected into the surefire/failsafe {@code <forkNode>} config by the daemon; carries the mvnd
 * {@code projectId} for attribution.
 */
public class MvndForkNodeFactory extends SurefireForkNodeFactory {

    /** Set by Surefire from the injected {@code <forkNode><projectId>...</projectId></forkNode>} configuration. */
    private String projectId;

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectId() {
        return projectId;
    }

    @Override
    public ForkChannel createForkChannel(ForkNodeArguments arguments) throws IOException {
        ForkChannel delegate = super.createForkChannel(arguments);
        return new WrappingForkChannel(arguments, delegate, projectId);
    }

    /** Wraps a {@link ForkChannel}, decorating the event handler passed to {@link #bindEventHandler}. */
    static final class WrappingForkChannel extends ForkChannel {
        private final ForkChannel delegate;
        private final String projectId;

        WrappingForkChannel(ForkNodeArguments arguments, ForkChannel delegate, String projectId) {
            super(arguments);
            this.delegate = delegate;
            this.projectId = projectId;
        }

        @Override
        public void tryConnectToClient() throws IOException, InterruptedException {
            delegate.tryConnectToClient();
        }

        @Override
        public String getForkNodeConnectionString() {
            return delegate.getForkNodeConnectionString();
        }

        @Override
        public int getCountdownCloseablePermits() {
            return delegate.getCountdownCloseablePermits();
        }

        @Override
        public void bindCommandReader(CommandReader commands, WritableByteChannel stdIn)
                throws IOException, InterruptedException {
            delegate.bindCommandReader(commands, stdIn);
        }

        @Override
        public void bindEventHandler(
                EventHandler<Event> eventHandler, CountdownCloseable countdown, ReadableByteChannel stdOut)
                throws IOException, InterruptedException {
            delegate.bindEventHandler(
                    new ProgressEventHandler(projectId, eventHandler, new TestProgressAccumulator()),
                    countdown,
                    stdOut);
        }

        @Override
        public void disable() {
            delegate.disable();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    /** Observes each event, updates the accumulator, pushes through the bridge, then always delegates. */
    static final class ProgressEventHandler implements EventHandler<Event> {
        private final String projectId;
        private final EventHandler<Event> delegate;
        private final TestProgressAccumulator acc;

        ProgressEventHandler(String projectId, EventHandler<Event> delegate, TestProgressAccumulator acc) {
            this.projectId = projectId;
            this.delegate = delegate;
            this.acc = acc;
        }

        @Override
        public void handleEvent(Event event) {
            try {
                observe(event);
            } catch (Throwable ignored) {
                // Never break the test run because of the progress feature.
            }
            delegate.handleEvent(event);
        }

        private void observe(Event event) {
            final TestProgressAccumulator.Type type;
            final ReportEntry re;
            if (event instanceof TestsetStartingEvent) {
                type = TestProgressAccumulator.Type.TESTSET_STARTING;
                re = ((TestsetStartingEvent) event).getReportEntry();
            } else if (event instanceof TestStartingEvent) {
                type = TestProgressAccumulator.Type.TEST_STARTING;
                re = ((TestStartingEvent) event).getReportEntry();
            } else if (event instanceof TestSucceededEvent) {
                type = TestProgressAccumulator.Type.TEST_SUCCEEDED;
                re = ((TestSucceededEvent) event).getReportEntry();
            } else if (event instanceof TestFailedEvent) {
                type = TestProgressAccumulator.Type.TEST_FAILED;
                re = ((TestFailedEvent) event).getReportEntry();
            } else if (event instanceof TestErrorEvent) {
                type = TestProgressAccumulator.Type.TEST_ERROR;
                re = ((TestErrorEvent) event).getReportEntry();
            } else if (event instanceof TestSkippedEvent) {
                type = TestProgressAccumulator.Type.TEST_SKIPPED;
                re = ((TestSkippedEvent) event).getReportEntry();
            } else if (event instanceof TestsetCompletedEvent) {
                type = TestProgressAccumulator.Type.TESTSET_COMPLETED;
                re = ((TestsetCompletedEvent) event).getReportEntry();
            } else {
                return; // not a test lifecycle event
            }

            acc.record(type, re.getSourceName(), re.getName());

            MvndTestProgress listener = MvndTestProgress.getListener();
            if (listener != null) {
                listener.update(
                        projectId,
                        acc.getTestClass(),
                        acc.getTestMethod(),
                        acc.getCompleted(),
                        acc.getFailures(),
                        acc.getErrors(),
                        acc.getSkipped());
            }
        }
    }
}
