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
package org.mvndaemon.mvnd.cache.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mvndaemon.mvnd.cache.Cache;
import org.mvndaemon.mvnd.cache.CacheRecord;
import org.mvndaemon.mvnd.common.Os;

public class CacheFactoryTest {

    @Test
    void timestampCache(@TempDir Path tempDir) throws IOException, InterruptedException {
        assertPutGet(tempDir, new TimestampCacheFactory().newCache(), 200);
    }

    @Test
    void watchServiceCache(@TempDir Path tempDir) throws IOException, InterruptedException {
        int asyncOpDelayMs = Os.current() == Os.MAC ? 2500 : 200;
        assertPutGet(tempDir, new WatchServiceCacheFactory().newCache(), asyncOpDelayMs);
    }

    public void assertPutGet(Path tempDir, final Cache<String, CacheRecord> cache, int asyncOpDelayMs)
            throws IOException, InterruptedException {
        final Path file1 = tempDir.resolve("file1");
        Files.write(file1, "content1".getBytes(StandardCharsets.UTF_8));
        final SimpleCacheRecord record1 = new SimpleCacheRecord(file1);

        final Path file2 = tempDir.resolve("file2");
        Files.write(file2, "content2".getBytes(StandardCharsets.UTF_8));
        final SimpleCacheRecord record2 = new SimpleCacheRecord(file2);

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

        /* Make sure the new content is written a couple of ms later than the original lastModifiedTime */
        final long deadline = Files.readAttributes(file1, BasicFileAttributes.class)
                        .lastModifiedTime()
                        .toMillis()
                + 10;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(1);
        }

        Files.write(file1, "content1.1".getBytes(StandardCharsets.UTF_8));
        if (asyncOpDelayMs > 0) {
            Thread.sleep(asyncOpDelayMs);
        }

        Assertions.assertFalse(cache.contains(k1));
        Assertions.assertNull(cache.get(k1));
        Assertions.assertTrue(record1.invalidated);
        Assertions.assertTrue(cache.contains(k2));
        Assertions.assertEquals(record2, cache.get(k2));
        Assertions.assertFalse(record2.invalidated);

        Files.delete(file2);
        if (asyncOpDelayMs > 0) {
            Thread.sleep(asyncOpDelayMs);
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

        @Override
        public String toString() {
            return "SimpleCacheRecord [paths=" + paths + ", invalidated=" + invalidated + "]";
        }
    }
}
