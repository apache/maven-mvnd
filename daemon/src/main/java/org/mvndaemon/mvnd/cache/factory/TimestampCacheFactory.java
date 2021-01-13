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
package org.mvndaemon.mvnd.cache.factory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.codehaus.plexus.logging.AbstractLogEnabled;

/**
 * A factory for {@link Cache} objects.
 */
@Named
@Singleton
public class TimestampCacheFactory extends AbstractLogEnabled implements CacheFactory {

    public TimestampCacheFactory() {
    }

    @Override
    public <K, V extends CacheRecord> Cache<K, V> newCache() {
        return new DefaultCache<>();
    }

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

    static class Record<V extends CacheRecord> {
        final V record;
        final Set<ArtifactTimestamp> timestamp;

        public Record(V record) {
            this.record = record;
            this.timestamp = current();
        }

        private Set<ArtifactTimestamp> current() {
            return record.getDependentPaths()
                    .map(ArtifactTimestamp::new)
                    .collect(Collectors.toSet());
        }
    }

    static class DefaultCache<K, V extends CacheRecord> implements Cache<K, V> {

        private final ConcurrentHashMap<K, Record<V>> map = new ConcurrentHashMap<>();

        @Override
        public boolean contains(K key) {
            return map.containsKey(key);
        }

        @Override
        public V get(K key) {
            Record<V> record = map.compute(key, (k, v) -> {
                if (v != null) {
                    try {
                        if (Objects.equals(v.timestamp, v.current())) {
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
            for (Iterator<Map.Entry<K, Record<V>>> iterator = map.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<K, Record<V>> entry = iterator.next();
                if (predicate.test(entry.getKey(), entry.getValue().record)) {
                    entry.getValue().record.invalidate();
                    iterator.remove();
                }
            }
        }

    }
}
