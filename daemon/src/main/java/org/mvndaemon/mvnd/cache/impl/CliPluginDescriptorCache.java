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
package org.mvndaemon.mvnd.cache.impl;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.DefaultPluginDescriptorCache;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.mvndaemon.mvnd.cache.factory.Cache;
import org.mvndaemon.mvnd.cache.factory.CacheFactory;
import org.mvndaemon.mvnd.cache.factory.CacheRecord;

@Singleton
@Named
public class CliPluginDescriptorCache extends DefaultPluginDescriptorCache {

    protected static class Record implements CacheRecord {

        private final PluginDescriptor descriptor;

        public Record(PluginDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public Stream<Path> getDependentPaths() {
            return Optional.ofNullable(descriptor.getArtifacts()).orElse(Collections.emptyList())
                    .stream().map(artifact -> artifact.getFile().toPath());
        }

        @Override
        public void invalidate() {
            ClassRealm realm = descriptor.getClassRealm();
            try {
                realm.getWorld().disposeRealm(realm.getId());
            } catch (NoSuchRealmException e) {
                // ignore
            }
        }
    }

    final Cache<Key, Record> cache;

    @Inject
    public CliPluginDescriptorCache(CacheFactory cacheFactory) {
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
    public void put(Key key, PluginDescriptor descriptor) {
        cache.put(key, new Record(clone(descriptor)));
    }

    @Override
    public void flush() {
        cache.clear();
    }
}
