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
package org.apache.maven.project;

import java.util.Objects;
import java.util.function.Supplier;

import org.apache.maven.api.services.Source;
import org.apache.maven.api.services.model.ModelCache;

public class SnapshotModelCache implements ModelCache {

    private final ModelCache globalCache;
    private final ModelCache reactorCache;

    public SnapshotModelCache(ModelCache globalCache, ModelCache reactorCache) {
        this.globalCache = Objects.requireNonNull(globalCache);
        this.reactorCache = Objects.requireNonNull(reactorCache);
    }

    @Override
    public <T> T computeIfAbsent(String groupId, String artifactId, String version, String tag, Supplier<T> data) {
        return getDelegate(version).computeIfAbsent(groupId, artifactId, version, tag, data);
    }

    @Override
    public <T> T computeIfAbsent(Source path, String tag, Supplier<T> data) {
        return reactorCache.computeIfAbsent(path, tag, data);
    }

    @Override
    public void clear() {
        reactorCache.clear();
    }

    private ModelCache getDelegate(String version) {
        return version.contains("SNAPSHOT") || version.contains("${") ? reactorCache : globalCache;
    }
}
