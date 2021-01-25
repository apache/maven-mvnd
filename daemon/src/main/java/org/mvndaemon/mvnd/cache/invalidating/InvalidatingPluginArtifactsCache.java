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
package org.mvndaemon.mvnd.cache.invalidating;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.DefaultPluginArtifactsCache;
import org.apache.maven.plugin.PluginResolutionException;
import org.eclipse.sisu.Priority;
import org.mvndaemon.mvnd.cache.Cache;
import org.mvndaemon.mvnd.cache.CacheFactory;

@Singleton
@Named
@Priority(10)
public class InvalidatingPluginArtifactsCache extends DefaultPluginArtifactsCache {

    protected static class Record implements org.mvndaemon.mvnd.cache.CacheRecord {

        private final CacheRecord record;

        public Record(CacheRecord record) {
            this.record = record;
        }

        @Override
        public Stream<Path> getDependencyPaths() {
            return record.getArtifacts().stream().map(artifact -> artifact.getFile().toPath());
        }

        @Override
        public void invalidate() {
        }
    }

    final Cache<Key, Record> cache;

    @Inject
    public InvalidatingPluginArtifactsCache(CacheFactory cacheFactory) {
        this.cache = cacheFactory.newCache();
    }

    public CacheRecord get(Key key) throws PluginResolutionException {
        Record r = cache.get(key);
        if (r != null) {
            if (r.record.getException() != null) {
                throw r.record.getException();
            }
            return r.record;
        }
        return null;
    }

    public CacheRecord put(Key key, List<Artifact> pluginArtifacts) {
        CacheRecord record = super.put(key, pluginArtifacts);
        super.cache.remove(key);
        cache.put(key, new Record(record));
        return record;
    }

    public CacheRecord put(Key key, PluginResolutionException exception) {
        CacheRecord record = super.put(key, exception);
        super.cache.remove(key);
        cache.put(key, new Record(record));
        return record;
    }

    protected void assertUniqueKey(Key key) {
        if (cache.get(key) != null) {
            throw new IllegalStateException("Duplicate artifact resolution result for plugin " + key);
        }
    }

    public void flush() {
        cache.clear();
    }

}
