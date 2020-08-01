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
package org.jboss.fuse.mvnd.plugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.enterprise.inject.Default;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
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
@Default
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
            this.foreignImports = (foreignImports != null) ? foreignImports : Collections.<String, ClassLoader> emptyMap();
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

    interface RecordValidator {
        void validateRecords();

        ValidableCacheRecord newRecord(ClassRealm pluginRealm, List<Artifact> pluginArtifacts);
    }

    static abstract class ValidableCacheRecord extends CacheRecord {

        public ValidableCacheRecord(ClassRealm realm, List<Artifact> artifacts) {
            super(realm, artifacts);
        }

        public abstract boolean isValid();

        public void dispose() {
            ClassRealm realm = getRealm();
            try {
                realm.getWorld().disposeRealm(realm.getId());
            } catch (NoSuchRealmException e) {
                // ignore
            }
        }
    }

    static class TimestampedRecordValidator implements RecordValidator {

        @Override
        public void validateRecords() {
        }

        @Override
        public ValidableCacheRecord newRecord(ClassRealm realm, List<Artifact> artifacts) {
            return new TimestampedCacheRecord(realm, artifacts);
        }

    }

    static class TimestampedCacheRecord extends ValidableCacheRecord {

        static class ArtifactTimestamp {
            final Path path;
            final FileTime lastModifiedTime;
            final Object fileKey;

            ArtifactTimestamp(Path path) {
                this.path = path;
                try {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    this.lastModifiedTime = attrs.lastModifiedTime();
                    this.fileKey = attrs.fileKey();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean equals(Object o) {
                if (this == o)
                    return true;
                if (o == null || getClass() != o.getClass())
                    return false;
                ArtifactTimestamp that = (ArtifactTimestamp) o;
                return path.equals(that.path) &&
                        Objects.equals(lastModifiedTime, that.lastModifiedTime) &&
                        Objects.equals(fileKey, that.fileKey);
            }

            @Override
            public int hashCode() {
                return Objects.hash(path, lastModifiedTime, fileKey);
            }
        }

        Set<ArtifactTimestamp> timestamp;

        public TimestampedCacheRecord(ClassRealm realm, List<Artifact> artifacts) {
            super(realm, artifacts);
            timestamp = current();
        }

        public boolean isValid() {
            try {
                return Objects.equals(current(), timestamp);
            } catch (Exception e) {
                return false;
            }
        }

        private Set<ArtifactTimestamp> current() {
            return getArtifacts().stream().map(Artifact::getFile)
                    .map(File::toPath)
                    .map(ArtifactTimestamp::new)
                    .collect(Collectors.toSet());
        }
    }

    /**
     * A {@link WatchService} with some methods to watch JARs associated with {@link WatchedCacheRecord}.
     */
    static class MultiWatcher implements RecordValidator {
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

        public MultiWatcher() {
            try {
                this.watchService = FileSystems.getDefault().newWatchService();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Watch the JARs associated with the given {@code record} for deletions and modifications.
         *
         * @param record the {@link WatchedCacheRecord} to watch
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
                                log.debug("Starting to watch path {}", key);
                                try {
                                    final WatchKey watchKey = dir.register(watchService, StandardWatchEventKinds.ENTRY_DELETE,
                                            StandardWatchEventKinds.ENTRY_MODIFY);
                                    return new Registration(watchKey);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                int cnt = value.count.incrementAndGet();
                                log.debug("Already {} watchers for path {}", cnt, key);
                                return value;
                            }
                        });
                    });
        }

        /**
         * Stopn watching the JARs associated with the given {@code record} for deletions and modifications.
         *
         * @param record the {@link WatchedCacheRecord} to stop watching
         */
        void remove(ValidableCacheRecord record) {
            record.getArtifacts().stream()
                    .map(Artifact::getFile)
                    .map(File::toPath)
                    .forEach(p -> {
                        final Path dir = p.getParent();
                        registrationsByDir.compute(dir, (key, value) -> {
                            if (value == null) {
                                log.debug("Already unwatchers for path {}", key);
                                return null;
                            } else {
                                final int cnt = value.count.decrementAndGet();
                                if (cnt <= 0) {
                                    log.debug("Unwatching path {}", key);
                                    value.watchKey.cancel();
                                    return null;
                                } else {
                                    log.debug("Still {} watchers for path {}", cnt, key);
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
                    log.debug("Got watcher event {}", kind.name());
                    if (kind == StandardWatchEventKinds.ENTRY_DELETE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        final Path path = dir.resolve((Path) event.context());
                        final List<ValidableCacheRecord> records = validRecordsByPath.get(path);
                        log.debug("Records for path {}: {}", path, records);
                        if (records != null) {
                            synchronized (records) {
                                for (ValidableCacheRecord record : records) {
                                    log.debug("Invalidating recorder of path {}", path);
                                    ((WatchedCacheRecord) record).valid = false;
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
                                            ((WatchedCacheRecord) record).valid = false;
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

        @Override
        public ValidableCacheRecord newRecord(ClassRealm pluginRealm, List<Artifact> pluginArtifacts) {
            final ValidableCacheRecord result = new WatchedCacheRecord(pluginRealm, pluginArtifacts);
            add(result);
            return result;
        }

    }

    static class WatchedCacheRecord extends ValidableCacheRecord {

        private volatile boolean valid = true;

        public WatchedCacheRecord(ClassRealm realm, List<Artifact> artifacts) {
            super(realm, artifacts);
        }

        public boolean isValid() {
            return valid;
        }

    }

    private static final Logger log = LoggerFactory.getLogger(CliPluginRealmCache.class);
    protected final Map<Key, ValidableCacheRecord> cache = new ConcurrentHashMap<>();
    private final RecordValidator watcher;
    private final EventSpy eventSpy = new EventSpy() {

        private Path multiModuleProjectDirectory;

        @Override
        public void onEvent(Object event) throws Exception {
            try {
                if (event instanceof MavenExecutionRequest) {
                    /*  Store the multiModuleProjectDirectory path */
                    multiModuleProjectDirectory = ((MavenExecutionRequest) event).getMultiModuleProjectDirectory().toPath()
                            .toRealPath();
                } else if (event instanceof MavenExecutionResult) {
                    /* Evict the entries refering to jars under multiModuleProjectDirectory */
                    final Iterator<Entry<Key, ValidableCacheRecord>> i = cache.entrySet().iterator();
                    while (i.hasNext()) {
                        final Entry<Key, ValidableCacheRecord> entry = i.next();
                        final ValidableCacheRecord record = entry.getValue();
                        for (URL url : record.getRealm().getURLs()) {
                            if (url.getProtocol().equals("file")) {
                                final Path path = Paths.get(url.toURI());
                                boolean remove = false;
                                if (path.startsWith(multiModuleProjectDirectory)) {
                                    remove = true;
                                } else if (Files.exists(path)) {
                                    /* Try to convert to real path only if the file exists */
                                    final Path realPath = path.toRealPath();
                                    if (realPath.startsWith(multiModuleProjectDirectory)) {
                                        remove = true;
                                    }
                                }
                                if (remove) {
                                    log.debug(
                                            "Removing PluginRealmCache entry {} because it refers to an artifact in the build tree {}",
                                            entry.getKey(), path);
                                    record.dispose();
                                    i.remove();
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not notify CliPluginRealmCache", e);
            }
        }

        @Override
        public void init(Context context) throws Exception {
        }

        @Override
        public void close() throws Exception {
        }
    };

    public CliPluginRealmCache() {
        this.watcher = System.getProperty("os.name").toLowerCase().contains("mac")
                ? new TimestampedRecordValidator()
                : new MultiWatcher();
    }

    public Key createKey(Plugin plugin, ClassLoader parentRealm, Map<String, ClassLoader> foreignImports,
            DependencyFilter dependencyFilter, List<RemoteRepository> repositories,
            RepositorySystemSession session) {
        return new CacheKey(plugin, parentRealm, foreignImports, dependencyFilter, repositories, session);
    }

    public CacheRecord get(Key key) {
        watcher.validateRecords();
        ValidableCacheRecord record = cache.get(key);
        if (record != null && !record.isValid()) {
            record.dispose();
            record = null;
            cache.remove(key);
        }
        return record;
    }

    public CacheRecord put(Key key, ClassRealm pluginRealm, List<Artifact> pluginArtifacts) {
        Objects.requireNonNull(pluginRealm, "pluginRealm cannot be null");
        Objects.requireNonNull(pluginArtifacts, "pluginArtifacts cannot be null");

        if (cache.containsKey(key)) {
            throw new IllegalStateException("Duplicate plugin realm for plugin " + key);
        }

        ValidableCacheRecord record = watcher.newRecord(pluginRealm, pluginArtifacts);
        cache.put(key, record);

        return record;
    }

    public void flush() {
        for (ValidableCacheRecord record : cache.values()) {
            record.dispose();
        }
        cache.clear();
    }

    protected static int pluginHashCode(Plugin plugin) {
        return CliCacheUtils.pluginHashCode(plugin);
    }

    protected static boolean pluginEquals(Plugin a, Plugin b) {
        return CliCacheUtils.pluginEquals(a, b);
    }

    public void register(MavenProject project, Key key, CacheRecord record) {
        // default cache does not track plugin usage
    }

    public void dispose() {
        flush();
    }

    public EventSpy asEventSpy() {
        return eventSpy;
    }

}
