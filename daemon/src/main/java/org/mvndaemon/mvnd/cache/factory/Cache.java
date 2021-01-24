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
 * A cache containing records that can be invalidated.
 *
 * Whenever the paths associated with the given {@link CacheRecord} have been modified,
 * the record will be invalidated using {@link CacheRecord#invalidate()}.
 *
 * @param <K> the type of cache keys
 * @param <V> the type of cache values
 */
public interface Cache<K, V extends CacheRecord> {

    /**
     * Check if the cache contains the given key
     */
    boolean contains(K key);

    /**
     * Get the cached record for the key
     *
     * @param  key the key to search for
     * @return     the {@link CacheRecord} associated with the given {@code key}
     */
    V get(K key);

    /**
     * Put the given {@link CacheRecord} into the cache under the given {@code key}
     *
     * @param key   the key to store the given {@code value} under
     * @param value the value to store under the given {@code key}
     */
    void put(K key, V value);

    /**
     * Remove all cached records
     */
    void clear();

    /**
     * Remove all records satisfying the given predicate
     */
    void removeIf(BiPredicate<K, V> predicate);

    /**
     * Get or compute the cached value if absent and return it.
     *
     * @param  key             the key to search for
     * @param  mappingFunction the function to use for the computation of the new {@link CacheRecord} if the key is not
     *                         available in this {@link Cache} yet
     * @return                 the existing or newly computed {@link CacheRecord}
     */
    V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

}
