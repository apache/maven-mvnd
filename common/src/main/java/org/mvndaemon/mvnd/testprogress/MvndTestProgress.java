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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bridge between Surefire's plugin realm (where {@code MvndForkNodeFactory} runs) and mvnd's daemon realm
 * (where the {@code ClientDispatcher} lives). This type MUST be loaded from a package exported by the Maven core
 * realm so both realms resolve the same {@link Class} and therefore share the static {@link #LISTENER} registry.
 */
public interface MvndTestProgress {

    /**
     * Push a per-test progress snapshot. Implementations must be cheap and non-throwing; the caller already
     * guards against exceptions but should not rely on it.
     */
    void update(
            String projectId,
            int forkChannelId,
            String testClass,
            String testMethod,
            int completed,
            int failures,
            int errors,
            int skipped,
            int retrying,
            int flaky,
            List<String> flakyTests,
            List<String> failedTests,
            List<String> erroredTests);

    AtomicReference<MvndTestProgress> LISTENER = new AtomicReference<>();

    /** Registered by the daemon at build start; cleared at build end. */
    static void setListener(MvndTestProgress listener) {
        LISTENER.set(listener);
    }

    /** Returns the active listener, or {@code null} when the feature is off or this is not a daemon invocation. */
    static MvndTestProgress getListener() {
        return LISTENER.get();
    }
}
