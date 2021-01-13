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
 * Data stored in a {@link Cache} which depends on the state
 * of a few {@link Path}s.
 */
public interface CacheRecord {

    /**
     * A list of Path that will invalidate this record if modified.
     */
    Stream<Path> getDependentPaths();

    /**
     * Callback called by the cache when this record
     * is removed from the cache.
     */
    void invalidate();

}
