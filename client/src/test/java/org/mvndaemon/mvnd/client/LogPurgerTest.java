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
package org.mvndaemon.mvnd.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogPurgerTest {

    @Test
    void purgeKeepsLogsOfActiveDaemons(@TempDir Path dir) throws Exception {
        FileTime old = FileTime.from(Instant.now().minusSeconds(30L * 24 * 3600));
        for (String name : Arrays.asList("daemon-abc.log", "daemon-abc.out.log", "daemon-gone.log")) {
            Path p = Files.createFile(dir.resolve(name));
            Files.setLastModifiedTime(p, old);
        }
        LogPurger.purge(dir, Duration.ofDays(7), Collections.singleton("abc"));
        assertTrue(Files.exists(dir.resolve("daemon-abc.log")));
        assertTrue(Files.exists(dir.resolve("daemon-abc.out.log")));
        assertFalse(Files.exists(dir.resolve("daemon-gone.log")));
    }
}
