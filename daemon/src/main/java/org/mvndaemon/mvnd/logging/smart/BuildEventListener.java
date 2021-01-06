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

/**
 * An abstract build event sink.
 */
public abstract class BuildEventListener {
    private static final BuildEventListener DUMMY = new BuildEventListener() {

        public void sessionStarted(ExecutionEvent event) {
        }

        public void projectStarted(ExecutionEvent event) {
        }

        public void projectLogMessage(String projectId, String event) {
        }

        public void projectFinished(ExecutionEvent event) {
        }

        public void mojoStarted(ExecutionEvent event) {
        }

        public void finish(int exitCode) throws Exception {
        }

        public void fail(Throwable t) throws Exception {
        }

        public void log(String msg) {
        }

    };

    /**
     * @return a dummy {@link BuildEventListener} that just swallows the messages and does not send them anywhere
     */
    public static BuildEventListener dummy() {
        return DUMMY;
    }

    protected BuildEventListener() {
    }

    public abstract void sessionStarted(ExecutionEvent event);

    public abstract void projectStarted(ExecutionEvent event);

    public abstract void projectLogMessage(String projectId, String event);

    public abstract void projectFinished(ExecutionEvent event);

    public abstract void mojoStarted(ExecutionEvent event);

    public abstract void finish(int exitCode) throws Exception;

    public abstract void fail(Throwable t) throws Exception;

    public abstract void log(String msg);
}
