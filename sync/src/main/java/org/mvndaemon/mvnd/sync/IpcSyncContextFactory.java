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

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PreDestroy;
import javax.annotation.Priority;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.impl.SyncContextFactory;

/**
 * The SyncContextFactory implementation.
 */
@Named
@Priority(Integer.MAX_VALUE)
@Singleton
public class IpcSyncContextFactory implements SyncContextFactory {

    private final Map<Path, IpcClient> clients = new ConcurrentHashMap<>();

    @Override
    public SyncContext newInstance(RepositorySystemSession session, boolean shared) {
        Path repository = session.getLocalRepository().getBasedir().toPath();
        IpcClient client = clients.computeIfAbsent(repository, IpcClient::new);
        return new IpcSyncContext(client, shared);
    }

    @PreDestroy
    void close() {
        clients.values().forEach(IpcClient::close);
    }

    @Override
    public String toString() {
        return "IpcSyncContextFactory{"
                + "clients=" + clients
                + '}';
    }
}
