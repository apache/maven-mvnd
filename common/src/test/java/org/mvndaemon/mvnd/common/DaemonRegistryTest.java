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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        assertTrue(size >= 128 * 1024);
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
        assertTrue(size2 < 128 * 1024);
        try (DaemonRegistry reg = new DaemonRegistry(temp)) {
            assertEquals(nbDaemons / 2, reg.getAll().size());
        }
    }

    @Test
    public void testRecovery() throws IOException {
        Path temp = File.createTempFile("reg", ".data").toPath();
        temp.toFile().deleteOnExit();
        try (TestDaemonRegistry reg1 = new TestDaemonRegistry(temp)) {
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
            // store an invalid event to trigger recovery
            StringBuilder sb = new StringBuilder(1024);
            for (int i = 0; i < 1024; i++) {
                sb.append('…');
            }
            reg1.storeStopEvent(new DaemonStopEvent(
                    "11111", System.currentTimeMillis(), DaemonExpirationStatus.QUIET_EXPIRE, sb.toString()));
            assertEquals(1, reg1.doGetDaemonStopEvents().size());
            // check if registry is reset
            assertEquals(0, reg1.getAll().size());
            assertEquals(0, reg1.doGetDaemonStopEvents().size());
        }
    }

    static class TestDaemonRegistry extends DaemonRegistry {
        public TestDaemonRegistry(Path registryFile) {
            super(registryFile);
        }

        @Override
        protected void writeString(String str) {
            ByteBuffer buffer = buffer();
            if (str == null) {
                buffer.putShort((short) -1);
            } else {
                byte[] buf = str.getBytes(StandardCharsets.UTF_8);
                buffer.putShort((short) buf.length);
                buffer.put(buf);
            }
        }
    }
}
