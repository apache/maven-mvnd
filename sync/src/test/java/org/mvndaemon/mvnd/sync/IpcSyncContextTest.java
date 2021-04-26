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
import java.util.Collections;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.SyncContextFactory;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.junit.jupiter.api.Test;

public class IpcSyncContextTest {

    @Test
    public void testContextSimple() throws Exception {
        SyncContextFactory factory = new IpcSyncContextFactory();

        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        LocalRepository repository = new LocalRepository(new File("target/test-repo"));
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
        LocalRepository repository = new LocalRepository(new File("target/test-repo"));
        LocalRepositoryManager localRepositoryManager = new SimpleLocalRepositoryManagerFactory()
                .newInstance(session, repository);
        session.setLocalRepositoryManager(localRepositoryManager);
        Artifact artifact = new DefaultArtifact("myGroup", "myArtifact", "jar", "0.1");

        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try (SyncContext context = factory.newInstance(session, false)) {
                    System.out.println("Trying to lock from " + context);
                    context.acquire(Collections.singleton(artifact), null);
                    System.out.println("Lock acquired from " + context);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("Unlock from " + context);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
    }
}
