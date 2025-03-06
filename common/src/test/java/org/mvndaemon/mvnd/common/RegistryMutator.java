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

import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * A class for testing multi-process safety in {@link DaemonRegistryTest}
 */
public class RegistryMutator {
    public static void main(String[] args) {
        Random random = new Random();
        if (args[0].equals("add")) {
            try (DaemonRegistry reg = new DaemonRegistry(Path.of(args[1]))) {
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
        } else {
            try (DaemonRegistry reg = new DaemonRegistry(Path.of(args[1]))) {
                reg.remove(args[2]);
            }
        }
    }
}
