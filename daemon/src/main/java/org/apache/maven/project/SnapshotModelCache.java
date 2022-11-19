/*
 * Copyright 2019 the original author or authors.
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
package org.apache.maven.project;

import org.apache.maven.model.building.ModelCache;

public class SnapshotModelCache implements ModelCache {

    private final ModelCache globalCache;
    private final ModelCache reactorCache;

    public SnapshotModelCache(ModelCache globalCache, ModelCache reactorCache) {
        this.globalCache = globalCache;
        this.reactorCache = reactorCache;
    }

    @Override
    public void put(String groupId, String artifactId, String version, String tag, Object data) {
        getDelegate(version).put(groupId, artifactId, version, tag, data);
    }

    @Override
    public Object get(String groupId, String artifactId, String version, String tag) {
        return getDelegate(version).get(groupId, artifactId, version, tag);
    }

    private ModelCache getDelegate(String version) {
        return version.contains("SNAPSHOT") || version.contains("${")
                ? reactorCache : globalCache;
    }
}
