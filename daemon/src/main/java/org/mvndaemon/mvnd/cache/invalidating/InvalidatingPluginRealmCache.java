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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.DefaultPluginRealmCache;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.eclipse.sisu.Priority;
import org.mvndaemon.mvnd.cache.Cache;
import org.mvndaemon.mvnd.cache.CacheFactory;
import org.mvndaemon.mvnd.forknode.MvndSurefireProgressLocator;

@Singleton
@Named
@Priority(10)
public class InvalidatingPluginRealmCache extends DefaultPluginRealmCache {

    protected static class Record implements org.mvndaemon.mvnd.cache.CacheRecord {

        final CacheRecord record;

        public Record(CacheRecord record) {
            this.record = record;
        }

        @Override
        public Stream<Path> getDependencyPaths() {
            return record.getArtifacts().stream()
                    .map(artifact -> artifact.getFile().toPath());
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
    public InvalidatingPluginRealmCache(CacheFactory cacheFactory) {
        cache = cacheFactory.newCache();
    }

    @Override
    public CacheRecord get(Key key) {
        Record r = cache.get(key);
        return r != null ? r.record : null;
    }

    @Override
    public CacheRecord get(Key key, PluginRealmSupplier supplier)
            throws PluginResolutionException, PluginContainerException {
        try {
            Record r = cache.computeIfAbsent(key, k -> {
                try {
                    CacheRecord loaded = supplier.load();
                    addTestProgressJarIfSurefire(loaded.getRealm());
                    return new Record(loaded);
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

    /**
     * Puts the {@code mvnd-surefire-progress} jar on the surefire/failsafe plugin realm so Surefire can load
     * {@code MvndForkNodeFactory} when it parses the injected {@code <forkNode>} configuration. No-op for any other
     * plugin realm, and never fails plugin-realm creation because of the progress feature.
     */
    private static void addTestProgressJarIfSurefire(ClassRealm realm) {
        String id = realm != null ? realm.getId() : null;
        if (id == null || (!id.contains("maven-surefire-plugin") && !id.contains("maven-failsafe-plugin"))) {
            return;
        }
        try {
            java.security.CodeSource cs =
                    MvndSurefireProgressLocator.class.getProtectionDomain().getCodeSource();
            URL jar = cs != null ? cs.getLocation() : null;
            if (jar != null) {
                realm.addURL(jar);
            }
        } catch (RuntimeException e) {
            // ignore: the test-progress feature must never break plugin realm creation
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
    public void register(MavenProject project, Key key, CacheRecord record) {}
}
