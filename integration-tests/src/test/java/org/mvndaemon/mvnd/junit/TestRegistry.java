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
package org.mvndaemon.mvnd.junit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
     * Kill all daemons in the registry, forcibly if they do not exit within a few seconds.
     *
     * @throws IllegalStateException if a daemon is still alive afterwards
     */
    public void killAll() {
        final List<String> survivors = new ArrayList<>();
        final long deadline = System.currentTimeMillis() + 30_000;
        List<DaemonInfo> daemons;
        // repeat, as a daemon may register while others are being stopped
        while (!(daemons = getAll()).isEmpty()) {
            for (DaemonInfo di : daemons) {
                ProcessHandle.of(di.getPid()).ifPresent(handle -> {
                    if (!kill(handle)) {
                        survivors.add(di.getId() + " (pid " + di.getPid() + ")");
                    }
                });
                remove(di.getId());
            }
            if (deadline < System.currentTimeMillis()) {
                break;
            }
        }
        if (!survivors.isEmpty()) {
            throw new IllegalStateException("Could not stop mvnd daemons " + survivors);
        }
    }

    /**
     * Kill the daemons a previous run left behind for this test: their registry may already be gone, so they are
     * found by the {@code -Dmvnd.daemonStorage} path on their command line.
     */
    static void killLeftovers(Path daemonStorageRoot) {
        final String marker = "-Dmvnd.daemonStorage=" + daemonStorageRoot;
        ProcessHandle.allProcesses()
                .filter(handle -> handle.info()
                        .commandLine()
                        .filter(cmd -> cmd.contains(marker))
                        .isPresent())
                .forEach(TestRegistry::kill);
    }

    static boolean kill(ProcessHandle handle) {
        final CompletableFuture<ProcessHandle> exit = handle.onExit();
        handle.destroy();
        try {
            exit.get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            handle.destroyForcibly();
            try {
                exit.get(5, TimeUnit.SECONDS);
                return true;
            } catch (Exception e2) {
                return !handle.isAlive();
            }
        }
    }

    /**
     * Poll the state of the daemon with the given {@code uid} until it becomes idle.
     *
     * @param  daemonId              the ID of the daemon to poll
     * @throws IllegalStateException if the daemon is not available in the registry
     * @throws AssertionError        if the timeout is exceeded
     */
    public void awaitIdle(String daemonId) {
        final int timeoutMs = 5000;
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (getAll().stream()
                        .filter(di -> di.getId().equals(daemonId))
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException("Daemon " + daemonId + " is not available in the registry"))
                        .getState()
                != DaemonState.Idle) {
            Assertions.assertThat(deadline)
                    .withFailMessage("Daemon %s should have become idle within %d", daemonId, timeoutMs)
                    .isGreaterThan(System.currentTimeMillis());
        }
    }
}
