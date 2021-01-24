/*
 * Copyright 2020 the original author or authors.
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
package org.mvndaemon.mvnd.cache.invalidating;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.eclipse.sisu.Typed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
@Singleton
@Typed(EventSpy.class)
public class InvalidatingPluginRealmCacheEventSpy extends AbstractEventSpy {

    private static final Logger LOG = LoggerFactory.getLogger(InvalidatingPluginRealmCacheEventSpy.class);

    private final InvalidatingPluginRealmCache cache;
    private Path multiModuleProjectDirectory;

    @Inject
    public InvalidatingPluginRealmCacheEventSpy(InvalidatingPluginRealmCache cache) {
        this.cache = cache;
    }

    @Override
    public void onEvent(Object event) throws Exception {
        try {
            if (event instanceof MavenExecutionRequest) {
                /*  Store the multiModuleProjectDirectory path */
                multiModuleProjectDirectory = ((MavenExecutionRequest) event).getMultiModuleProjectDirectory().toPath();
            } else if (event instanceof MavenExecutionResult) {
                /* Evict the entries referring to jars under multiModuleProjectDirectory */
                cache.cache.removeIf(this::shouldEvict);
            }
        } catch (Exception e) {
            LOG.warn("Could not notify CliPluginRealmCache", e);
        }
    }

    private boolean shouldEvict(InvalidatingPluginRealmCache.Key k, InvalidatingPluginRealmCache.Record v) {
        try {
            for (URL url : v.record.getRealm().getURLs()) {
                if (url.getProtocol().equals("file")) {
                    final Path path = Paths.get(url.toURI());
                    if (path.startsWith(multiModuleProjectDirectory)) {
                        LOG.debug(
                                "Removing PluginRealmCache entry {} because it refers to an artifact in the build tree {}",
                                k, path);
                        return true;
                    }
                }
            }
            return false;
        } catch (URISyntaxException e) {
            return true;
        }
    }
}
