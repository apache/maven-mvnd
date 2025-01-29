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
package org.mvndaemon.mvnd.plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.artifact.repository.metadata.io.MetadataReader;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.version.PluginVersionRequest;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.plugin.version.PluginVersionResolver;
import org.apache.maven.plugin.version.PluginVersionResult;
import org.apache.maven.plugin.version.internal.DefaultPluginVersionResolver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.SessionData;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.version.VersionScheme;
import org.eclipse.sisu.Priority;
import org.eclipse.sisu.Typed;

@Named
@Singleton
@Priority(10)
@Typed(PluginVersionResolver.class)
public class CachingPluginVersionResolver extends DefaultPluginVersionResolver {

    private static final Object CACHE_KEY = new Object();

    @Inject
    public CachingPluginVersionResolver(
            RepositorySystem repositorySystem,
            MetadataReader metadataReader,
            MavenPluginManager pluginManager,
            VersionScheme versionScheme) {
        super(repositorySystem, metadataReader, pluginManager, versionScheme);
    }

    @Override
    public PluginVersionResult resolve(PluginVersionRequest request) throws PluginVersionResolutionException {
        Map<String, PluginVersionResult> cache =
                getCache(request.getRepositorySession().getData());
        String key = getKey(request);
        PluginVersionResult result = cache.get(key);
        if (result == null) {
            result = super.resolve(request);
            cache.putIfAbsent(key, result);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, PluginVersionResult> getCache(SessionData data) {
        Map<String, PluginVersionResult> cache = (Map<String, PluginVersionResult>) data.get(CACHE_KEY);
        while (cache == null) {
            cache = new ConcurrentHashMap<>(256);
            if (data.set(CACHE_KEY, null, cache)) {
                break;
            }
            cache = (Map<String, PluginVersionResult>) data.get(CACHE_KEY);
        }
        return cache;
    }

    private static String getKey(PluginVersionRequest request) {
        return Stream.concat(
                        Stream.of(request.getGroupId(), request.getArtifactId()),
                        request.getRepositories().stream().map(RemoteRepository::getId))
                .collect(Collectors.joining(":"));
    }
}
