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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.api.cache.RequestCache;
import org.apache.maven.api.cache.RequestCacheFactory;
import org.apache.maven.api.services.VersionParser;
import org.eclipse.sisu.Typed;
import org.mvndaemon.mvnd.cache.Cache;
import org.mvndaemon.mvnd.cache.CacheFactory;

@Named
@Singleton
@Typed(RequestCacheFactory.class)
public class InvalidatingRequestCacheFactory implements RequestCacheFactory {

    private final CacheFactory cacheFactory;
    private final VersionParser versionParser;
    private final Cache<?, ?> forever;

    @Inject
    public InvalidatingRequestCacheFactory(CacheFactory cacheFactory, VersionParser versionParser) {
        this.cacheFactory = cacheFactory;
        this.versionParser = versionParser;
        this.forever = cacheFactory.newWeakCache();
    }

    @Override
    public RequestCache createCache() {
        return new InvalidatingRequestCache(cacheFactory, versionParser, forever);
    }
}
