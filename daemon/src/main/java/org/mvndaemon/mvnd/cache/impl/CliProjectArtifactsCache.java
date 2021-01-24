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

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.DefaultProjectArtifactsCache;
import org.mvndaemon.mvnd.cache.factory.Cache;
import org.mvndaemon.mvnd.cache.factory.CacheFactory;

@Singleton
@Named
public class CliProjectArtifactsCache extends DefaultProjectArtifactsCache {

    static class Record implements org.mvndaemon.mvnd.cache.factory.CacheRecord {
        private final CacheRecord record;

        public Record(CacheRecord record) {
            this.record = record;
        }

        @Override
        public Stream<Path> getDependencyPaths() {
            return record.getArtifacts().stream()
                    .map(Artifact::getFile)
                    .filter(Objects::nonNull)
                    .map(File::toPath);
        }

        @Override
        public void invalidate() {
        }
    }

    final Cache<Key, Record> cache;

    @Inject
    public CliProjectArtifactsCache(CacheFactory cacheFactory) {
        this.cache = cacheFactory.newCache();
    }

    @Override
    public CacheRecord get(Key key) throws LifecycleExecutionException {
        Record r = cache.get(key);
        if (r != null) {
            if (r.record.getException() != null) {
                throw r.record.getException();
            }
            return r.record;
        }
        return null;
    }

    @Override
    public CacheRecord put(Key key, Set<Artifact> pluginArtifacts) {
        CacheRecord record = super.put(key, pluginArtifacts);
        super.cache.remove(key);
        cache.put(key, new Record(record));
        return record;
    }

    @Override
    public CacheRecord put(Key key, LifecycleExecutionException e) {
        CacheRecord record = super.put(key, e);
        super.cache.remove(key);
        cache.put(key, new Record(record));
        return record;
    }

    @Override
    public void flush() {
        cache.clear();
    }

    @Override
    public void register(MavenProject project, Key cacheKey, CacheRecord record) {
    }
}
