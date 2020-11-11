/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.mvndaemon.mvnd.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.PluginRealmCache;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.sisu.Priority;
import org.eclipse.sisu.Typed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default PluginCache implementation. Assumes cached data does not change.
 *
 * File origin:
 * https://github.com/apache/maven/blob/maven-3.6.2/maven-core/src/main/java/org/apache/maven/plugin/DefaultPluginRealmCache.java
 */
@Singleton
@Named
@Priority(10)
@Typed(PluginRealmCache.class)
public class CliPluginRealmCache
        implements PluginRealmCache, Disposable {
    /**
     * CacheKey
     */
    protected static class CacheKey
            implements Key {

        private final Plugin plugin;

        private final WorkspaceRepository workspace;

        private final LocalRepository localRepo;

        private final List<RemoteRepository> repositories;

        private final ClassLoader parentRealm;

        private final Map<String, ClassLoader> foreignImports;

        private final DependencyFilter filter;

        private final int hashCode;

        public CacheKey(Plugin plugin, ClassLoader parentRealm, Map<String, ClassLoader> foreignImports,
                DependencyFilter dependencyFilter, List<RemoteRepository> repositories,
                RepositorySystemSession session) {
            this.plugin = plugin.clone();
            this.workspace = RepositoryUtils.getWorkspace(session);
            this.localRepo = session.getLocalRepository();
            this.repositories = new ArrayList<>(repositories.size());
            for (RemoteRepository repository : repositories) {
                if (repository.isRepositoryManager()) {
                    this.repositories.addAll(repository.getMirroredRepositories());
                } else {
                    this.repositories.add(repository);
                }
            }
            this.parentRealm = parentRealm;
            this.foreignImports = (foreignImports != null) ? foreignImports : Collections.emptyMap();
            this.filter = dependencyFilter;

            int hash = 17;
            hash = hash * 31 + CliCacheUtils.pluginHashCode(plugin);
            hash = hash * 31 + Objects.hashCode(workspace);
            hash = hash * 31 + Objects.hashCode(localRepo);
            hash = hash * 31 + RepositoryUtils.repositoriesHashCode(repositories);
            hash = hash * 31 + Objects.hashCode(parentRealm);
            hash = hash * 31 + this.foreignImports.hashCode();
            hash = hash * 31 + Objects.hashCode(dependencyFilter);
            this.hashCode = hash;
        }

        @Override
        public String toString() {
            return plugin.getId();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if (!(o instanceof CacheKey)) {
                return false;
            }

            CacheKey that = (CacheKey) o;

            return parentRealm == that.parentRealm
                    && CliCacheUtils.pluginEquals(plugin, that.plugin)
                    && Objects.equals(workspace, that.workspace)
                    && Objects.equals(localRepo, that.localRepo)
                    && RepositoryUtils.repositoriesEquals(this.repositories, that.repositories)
                    && Objects.equals(filter, that.filter)
                    && Objects.equals(foreignImports, that.foreignImports);
        }
    }

    static class ValidableCacheRecord extends CacheRecord {

        private volatile boolean valid = true;

        public ValidableCacheRecord(ClassRealm realm, List<Artifact> artifacts) {
            super(realm, artifacts);
        }

        public boolean isValid() {
            return valid;
        }

        public void dispose() {
            ClassRealm realm = getRealm();
            try {
                realm.getWorld().disposeRealm(realm.getId());
            } catch (NoSuchRealmException e) {
                // ignore
            }
        }
    }

    /**
     * A {@link RecordValidator} with some methods to watch JARs associated with {@link ValidableCacheRecord}.
     */
    static class RecordValidator {
        private final WatchService watchService;

        /**
         * Records that have no been invalidated so far. From watched JAR paths to records (because one JAR can be
         * present in multiple records)
         */
        private final Map<Path, List<ValidableCacheRecord>> validRecordsByPath = new ConcurrentHashMap<>();

        /**
         * {@link WatchService} can watch only directories but we actually want to watch files. So here we store
         * for the given parent directory the count of its child files we watch.
         */
        private final Map<Path, Registration> registrationsByDir = new ConcurrentHashMap<>();

        public RecordValidator() {
            try {
                this.watchService = FileSystems.getDefault().newWatchService();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Watch the JARs associated with the given {@code record} for deletions and modifications.
         *
         * @param record the {@link ValidableCacheRecord} to watch
         */
        void add(ValidableCacheRecord record) {
            record.getArtifacts().stream()
                    .map(Artifact::getFile)
                    .map(File::toPath)
                    .forEach(p -> {
                        validRecordsByPath.compute(p, (key, value) -> {
                            if (value == null) {
                                value = new ArrayList<>();
                            }
                            value.add(record);
                            return value;
                        });
                        final Path dir = p.getParent();
                        registrationsByDir.compute(dir, (key, value) -> {
                            if (value == null) {
                                LOG.debug("Starting to watch path {}", key);
                                try {
                                    final WatchKey watchKey = dir.register(watchService, StandardWatchEventKinds.ENTRY_DELETE,
                                            StandardWatchEventKinds.ENTRY_MODIFY);
                                    return new Registration(watchKey);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                int cnt = value.count.incrementAndGet();
                                LOG.debug("Already {} watchers for path {}", cnt, key);
                                return value;
                            }
                        });
                    });
        }

        /**
         * Stopn watching the JARs associated with the given {@code record} for deletions and modifications.
         *
         * @param record the {@link ValidableCacheRecord} to stop watching
         */
        void remove(ValidableCacheRecord record) {
            record.getArtifacts().stream()
                    .map(Artifact::getFile)
                    .map(File::toPath)
                    .forEach(p -> {
                        final Path dir = p.getParent();
                        registrationsByDir.compute(dir, (key, value) -> {
                            if (value == null) {
                                LOG.debug("Already unwatchers for path {}", key);
                                return null;
                            } else {
                                final int cnt = value.count.decrementAndGet();
                                if (cnt <= 0) {
                                    LOG.debug("Unwatching path {}", key);
                                    value.watchKey.cancel();
                                    return null;
                                } else {
                                    LOG.debug("Still {} watchers for path {}", cnt, key);
                                    return value;
                                }
                            }
                        });
                    });
        }

        /**
         * Poll for events and process them.
         */
        public void validateRecords() {
            for (Entry<Path, Registration> entry : registrationsByDir.entrySet()) {
                final Path dir = entry.getKey();
                final WatchKey watchKey = entry.getValue().watchKey;
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    Kind<?> kind = event.kind();
                    LOG.debug("Got watcher event {}", kind.name());
                    if (kind == StandardWatchEventKinds.ENTRY_DELETE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        final Path path = dir.resolve((Path) event.context());
                        final List<ValidableCacheRecord> records = validRecordsByPath.get(path);
                        LOG.debug("Records for path {}: {}", path, records);
                        if (records != null) {
                            synchronized (records) {
                                for (ValidableCacheRecord record : records) {
                                    LOG.debug("Invalidating recorder of path {}", path);
                                    record.valid = false;
                                    remove(record);
                                }
                                records.clear();
                            }
                        }
                    } else if (kind == StandardWatchEventKinds.OVERFLOW) {
                        /* Invalidate all records under the given dir */
                        for (Entry<Path, List<ValidableCacheRecord>> en : validRecordsByPath.entrySet()) {
                            final Path entryParent = en.getKey().getParent();
                            if (entryParent.equals(dir)) {
                                final List<ValidableCacheRecord> records = en.getValue();
                                if (records != null) {
                                    synchronized (records) {
                                        for (ValidableCacheRecord record : records) {
                                            record.valid = false;
                                            remove(record);
                                        }
                                        records.clear();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * A watcher registration for a directory storing the {@link WatchKey} and the count of watchers to be able to
         * tell when the {@link #watchKey} should be cancelled.
         */
        static class Registration {
            final AtomicInteger count = new AtomicInteger(1);
            final WatchKey watchKey;

            public Registration(WatchKey watchKey) {
                this.watchKey = watchKey;
            }
        }

        public ValidableCacheRecord newRecord(ClassRealm pluginRealm, List<Artifact> pluginArtifacts) {
            final ValidableCacheRecord result = new ValidableCacheRecord(pluginRealm, pluginArtifacts);
            add(result);
            return result;
        }

    }

    private static final Logger LOG = LoggerFactory.getLogger(CliPluginRealmCache.class);

    protected final Map<Key, ValidableCacheRecord> cache = new ConcurrentHashMap<>();
    private final RecordValidator watcher;

    public CliPluginRealmCache() {
        this.watcher = new RecordValidator();
    }

    public Key createKey(Plugin plugin, ClassLoader parentRealm, Map<String, ClassLoader> foreignImports,
            DependencyFilter dependencyFilter, List<RemoteRepository> repositories,
            RepositorySystemSession session) {
        return new CacheKey(plugin, parentRealm, foreignImports, dependencyFilter, repositories, session);
    }

    public CacheRecord get(Key key) {
        watcher.validateRecords();
        return cache.computeIfPresent(key, (k, r) -> {
            if (!r.isValid()) {
                r.dispose();
                return null;
            } else {
                return r;
            }
        });
    }

    public CacheRecord put(Key key, ClassRealm pluginRealm, List<Artifact> pluginArtifacts) {
        Objects.requireNonNull(pluginRealm, "pluginRealm cannot be null");
        Objects.requireNonNull(pluginArtifacts, "pluginArtifacts cannot be null");
        return cache.computeIfAbsent(key, k -> watcher.newRecord(pluginRealm, pluginArtifacts));
    }

    public void flush() {
        for (ValidableCacheRecord record : cache.values()) {
            record.dispose();
        }
        cache.clear();
    }

    public void register(MavenProject project, Key key, CacheRecord record) {
        // default cache does not track plugin usage
    }

    public void dispose() {
        flush();
    }

}
