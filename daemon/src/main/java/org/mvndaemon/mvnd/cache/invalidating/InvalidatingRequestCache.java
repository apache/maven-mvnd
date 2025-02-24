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

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.maven.api.Session;
import org.apache.maven.api.SessionData;
import org.apache.maven.api.cache.CacheMetadata;
import org.apache.maven.api.cache.CacheRetention;
import org.apache.maven.api.services.Request;
import org.apache.maven.api.services.RequestTrace;
import org.apache.maven.api.services.Result;
import org.apache.maven.api.services.VersionParser;
import org.apache.maven.api.services.model.ModelResolver;
import org.apache.maven.impl.cache.AbstractRequestCache;
import org.apache.maven.impl.cache.CachingSupplier;
import org.apache.maven.impl.cache.WeakIdentityMap;
import org.apache.maven.impl.model.DefaultModelBuilder;
import org.mvndaemon.mvnd.cache.Cache;
import org.mvndaemon.mvnd.cache.CacheFactory;
import org.mvndaemon.mvnd.cache.CacheRecord;

public class InvalidatingRequestCache extends AbstractRequestCache {

    protected static final SessionData.Key<WeakIdentityMap> KEY =
            SessionData.key(WeakIdentityMap.class, CacheMetadata.class);
    protected static final Object ROOT = new Object();

    protected final Cache<Object, Record<?, ?>> forever;

    protected final CacheFactory cacheFactory;
    protected final VersionParser versionParser;

    public InvalidatingRequestCache(CacheFactory cacheFactory, VersionParser versionParser) {
        this.cacheFactory = cacheFactory;
        this.versionParser = versionParser;
        this.forever = cacheFactory.newCache();
    }

    @SuppressWarnings("unchecked")
    protected <REQ extends Request<?>, REP extends Result<REQ>> CachingSupplier<REQ, REP> doCache(
            REQ req, Function<REQ, REP> supplier) {

        CacheRetention retention = Objects.requireNonNullElse(
                req instanceof CacheMetadata metadata ? metadata.getCacheRetention() : null,
                CacheRetention.SESSION_SCOPED);
        Function<REQ, Record<REQ, REP>> record;
        if (req instanceof DefaultModelBuilder.RgavCacheKey rgavCacheKey) {
            // retention = versionParser.isSnapshot(rgavCacheKey.version())
            //                ? CacheRetention.REQUEST_SCOPED
            //                : CacheRetention.PERSISTENT;
            record = k -> new Record<>(supplier, List.of());
        } else if (req instanceof DefaultModelBuilder.SourceCacheKey sourceCacheKey) {
            retention = CacheRetention.PERSISTENT;
            Path path = sourceCacheKey.source().getPath();
            record = k -> new Record<>(supplier, path != null ? List.of(path) : List.of());
        } else if (req instanceof ModelResolver.ModelResolverRequest modelResolverRequest) {
            // retention = versionParser.isSnapshot(modelResolverRequest.version())
            //                ? CacheRetention.REQUEST_SCOPED
            //                : CacheRetention.PERSISTENT;
            record = k -> new Record<>(supplier, List.of());
        } else {
            // retention = Objects.requireNonNullElse(
            //                req instanceof CacheMetadata metadata ? metadata.getCacheRetention() : null,
            //                CacheRetention.PERSISTENT);
            record = k -> new Record<>(supplier, List.of());
        }
        Cache<REQ, Record<REQ, REP>> cache;
        if ((retention == CacheRetention.REQUEST_SCOPED || retention == CacheRetention.SESSION_SCOPED)
                && req.getSession() instanceof Session session) {
            Object key = retention == CacheRetention.REQUEST_SCOPED ? doGetOuterRequest(req) : ROOT;
            WeakIdentityMap<Object, Cache<REQ, Record<REQ, REP>>> caches =
                    session.getData().computeIfAbsent(KEY, WeakIdentityMap::new);
            cache = caches.computeIfAbsent(key, k -> cacheFactory.newCache());
        } else if (retention == CacheRetention.PERSISTENT) {
            cache = (Cache) forever;
        } else {
            return record.apply(req);
        }
        return cache.computeIfAbsent(req, record);
    }

    private <REQ extends Request<?>> Object doGetOuterRequest(REQ req) {
        RequestTrace trace = req.getTrace();
        while (trace != null && trace.parent() != null) {
            trace = trace.parent();
        }
        return trace != null && trace.data() != null ? trace.data() : req;
    }

    static class Record<K, V> extends CachingSupplier<K, V> implements CacheRecord {
        final List<Path> paths;

        Record(Function<K, V> supplier, Collection<Path> paths) {
            super(supplier);
            this.paths = paths != null ? List.copyOf(paths) : List.of();
        }

        @Override
        public Stream<Path> getDependencyPaths() {
            return paths.stream();
        }
    }
}
