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
package org.mvndaemon.mvnd.cache.factory.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mvndaemon.mvnd.cache.Cache;
import org.mvndaemon.mvnd.cache.CacheRecord;
import org.mvndaemon.mvnd.cache.impl.DefaultCacheFactory;
import org.mvndaemon.mvnd.common.Os;

public class TimestampCacheFactoryTest {

    @Test
    void putGet(@TempDir Path tempDir) throws IOException, InterruptedException {

        final Path file1 = tempDir.resolve("file1");
        Files.write(file1, "content1".getBytes(StandardCharsets.UTF_8));
        final SimpleCacheRecord record1 = new SimpleCacheRecord(file1);

        final Path file2 = tempDir.resolve("file2");
        Files.write(file2, "content2".getBytes(StandardCharsets.UTF_8));
        final SimpleCacheRecord record2 = new SimpleCacheRecord(file2);

        final Cache<String, CacheRecord> cache = new DefaultCacheFactory().newCache();

        final String k1 = "k1";
        cache.put(k1, record1);
        final String k2 = "k2";
        cache.put(k2, record2);

        Assertions.assertTrue(cache.contains(k1));
        Assertions.assertEquals(record1, cache.get(k1));
        Assertions.assertFalse(record1.invalidated);
        Assertions.assertTrue(cache.contains(k2));
        Assertions.assertEquals(record2, cache.get(k2));
        Assertions.assertFalse(record2.invalidated);

        Files.write(file1, "content1.1".getBytes(StandardCharsets.UTF_8));

        if (Os.current() == Os.WINDOWS) {
            Thread.sleep(3000);
        }

        Assertions.assertFalse(cache.contains(k1));
        Assertions.assertNull(cache.get(k1));
        Assertions.assertTrue(record1.invalidated);
        Assertions.assertTrue(cache.contains(k2));
        Assertions.assertEquals(record2, cache.get(k2));
        Assertions.assertFalse(record2.invalidated);

        Files.delete(file2);
        if (Os.current() == Os.WINDOWS) {
            Thread.sleep(3000);
        }
        Assertions.assertFalse(cache.contains(k2));
        Assertions.assertNull(cache.get(k2));
        Assertions.assertTrue(record2.invalidated);
    }

    static class SimpleCacheRecord implements org.mvndaemon.mvnd.cache.CacheRecord {

        private final List<Path> paths;
        private boolean invalidated = false;

        SimpleCacheRecord(Path... paths) {
            this.paths = Arrays.asList(paths);
        }

        @Override
        public Stream<Path> getDependencyPaths() {
            return paths.stream();
        }

        @Override
        public void invalidate() {
            this.invalidated = true;
        }

    }
}
