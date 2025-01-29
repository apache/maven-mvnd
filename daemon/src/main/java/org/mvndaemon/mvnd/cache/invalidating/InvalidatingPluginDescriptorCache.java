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
package org.mvndaemon.mvnd.cache.invalidating;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.*;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.sisu.Priority;
import org.mvndaemon.mvnd.cache.Cache;
import org.mvndaemon.mvnd.cache.CacheFactory;
import org.mvndaemon.mvnd.cache.CacheRecord;

@Singleton
@Named
@Priority(10)
public class InvalidatingPluginDescriptorCache extends DefaultPluginDescriptorCache {
    protected static class Record implements CacheRecord {

        private final PluginDescriptor descriptor;

        public Record(PluginDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public Stream<Path> getDependencyPaths() {
            return Optional.ofNullable(descriptor.getArtifacts()).orElse(Collections.emptyList()).stream()
                    .map(artifact -> artifact.getFile().toPath());
        }

        @Override
        public void invalidate() {
            ClassRealm realm = descriptor.getClassRealm();
            if (realm == null) {
                return;
            }
            try {
                realm.getWorld().disposeRealm(realm.getId());
            } catch (NoSuchRealmException e) {
                // ignore
            }
        }
    }

    final Cache<Key, Record> cache;

    @Inject
    public InvalidatingPluginDescriptorCache(CacheFactory cacheFactory) {
        this.cache = cacheFactory.newCache();
    }

    @Override
    public Key createKey(Plugin plugin, List<RemoteRepository> repositories, RepositorySystemSession session) {
        return super.createKey(plugin, repositories, session);
    }

    @Override
    public PluginDescriptor get(Key key) {
        Record r = cache.get(key);
        return r != null ? clone(r.descriptor) : null;
    }

    @Override
    public PluginDescriptor get(Key key, PluginDescriptorSupplier supplier)
            throws PluginDescriptorParsingException, PluginResolutionException, InvalidPluginDescriptorException {
        try {
            Record r = cache.computeIfAbsent(key, k -> {
                try {
                    return new Record(clone(supplier.load()));
                } catch (PluginDescriptorParsingException
                        | PluginResolutionException
                        | InvalidPluginDescriptorException e) {
                    throw new RuntimeException(e);
                }
            });
            return clone(r.descriptor);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof PluginDescriptorParsingException) {
                throw (PluginDescriptorParsingException) e.getCause();
            }
            if (e.getCause() instanceof PluginResolutionException) {
                throw (PluginResolutionException) e.getCause();
            }
            if (e.getCause() instanceof InvalidPluginDescriptorException) {
                throw (InvalidPluginDescriptorException) e.getCause();
            }
            throw e;
        }
    }

    @Override
    public void put(Key key, PluginDescriptor descriptor) {
        cache.put(key, new Record(clone(descriptor)));
    }

    @Override
    public void flush() {
        cache.clear();
    }
}
