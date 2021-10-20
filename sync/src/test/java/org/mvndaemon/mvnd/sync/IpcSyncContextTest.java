/*
 * Copyright 2021 the original author or authors.
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
package org.mvndaemon.mvnd.sync;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.common.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IpcSyncContextTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(IpcSyncContextTest.class);

    @BeforeAll
    static void setup() {
        Path target = Paths.get(System.getProperty("basedir", "")).resolve("target");
        System.setProperty(Environment.MVND_DAEMON_STORAGE.getProperty(), target.resolve("mvnd/storage").toString());
        System.setProperty(Environment.MVND_HOME.getProperty(), target.resolve("mvnd/home").toString());
        System.setProperty(IpcServer.IDLE_TIMEOUT_PROP, "5");
        System.setProperty(IpcServer.FAMILY_PROP, "inet");
        System.setProperty(IpcServer.NO_NATIVE_PROP, "true");
    }

    @AfterAll
    static void tearDown() {
        System.clearProperty(IpcServer.IDLE_TIMEOUT_PROP);
    }

    @Test
    public void testContextSimple() throws Exception {
        SyncContextFactory factory = new IpcSyncContextFactory();

        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        LocalRepository repository = new LocalRepository(new File("target/mvnd/test-repo"));
        LocalRepositoryManager localRepositoryManager = new SimpleLocalRepositoryManagerFactory()
                .newInstance(session, repository);
        session.setLocalRepositoryManager(localRepositoryManager);
        Artifact artifact = new DefaultArtifact("myGroup", "myArtifact", "jar", "0.1");

        try (SyncContext context = factory.newInstance(session, false)) {
            context.acquire(Collections.singleton(artifact), null);
            Thread.sleep(50);
        }
    }

    @Test
    public void testContext() throws Exception {
        SyncContextFactory factory = new IpcSyncContextFactory();

        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        LocalRepository repository = new LocalRepository(new File("target/mvnd/test-repo"));
        LocalRepositoryManager localRepositoryManager = new SimpleLocalRepositoryManagerFactory()
                .newInstance(session, repository);
        session.setLocalRepositoryManager(localRepositoryManager);
        Artifact artifact = new DefaultArtifact("myGroup", "myArtifact", "jar", "0.1");

        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try (SyncContext context = factory.newInstance(session, false)) {
                    LOGGER.info("Trying to lock from {}", context);
                    context.acquire(Collections.singleton(artifact), null);
                    LOGGER.info("Lock acquired from {}", context);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    LOGGER.info("Unlock from {}", context);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
    }

    @Test
    void testTimeoutAndConnect() throws Exception {
        System.setProperty(IpcServer.IDLE_TIMEOUT_PROP, "50ms");
        System.setProperty(IpcServer.NO_FORK_PROP, "true");
        try {

            SyncContextFactory factory = new IpcSyncContextFactory();

            DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
            LocalRepository repository = new LocalRepository(new File("target/mvnd/test-repo"));
            LocalRepositoryManager localRepositoryManager = new SimpleLocalRepositoryManagerFactory()
                    .newInstance(session, repository);
            session.setLocalRepositoryManager(localRepositoryManager);
            Artifact artifact = new DefaultArtifact("myGroup", "myArtifact", "jar", "0.1");

            for (int i = 0; i < 10; i++) {
                LOGGER.info("[client] Creating sync context");
                try (SyncContext context = factory.newInstance(session, false)) {
                    LOGGER.info("[client] Sync context created: {}", context.toString());
                    context.acquire(Collections.singleton(artifact), null);
                }
                LOGGER.info("[client] Sync context closed");
                Thread.sleep(100);
            }
        } finally {
            System.clearProperty(IpcServer.IDLE_TIMEOUT_PROP);
            System.clearProperty(IpcServer.NO_FORK_PROP);
        }
    }
}
