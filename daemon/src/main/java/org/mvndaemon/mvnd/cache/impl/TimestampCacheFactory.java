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
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.mvndaemon.mvnd.cache.Cache;
import org.mvndaemon.mvnd.cache.CacheFactory;
import org.mvndaemon.mvnd.cache.CacheRecord;

/**
 * A factory for {@link Cache} objects invalidating its entries based on {@link BasicFileAttributes#lastModifiedTime()}
 * and {@link java.nio.file.attribute.BasicFileAttributes#fileKey()}.
 */
public class TimestampCacheFactory implements CacheFactory {

    public TimestampCacheFactory() {}

    @Override
    public <K, V extends CacheRecord> Cache<K, V> newCache() {
        return new TimestampCache<>();
    }

    /**
     * A state of a file given by {@link BasicFileAttributes#lastModifiedTime()} and
     * {@link java.nio.file.attribute.BasicFileAttributes#fileKey()} at the time of {@link FileState} creation.
     */
    static class FileState {
        final Path path;
        final FileTime lastModifiedTime;
        final Object fileKey;

        FileState(Path path) {
            this.path = path;
            BasicFileAttributes attrs;
            try {
                attrs = Files.readAttributes(path, BasicFileAttributes.class);
            } catch (NoSuchFileException e) {
                // we allow this exception in case of a missing reactor artifact
                attrs = null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            this.lastModifiedTime = attrs != null ? attrs.lastModifiedTime() : FileTime.fromMillis(0);
            this.fileKey = attrs != null ? attrs.fileKey() : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FileState that = (FileState) o;
            return path.equals(that.path)
                    && Objects.equals(lastModifiedTime, that.lastModifiedTime)
                    && Objects.equals(fileKey, that.fileKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, lastModifiedTime, fileKey);
        }

        @Override
        public String toString() {
            return "FileState [path=" + path + ", lastModifiedTime=" + lastModifiedTime + ", fileKey=" + fileKey + "]";
        }
    }

    static class Record<V extends CacheRecord> {
        final V record;

        /** {@link Set} of {@link FileState}s at the creation time of this {@link Record} */
        final Set<FileState> fileStates;

        public Record(V record) {
            this.record = record;
            this.fileStates = currentFileStates();
        }

        /**
         * @return {@link Set} of {@link FileState}s at current time
         */
        private Set<FileState> currentFileStates() {
            return record.getDependencyPaths().map(FileState::new).collect(Collectors.toSet());
        }
    }

    static class TimestampCache<K, V extends CacheRecord> implements Cache<K, V> {

        private final ConcurrentHashMap<K, Record<V>> map = new ConcurrentHashMap<>();

        @Override
        public boolean contains(K key) {
            return this.get(key) != null;
        }

        @Override
        public V get(K key) {
            Record<V> record = map.compute(key, (k, v) -> {
                if (v != null) {
                    try {
                        final Set<FileState> currentFileStates = v.currentFileStates();
                        if (Objects.equals(v.fileStates, currentFileStates)) {
                            return v;
                        }
                    } catch (RuntimeException e) {
                        // ignore and invalidate the record
                    }
                    v.record.invalidate();
                    v = null;
                }
                return v;
            });
            return record != null ? record.record : null;
        }

        @Override
        public void put(K key, V value) {
            map.put(key, new Record<>(value));
        }

        @Override
        public void clear() {
            removeIf((k, v) -> true);
        }

        @Override
        public void removeIf(BiPredicate<K, V> predicate) {
            for (Iterator<Map.Entry<K, Record<V>>> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<K, Record<V>> entry = iterator.next();
                if (predicate.test(entry.getKey(), entry.getValue().record)) {
                    entry.getValue().record.invalidate();
                    iterator.remove();
                }
            }
        }

        @Override
        public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
            return map.compute(key, (k, v) -> {
                        if (v != null) {
                            try {
                                if (Objects.equals(v.fileStates, v.currentFileStates())) {
                                    return v;
                                }
                            } catch (RuntimeException e) {
                                // ignore and invalidate the record
                            }
                            v.record.invalidate();
                            v = null;
                        }
                        return new Record<>(mappingFunction.apply(k));
                    })
                    .record;
        }
    }
}
