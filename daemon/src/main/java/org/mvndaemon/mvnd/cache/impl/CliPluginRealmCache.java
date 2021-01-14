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
package org.mvndaemon.mvnd.cache.impl;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.DefaultPluginRealmCache;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.plugin.PluginRealmCache;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.eclipse.sisu.Priority;
import org.eclipse.sisu.Typed;
import org.mvndaemon.mvnd.cache.factory.Cache;
import org.mvndaemon.mvnd.cache.factory.CacheFactory;

@Singleton
@Named
@Priority(10)
@Typed(PluginRealmCache.class)
public class CliPluginRealmCache extends DefaultPluginRealmCache {

    @FunctionalInterface
    public interface PluginRealmSupplier {
        CacheRecord load() throws PluginResolutionException, PluginContainerException;
    }

    protected static class Record implements org.mvndaemon.mvnd.cache.factory.CacheRecord {

        final CacheRecord record;

        public Record(CacheRecord record) {
            this.record = record;
        }

        @Override
        public Stream<Path> getDependentPaths() {
            return record.getArtifacts().stream().map(artifact -> artifact.getFile().toPath());
        }

        @Override
        public void invalidate() {
            ClassRealm realm = record.getRealm();
            try {
                realm.getWorld().disposeRealm(realm.getId());
            } catch (NoSuchRealmException e) {
                // ignore
            }
        }
    }

    final Cache<Key, Record> cache;

    @Inject
    public CliPluginRealmCache(CacheFactory cacheFactory) {
        cache = cacheFactory.newCache();
    }

    @Override
    public CacheRecord get(Key key) {
        Record r = cache.get(key);
        return r != null ? r.record : null;
    }

    public CacheRecord get(Key key, PluginRealmSupplier supplier)
            throws PluginResolutionException, PluginContainerException {
        try {
            Record r = cache.computeIfAbsent(key, k -> {
                try {
                    return new Record(supplier.load());
                } catch (PluginResolutionException | PluginContainerException e) {
                    throw new RuntimeException(e);
                }
            });
            return r.record;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof PluginResolutionException) {
                throw (PluginResolutionException) e.getCause();
            }
            if (e.getCause() instanceof PluginContainerException) {
                throw (PluginContainerException) e.getCause();
            }
            throw e;
        }
    }

    @Override
    public CacheRecord put(Key key, ClassRealm pluginRealm, List<Artifact> pluginArtifacts) {
        CacheRecord record = super.put(key, pluginRealm, pluginArtifacts);
        super.cache.remove(key);
        cache.put(key, new Record(record));
        return record;
    }

    @Override
    public void flush() {
        cache.clear();
    }

    @Override
    public void register(MavenProject project, Key key, CacheRecord record) {
    }

}
