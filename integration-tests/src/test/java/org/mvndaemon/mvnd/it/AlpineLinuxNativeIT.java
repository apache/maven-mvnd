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
package org.mvndaemon.mvnd.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AlpineLinuxNativeIT {

    private ImageFromDockerfile image;
    private String mvndHome;

    @BeforeAll
    void createImage() {
        image = new ImageFromDockerfile()
                .withDockerfileFromBuilder(builder -> builder.from("bellsoft/liberica-openjdk-alpine-musl:latest")
                        .run("apk add gcompat")
                        .build());
        mvndHome = System.getProperty("mvnd.home");
        if (mvndHome == null) {
            throw new IllegalStateException("System property mnvd.home is undefined");
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void testAlpineJvm() throws Exception {
        String logs;
        try (GenericContainer<?> alpine = new GenericContainer<>(image)
                .withFileSystemBind(mvndHome, "/mvnd")
                .withCommand(isNative() ? "/mvnd/bin/mvnd" : "/mvnd/bin/mvnd.sh", "-v")) {
            alpine.start();
            while (alpine.isRunning()) {
                Thread.sleep(100);
            }
            logs = alpine.getLogs();
        }
        assertTrue(logs.contains("Apache Maven Daemon"), logs);
    }

    protected boolean isNative() {
        return true;
    }
}
