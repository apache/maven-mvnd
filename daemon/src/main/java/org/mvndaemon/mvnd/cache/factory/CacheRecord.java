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

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Data stored in a {@link Cache} depending on the state of a collection of files.
 */
public interface CacheRecord {

    /**
     * @return a {@link Stream} of file (not directory) {@link Path}s whose modification or deletion causes invalidation
     *         of this {@link CacheRecord}.
     */
    Stream<Path> getDependencyPaths();

    /**
     * Callback called by the cache when this {@link CacheRecord} is removed from the cache.
     */
    void invalidate();

}
