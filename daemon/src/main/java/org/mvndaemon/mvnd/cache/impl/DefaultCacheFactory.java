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
package org.mvndaemon.mvnd.cache.impl;

import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.sisu.Priority;
import org.mvndaemon.mvnd.cache.Cache;
import org.mvndaemon.mvnd.cache.CacheFactory;
import org.mvndaemon.mvnd.cache.CacheRecord;
import org.mvndaemon.mvnd.common.Os;

@Named
@Singleton
@Priority(10)
public class DefaultCacheFactory implements CacheFactory {

    private final CacheFactory delegate;

    public DefaultCacheFactory() {
        /* java.nio.file.attribute.BasicFileAttributes.fileKey() is always null on Windows
         * and we do not hold relying solely on java.nio.file.attribute.BasicFileAttributes#lastModifiedTime()
         * for sufficient. So we rather rely on WatchService on Windows */
        this.delegate = Os.current() == Os.WINDOWS ? new WatchServiceCacheFactory() : new TimestampCacheFactory();
    }

    @Override
    public <K, V extends CacheRecord> Cache<K, V> newCache() {
        return delegate.newCache();
    }

}
