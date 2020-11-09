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
package org.jboss.fuse.mvnd.common.logging;

import org.jline.terminal.Terminal;

/**
 * A sink for various kinds of events sent by the daemon.
 */
public interface ClientOutput extends AutoCloseable {

    Terminal getTerminal();

    void startBuild(String name, int projects, int cores);

    void projectStateChanged(String projectId, String display);

    void projectFinished(String projectId);

    void accept(String projectId, String message);

    void error(String message, String className, String stackTrace);

    void keepAlive();

    void buildStatus(String status);

    void display(String projectId, String message);

    String prompt(String projectId, String message, boolean password);

    void onInterrupt(Runnable runnable);

    void cancel();
}
