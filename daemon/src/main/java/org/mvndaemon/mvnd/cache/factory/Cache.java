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

import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Cache containing records that can be invalidated.
 *
 * Whenever the paths associated to a given {@link CacheRecord} have been modified,
 * the record will be invalidated using {@link CacheRecord#invalidate()}.
 *
 * @param <K>
 * @param <V>
 */
public interface Cache<K, V extends CacheRecord> {

    /**
     * Check if the cache contains the given key
     */
    boolean contains(K key);

    /**
     * Get the cached record for the key
     */
    V get(K key);

    /**
     * Put a record in the cache
     */
    void put(K key, V value);

    /**
     * Remove all cached records
     */
    void clear();

    /**
     * Remove cached records according to the predicate
     */
    void removeIf(BiPredicate<K, V> predicate);

    /**
     * Get or compute the cached value if absent and return it.
     */
    V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

}
