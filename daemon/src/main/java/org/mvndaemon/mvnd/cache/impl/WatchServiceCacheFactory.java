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
package org.mvndaemon.mvnd.cache.impl;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.mvndaemon.mvnd.cache.Cache;
import org.mvndaemon.mvnd.cache.CacheFactory;
import org.mvndaemon.mvnd.cache.CacheRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory for {@link Cache} objects invalidating its entries based on events received from {@link WatchService}.
 */
public class WatchServiceCacheFactory implements CacheFactory {

    private static final Logger LOG = LoggerFactory.getLogger(WatchServiceCacheFactory.class);

    private final WatchService watchService;

    /**
     * Records that have no been invalidated so far. From watched JAR paths to records (because one JAR can be
     * present in multiple records)
     */
    private final Map<Path, List<CacheRecord>> recordsByPath = new ConcurrentHashMap<>();

    /**
     * {@link WatchService} can watch only directories but we actually want to watch files. So here we store
     * for the given parent directory the count of its child files we watch.
     */
    private final Map<Path, Registration> registrationsByDir = new ConcurrentHashMap<>();

    public WatchServiceCacheFactory() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Watch the JARs associated with the given {@code record} for deletions and modifications.
     *
     * @param record the {@link CacheRecord} to watch
     */
    public void add(CacheRecord record) {
        record.getDependencyPaths().forEach(p -> {
            final List<CacheRecord> records = recordsByPath.computeIfAbsent(p, k -> new ArrayList<>());
            synchronized (records) {
                records.add(record);
                registrationsByDir.compute(p.getParent(), this::register);
            }
        });
    }

    @Override
    public <K, V extends CacheRecord> Cache<K, V> newCache() {
        return new WatchServiceCache<>();
    }

    private Registration register(Path key, Registration value) {
        if (value == null) {
            LOG.debug("Starting to watch path {}", key);
            try {
                WatchEvent.Modifier[] mods;
                try {
                    mods = new WatchEvent.Modifier[] {com.sun.nio.file.SensitivityWatchEventModifier.HIGH};
                } catch (Throwable t) {
                    mods = null;
                }
                final WatchKey watchKey = key.register(
                        watchService,
                        new WatchEvent.Kind[] {
                            StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY
                        },
                        mods);
                return new Registration(watchKey);
            } catch (NoSuchFileException e) {
                // we allow this exception in case of a missing reactor artifact
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            int cnt = value.count.incrementAndGet();
            LOG.debug("Already {} watchers for path {}", cnt, key);
            return value;
        }
    }

    /**
     * Poll for events and process them.
     */
    public void validateRecords() {
        for (Map.Entry<Path, Registration> entry : registrationsByDir.entrySet()) {
            final WatchKey watchKey = entry.getValue().watchKey;
            for (WatchEvent<?> event : watchKey.pollEvents()) {
                final Path dir = entry.getKey();
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.ENTRY_DELETE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    final Path path = dir.resolve((Path) event.context());
                    LOG.debug("Got watcher event {} for file {}", kind.name(), path);
                    final List<CacheRecord> records = recordsByPath.remove(path);
                    if (records != null) {
                        synchronized (records) {
                            LOG.debug("Invalidating records for path {}: {}", path, records);
                            remove(records);
                        }
                    }
                } else if (kind == StandardWatchEventKinds.OVERFLOW) {
                    /* Invalidate all records under the given dir */
                    LOG.debug("Got overflow event for path {}", dir);
                    Iterator<Map.Entry<Path, List<CacheRecord>>> it =
                            recordsByPath.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Path, List<CacheRecord>> en = it.next();
                        final Path path = en.getKey();
                        if (path.getParent().equals(dir)) {
                            it.remove();
                            final List<CacheRecord> records = en.getValue();
                            if (records != null) {
                                synchronized (records) {
                                    LOG.debug("Invalidating records of path {}: {}", path, records);
                                    remove(records);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Stop watching the JARs associated with the given {@code record} for deletions and modifications.
     *
     * @param records the {@link CacheRecord}s to stop watching
     */
    void remove(List<CacheRecord> records) {
        for (CacheRecord record : records) {
            record.invalidate();
            record.getDependencyPaths()
                    .map(Path::getParent)
                    .forEach(dir -> registrationsByDir.compute(dir, this::unregister));
        }
    }

    private Registration unregister(Path key, Registration value) {
        if (value == null) {
            LOG.debug("Already stopped watching path {}", key);
            return null;
        } else {
            final int cnt = value.count.decrementAndGet();
            if (cnt <= 0) {
                LOG.debug("Unwatching path {}", key);
                value.watchKey.cancel();
                return null;
            } else {
                LOG.debug("Still " + cnt + " watchers for path {}", key);
                return value;
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

        Registration(WatchKey watchKey) {
            this.watchKey = watchKey;
        }
    }

    class WatchServiceCache<K, V extends CacheRecord> implements Cache<K, V> {

        private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();

        @Override
        public boolean contains(K key) {
            return get(key) != null;
        }

        @Override
        public V get(K key) {
            validateRecords();
            return map.get(key);
        }

        @Override
        public void put(K key, V value) {
            add(new WrappedCacheRecord<>(map, key, value));
            map.put(key, value);
        }

        @Override
        public void clear() {
            removeIf((k, v) -> true);
        }

        @Override
        public void removeIf(BiPredicate<K, V> predicate) {
            for (Iterator<Map.Entry<K, V>> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<K, V> entry = iterator.next();
                if (predicate.test(entry.getKey(), entry.getValue())) {
                    entry.getValue().invalidate();
                    iterator.remove();
                }
            }
        }

        @Override
        public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
            validateRecords();
            return map.computeIfAbsent(key, k -> {
                V v = mappingFunction.apply(k);
                add(new WrappedCacheRecord<>(map, k, v));
                return v;
            });
        }
    }

    static class WrappedCacheRecord<K, V extends CacheRecord> implements CacheRecord {
        private final Map<K, V> map;
        private final K key;
        private final V delegate;

        public WrappedCacheRecord(Map<K, V> map, K key, V delegate) {
            this.map = map;
            this.key = key;
            this.delegate = delegate;
        }

        @Override
        public Stream<Path> getDependencyPaths() {
            return delegate.getDependencyPaths();
        }

        @Override
        public void invalidate() {
            delegate.invalidate();
            map.remove(key);
        }
    }
}
