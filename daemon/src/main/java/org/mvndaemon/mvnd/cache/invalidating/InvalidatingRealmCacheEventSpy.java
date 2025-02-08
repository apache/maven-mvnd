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
package org.mvndaemon.mvnd.cache.invalidating;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.project.MavenProject;
import org.eclipse.sisu.Typed;
import org.mvndaemon.mvnd.common.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
@Typed(EventSpy.class)
public class InvalidatingRealmCacheEventSpy extends AbstractEventSpy {

    private static final Logger LOG = LoggerFactory.getLogger(InvalidatingRealmCacheEventSpy.class);

    private final InvalidatingPluginRealmCache pluginCache;
    private final InvalidatingExtensionRealmCache extensionCache;
    private final InvalidatingProjectArtifactsCache projectArtifactsCache;
    private Path multiModuleProjectDirectory;
    private String pattern;
    private PathMatcher matcher;

    @Inject
    public InvalidatingRealmCacheEventSpy(
            InvalidatingPluginRealmCache cache,
            InvalidatingExtensionRealmCache extensionCache,
            InvalidatingProjectArtifactsCache projectArtifactsCache) {
        this.pluginCache = cache;
        this.extensionCache = extensionCache;
        this.projectArtifactsCache = projectArtifactsCache;
    }

    @Override
    public void onEvent(Object event) throws Exception {
        try {
            if (event instanceof MavenExecutionRequest) {
                /*  Store the multiModuleProjectDirectory path */
                multiModuleProjectDirectory = ((MavenExecutionRequest) event)
                        .getMultiModuleProjectDirectory()
                        .toPath();
                pattern = Environment.MVND_PLUGIN_REALM_EVICT_PATTERN
                        .asOptional()
                        .orElse(Environment.MVND_PLUGIN_REALM_EVICT_PATTERN.getDefault());
                if (!pattern.isEmpty()) {
                    String[] patterns = pattern.split(",");
                    List<PathMatcher> matchers = new ArrayList<>();
                    for (String pattern : patterns) {
                        if (pattern.startsWith("mvn:")) {
                            String[] parts = pattern.substring("mvn:".length()).split(":");
                            String groupId, artifactId, version;
                            if (parts.length >= 3) {
                                version = parts[2];
                            } else {
                                version = "*";
                            }
                            if (parts.length >= 2) {
                                groupId = parts[0];
                                artifactId = parts[1];
                            } else {
                                groupId = "*";
                                artifactId = parts[0];
                            }
                            pattern = "glob:**/" + ("*".equals(groupId) ? "" : groupId.replace('.', '/') + "/")
                                    + artifactId + "/" + ("*".equals(version) ? "**" : version + "/**");
                        }
                        matchers.add(getPathMatcher(pattern));
                    }
                    if (matchers.size() == 1) {
                        matcher = matchers.iterator().next();
                    } else {
                        matcher = path -> matchers.stream().anyMatch(f -> f.matches(path));
                    }
                }
            } else if (event instanceof MavenExecutionResult) {
                /* Evict the entries referring to jars under multiModuleProjectDirectory */
                pluginCache.cache.removeIf(this::shouldEvict);
                extensionCache.cache.removeIf(this::shouldEvict);
                MavenExecutionResult mer = (MavenExecutionResult) event;
                List<MavenProject> projects = mer.getTopologicallySortedProjects();
                projectArtifactsCache.cache.removeIf(
                        (k, r) -> shouldEvict(projects, (InvalidatingProjectArtifactsCache.CacheKey) k, r));
            }
        } catch (Exception e) {
            LOG.warn("Could not notify CliPluginRealmCache", e);
        }
    }

    private boolean shouldEvict(
            List<MavenProject> projects,
            InvalidatingProjectArtifactsCache.CacheKey k,
            InvalidatingProjectArtifactsCache.Record v) {
        return projects.stream().anyMatch(p -> k.matches(p.getGroupId(), p.getArtifactId(), p.getVersion()));
    }

    private boolean shouldEvict(InvalidatingPluginRealmCache.Key k, InvalidatingPluginRealmCache.Record v) {
        try {
            for (URL url : v.record.getRealm().getURLs()) {
                if (url.getProtocol().equals("file")) {
                    final Path path = Paths.get(url.toURI());
                    if (path.startsWith(multiModuleProjectDirectory)) {
                        LOG.debug(
                                "Removing PluginRealmCache entry {} because it refers to an artifact in the build tree {}",
                                k,
                                path);
                        return true;
                    } else if (matcher != null && matcher.matches(path)) {
                        LOG.debug(
                                "Removing PluginRealmCache entry {} because its components {} matches the eviction pattern '{}'",
                                k,
                                path,
                                pattern);
                        return true;
                    }
                }
            }
            return false;
        } catch (URISyntaxException e) {
            return true;
        }
    }

    private boolean shouldEvict(InvalidatingExtensionRealmCache.Key k, InvalidatingExtensionRealmCache.Record v) {
        try {
            for (URL url : v.record.getRealm().getURLs()) {
                if (url.getProtocol().equals("file")) {
                    final Path path = Paths.get(url.toURI());
                    if (path.startsWith(multiModuleProjectDirectory)) {
                        LOG.debug(
                                "Removing ExtensionRealmCache entry {} because it refers to an artifact in the build tree {}",
                                k,
                                path);
                        return true;
                    } else if (matcher != null && matcher.matches(path)) {
                        LOG.debug(
                                "Removing ExtensionRealmCache entry {} because its components {} matches the eviction pattern '{}'",
                                k,
                                path,
                                pattern);
                        return true;
                    }
                }
            }
            return false;
        } catch (URISyntaxException e) {
            return true;
        }
    }

    private static PathMatcher getPathMatcher(String pattern) {
        if (!pattern.startsWith("glob:") && !pattern.startsWith("regex:")) {
            pattern = "glob:" + pattern;
        }
        return FileSystems.getDefault().getPathMatcher(pattern);
    }
}
