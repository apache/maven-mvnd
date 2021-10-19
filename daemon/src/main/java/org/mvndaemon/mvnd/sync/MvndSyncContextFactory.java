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
package org.mvndaemon.mvnd.sync;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.SyncContextFactory;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.sisu.Priority;
import org.mvndaemon.mvnd.common.Environment;

@Singleton
@Named
@Priority(20)
public class MvndSyncContextFactory implements SyncContextFactory {

    public static final String FACTORY_NOOP = "noop";
    public static final String FACTORY_IPC = "ipc";
    public static final String FACTORY_LOCAL = "local";

    public static final String IPC_SYNC_CONTEXT_FACTORY = "org.mvndaemon.mvnd.sync.IpcSyncContextFactory";

    private final Map<String, SyncContextFactory> factories;

    @SuppressWarnings("unchecked")
    public MvndSyncContextFactory() {
        try {
            Map<String, SyncContextFactory> map = new HashMap<>();
            map.put(FACTORY_LOCAL, new LocalSyncContextFactory());
            map.put(FACTORY_NOOP, new NoopSyncContextFactory());
            Class<? extends SyncContextFactory> factoryClass = (Class<? extends SyncContextFactory>) getClass().getClassLoader()
                    .loadClass(IPC_SYNC_CONTEXT_FACTORY);
            map.put(FACTORY_IPC, factoryClass.getDeclaredConstructor().newInstance());
            factories = Collections.unmodifiableMap(map);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create IpcSyncContextFactory instance", e);
        }
    }

    @Override
    public SyncContext newInstance(RepositorySystemSession repositorySystemSession, boolean shared) {
        String name = Environment.MVND_SYNC_CONTEXT_FACTORY.asOptional()
                .orElseGet(Environment.MVND_SYNC_CONTEXT_FACTORY::getDefault);
        SyncContextFactory factory = factories.get(name);
        if (factory == null) {
            throw new RuntimeException("Unable to find SyncContextFactory named '" + name + "'");
        }
        return factory.newInstance(repositorySystemSession, shared);
    }

    private static class NoopSyncContextFactory implements SyncContextFactory {
        @Override
        public SyncContext newInstance(RepositorySystemSession repositorySystemSession, boolean shared) {
            return new SyncContext() {
                @Override
                public void acquire(Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas) {
                }

                @Override
                public void close() {
                }
            };
        }
    }

    private static class LocalSyncContextFactory implements SyncContextFactory {
        final Map<String, Lock> locks = new ConcurrentHashMap<>();
        @Override
        public SyncContext newInstance(RepositorySystemSession session, boolean shared) {
            return new LocalSyncContext();
        }

        private class LocalSyncContext implements SyncContext {
            private final Deque<String> locked = new ArrayDeque<>();

            @Override
            public void acquire(Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas) {
                Collection<String> keys = new TreeSet<>();
                stream(artifacts).map(this::getKey).forEach(keys::add);
                stream(metadatas).map(this::getKey).forEach(keys::add);
                String current = null;
                try {
                    for (String key : keys) {
                        current = key;
                        getLock(key).lock();
                        locked.add(key);
                    }
                } catch (Exception e) {
                    close();
                    throw new IllegalStateException("Could not acquire lock for '" + current + "'", e);
                }
            }

            @Override
            public void close() {
                String key;
                while ((key = locked.poll()) != null) {
                    getLock(key).unlock();
                }
            }

            private Lock getLock(String key) {
                return locks.computeIfAbsent(key, k -> new ReentrantLock());
            }

            private <T> Stream<T> stream(Collection<T> col) {
                return col != null ? col.stream() : Stream.empty();
            }

            private String getKey(Artifact a) {
                return "artifact:" + a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getBaseVersion();
            }

            private String getKey(Metadata m) {
                StringBuilder key = new StringBuilder("metadata:");
                if (!m.getGroupId().isEmpty()) {
                    key.append(m.getGroupId());
                    if (!m.getArtifactId().isEmpty()) {
                        key.append(':').append(m.getArtifactId());
                        if (!m.getVersion().isEmpty()) {
                            key.append(':').append(m.getVersion());
                        }
                    }
                }
                return key.toString();
            }
        }
    }

}
