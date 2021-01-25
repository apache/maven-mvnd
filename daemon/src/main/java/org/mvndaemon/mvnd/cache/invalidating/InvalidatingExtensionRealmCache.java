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
import org.apache.maven.plugin.DefaultExtensionRealmCache;
import org.apache.maven.project.ExtensionDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.mvndaemon.mvnd.cache.Cache;
import org.mvndaemon.mvnd.cache.CacheFactory;

@Singleton
@Named
public class InvalidatingExtensionRealmCache extends DefaultExtensionRealmCache {

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
            ClassRealm realm = record.getRealm();
            try {
                realm.getWorld().disposeRealm(realm.getId());
            } catch (NoSuchRealmException e) {
                // ignore
            }
        }
    }

    private final Cache<Key, Record> cache;

    @Inject
    public InvalidatingExtensionRealmCache(CacheFactory cacheFactory) {
        cache = cacheFactory.newCache();
    }

    @Override
    public CacheRecord get(Key key) {
        Record r = cache.get(key);
        return r != null ? r.record : null;
    }

    @Override
    public CacheRecord put(Key key, ClassRealm extensionRealm, ExtensionDescriptor extensionDescriptor,
            List<Artifact> artifacts) {
        CacheRecord record = super.put(key, extensionRealm, extensionDescriptor, artifacts);
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
