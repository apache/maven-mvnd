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
package org.mvndaemon.mvnd.junit;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.mvndaemon.mvnd.common.DaemonInfo;
import org.mvndaemon.mvnd.common.DaemonRegistry;
import org.mvndaemon.mvnd.common.DaemonState;

public class TestRegistry extends DaemonRegistry {

    public TestRegistry(Path registryFile) {
        super(registryFile);
    }

    /**
     * Kill all daemons in the registry.
     */
    public void killAll() {
        List<DaemonInfo> daemons;
        final int timeout = 5000;
        final long deadline = System.currentTimeMillis() + timeout;
        while (!(daemons = getAll()).isEmpty()) {
            for (DaemonInfo di : daemons) {
                try {
                    final Optional<ProcessHandle> maybeHandle = ProcessHandle.of(di.getPid());
                    if (maybeHandle.isPresent()) {
                        final ProcessHandle handle = maybeHandle.get();
                        final CompletableFuture<ProcessHandle> exit = handle.onExit();
                        handle.destroy();
                        exit.get(5, TimeUnit.SECONDS);
                    }
                } catch (Exception t) {
                    System.out.println("Daemon " + di.getUid() + ": " + t);
                } finally {
                    remove(di.getUid());
                }
            }
            if (deadline < System.currentTimeMillis() && !getAll().isEmpty()) {
                throw new RuntimeException("Could not stop all mvnd daemons within " + timeout + " ms");
            }
        }
    }

    /**
     * Poll the state of the daemon with the given {@code uid} until it becomes idle.
     *
     * @param  uid                   the uid of the daemon to poll
     * @throws IllegalStateException if the daemon is not available in the registry
     * @throws AssertionError        if the timeout is exceeded
     */
    public void awaitIdle(String uid) {
        final int timeoutMs = 5000;
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (getAll().stream()
                .filter(di -> di.getUid().equals(uid))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Daemon " + uid + " is not available in the registry"))
                .getState() != DaemonState.Idle) {
            Assertions.assertThat(deadline)
                    .withFailMessage("Daemon %s should have become idle within %d", uid, timeoutMs)
                    .isGreaterThan(System.currentTimeMillis());
        }
    }

}
