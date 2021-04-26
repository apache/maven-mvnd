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

import java.util.Collection;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

/**
 * The SyncContext implementation.
 */
class IpcSyncContext implements SyncContext {

    IpcClient client;
    boolean shared;
    String contextId;

    IpcSyncContext(IpcClient client, boolean shared) {
        this.client = client;
        this.shared = shared;
        this.contextId = client.newContext(shared);
    }

    @Override
    public void acquire(Collection<? extends Artifact> artifacts, Collection<? extends Metadata> metadatas) {
        Collection<String> keys = new TreeSet<>();
        stream(artifacts).map(this::getKey).forEach(keys::add);
        stream(metadatas).map(this::getKey).forEach(keys::add);
        if (keys.isEmpty()) {
            return;
        }
        client.lock(contextId, keys);
    }

    @Override
    public void close() {
        if (contextId != null) {
            client.unlock(contextId);
        }
    }

    @Override
    public String toString() {
        return "IpcSyncContext{"
                + "client=" + client
                + ", shared=" + shared
                + ", contextId='" + contextId + '\''
                + '}';
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
