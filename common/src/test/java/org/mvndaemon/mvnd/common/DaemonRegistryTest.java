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
package org.mvndaemon.mvnd.common;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DaemonRegistryTest {

    private PrintStream oldSysErr;
    private PrintStream newSysErr;

    @BeforeEach
    void setup() {
        oldSysErr = System.err;
        newSysErr = new PrintStream(new ByteArrayOutputStream());
        System.setErr(newSysErr);
    }

    @AfterEach
    void tearDown() {
        System.setErr(oldSysErr);
    }

    @Test
    public void testReadWrite() throws IOException {
        Path temp = File.createTempFile("reg", ".data").toPath();
        try (DaemonRegistry reg1 = new DaemonRegistry(temp);
                DaemonRegistry reg2 = new DaemonRegistry(temp)) {
            assertNotNull(reg1.getAll());
            assertEquals(0, reg1.getAll().size());
            assertNotNull(reg2.getAll());
            assertEquals(0, reg2.getAll().size());

            byte[] token = new byte[16];
            new Random().nextBytes(token);
            reg1.store(new DaemonInfo(
                    "12345678",
                    "/java/home/",
                    "/data/reg/",
                    0x12345678,
                    "inet:/127.0.0.1:7502",
                    token,
                    Locale.getDefault().toLanguageTag(),
                    Arrays.asList("-Xmx"),
                    DaemonState.Idle,
                    System.currentTimeMillis(),
                    System.currentTimeMillis()));

            assertNotNull(reg1.getAll());
            assertEquals(1, reg1.getAll().size());
            assertNotNull(reg2.getAll());
            assertEquals(1, reg2.getAll().size());
        }
    }

    @Test
    public void testBigRegistry() throws IOException {
        int nbDaemons = 512;

        Path temp = File.createTempFile("reg", ".data").toPath();
        Random random = new Random();
        try (DaemonRegistry reg = new DaemonRegistry(temp)) {
            for (int i = 0; i < nbDaemons; i++) {
                byte[] token = new byte[16];
                random.nextBytes(token);
                reg.store(new DaemonInfo(
                        UUID.randomUUID().toString(),
                        "/java/home/",
                        "/data/reg/",
                        random.nextInt(),
                        "inet:/127.0.0.1:7502",
                        token,
                        Locale.getDefault().toLanguageTag(),
                        Collections.singletonList("-Xmx"),
                        DaemonState.Idle,
                        System.currentTimeMillis(),
                        System.currentTimeMillis()));
            }
        }

        long size = Files.size(temp);
        assertTrue(size >= 64 * 1024);
        try (DaemonRegistry reg = new DaemonRegistry(temp)) {
            assertEquals(nbDaemons, reg.getAll().size());
        }

        try (DaemonRegistry reg = new DaemonRegistry(temp)) {
            for (int i = 0; i < nbDaemons / 2; i++) {
                List<DaemonInfo> list = reg.getAll();
                reg.remove(list.get(random.nextInt(list.size())).getId());
            }
        }

        long size2 = Files.size(temp);
        assertTrue(size2 < 64 * 1024);
        try (DaemonRegistry reg = new DaemonRegistry(temp)) {
            assertEquals(nbDaemons / 2, reg.getAll().size());
        }
    }

    @Test
    public void testRecovery() throws IOException {
        Path temp = File.createTempFile("reg", ".data").toPath();
        temp.toFile().deleteOnExit();
        try (DaemonRegistry reg1 = new DaemonRegistry(temp)) {
            // first store daemon
            byte[] token = new byte[16];
            new Random().nextBytes(token);
            reg1.store(new DaemonInfo(
                    "12345678",
                    "/java/home/",
                    "/data/reg/",
                    0x12345678,
                    "inet:/127.0.0.1:7502",
                    token,
                    Locale.getDefault().toLanguageTag(),
                    Arrays.asList("-Xmx"),
                    DaemonState.Idle,
                    System.currentTimeMillis(),
                    System.currentTimeMillis()));
            assertEquals(1, reg1.getAll().size());
            reg1.storeStopEvent(new DaemonStopEvent(
                    "11111", System.currentTimeMillis(), DaemonExpirationStatus.QUIET_EXPIRE, "because"));
            assertEquals(1, reg1.doGetDaemonStopEvents().size());
            Files.writeString(temp, "Foobar");
            // check if registry is reset
            assertEquals(0, reg1.getAll().size());
            assertEquals(0, reg1.doGetDaemonStopEvents().size());
        }
    }

    @Test
    public void testThreadSafety() throws Exception {
        int nbDaemons = 512;

        Path temp = File.createTempFile("reg", ".data").toPath();
        Random random = new Random();

        CompletableFuture.allOf(IntStream.range(0, nbDaemons)
                        .mapToObj(i -> CompletableFuture.runAsync(() -> {
                            try (DaemonRegistry reg = new DaemonRegistry(temp)) {
                                byte[] token = new byte[16];
                                random.nextBytes(token);
                                reg.store(new DaemonInfo(
                                        UUID.randomUUID().toString(),
                                        "/java/home/",
                                        "/data/reg/",
                                        random.nextInt(),
                                        "inet:/127.0.0.1:7502",
                                        token,
                                        Locale.getDefault().toLanguageTag(),
                                        Collections.singletonList("-Xmx"),
                                        DaemonState.Idle,
                                        System.currentTimeMillis(),
                                        System.currentTimeMillis()));
                            }
                        }))
                        .toList()
                        .toArray(new CompletableFuture[0]))
                .get();

        List<DaemonInfo> all;
        try (DaemonRegistry reg = new DaemonRegistry(temp)) {
            all = reg.getAll();
            assertEquals(nbDaemons, all.size());
        }

        Set<DaemonInfo> toRemove = new HashSet<>(all);
        for (int i = 0; i < nbDaemons / 2; i++) {
            toRemove.add(all.get(random.nextInt(all.size())));
        }

        CompletableFuture.allOf(toRemove.stream()
                        .map(info -> CompletableFuture.runAsync(() -> {
                            try (DaemonRegistry reg = new DaemonRegistry(temp)) {
                                reg.remove(info.getId());
                            }
                        }))
                        .toList()
                        .toArray(new CompletableFuture[0]))
                .get();

        try (DaemonRegistry reg = new DaemonRegistry(temp)) {
            assertEquals(nbDaemons - toRemove.size(), reg.getAll().size());
        }
    }

    @Test
    public void testMultiProcessSafety() throws Exception {
        int nbDaemons = 64;

        Path temp = File.createTempFile("reg", ".data").toPath();
        Random random = new Random();

        CompletableFuture.allOf(IntStream.range(0, nbDaemons)
                        .mapToObj(i -> {
                            try {
                                return new ProcessBuilder(
                                                "java",
                                                "-cp",
                                                System.getProperty("java.class.path"),
                                                "org.mvndaemon.mvnd.common.RegistryMutator",
                                                "add",
                                                temp.toString())
                                        .start()
                                        .onExit();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .toList()
                        .toArray(new CompletableFuture[0]))
                .get();

        List<DaemonInfo> all;
        try (DaemonRegistry reg = new DaemonRegistry(temp)) {
            all = reg.getAll();
            assertEquals(nbDaemons, all.size());
        }

        Set<DaemonInfo> toRemove = new HashSet<>(all);
        for (int i = 0; i < nbDaemons / 2; i++) {
            toRemove.add(all.get(random.nextInt(all.size())));
        }

        CompletableFuture.allOf(toRemove.stream()
                        .map(info -> {
                            try {
                                return new ProcessBuilder(
                                                "java",
                                                "-cp",
                                                System.getProperty("java.class.path"),
                                                "org.mvndaemon.mvnd.common.RegistryMutator",
                                                "remove",
                                                temp.toString(),
                                                info.getId())
                                        .start()
                                        .onExit();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .toList()
                        .toArray(new CompletableFuture[0]))
                .get();

        try (DaemonRegistry reg = new DaemonRegistry(temp)) {
            assertEquals(nbDaemons - toRemove.size(), reg.getAll().size());
        }
    }
}
