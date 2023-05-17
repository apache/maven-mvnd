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

import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class AlpineLinuxTest {

//    @Container
//    GenericContainer alpine = new GenericContainer(
//        new ImageFromDockerfile()
//            .withDockerfileFromBuilder(builder ->
//                    builder
//                        .from("alpine:3.16")
//                        .run("apk add gcompat")
//                        .cmd("/bin/sh")
//                        .build()));

    @Test
    void testAlpine() throws Exception {
        String mvndHome = System.getProperty("mvnd.home");
        if (mvndHome == null) {
            throw new IllegalStateException("System property mnvd.home is undefined");
        }
        String logs;
        try (GenericContainer alpine = new GenericContainer(
            new ImageFromDockerfile()
                .withDockerfileFromBuilder(builder ->
                    builder
                        .from("bellsoft/liberica-openjdk-alpine-musl:latest")
                        .run("apk add gcompat")
                        .cmd("/mvnd/bin/mvnd.sh", "-v")
                        .build()))
            .withFileSystemBind(mvndHome, "/mvnd", BindMode.READ_WRITE)) {

            alpine.withStartupAttempts(1).withStartupTimeout(Duration.ofSeconds(3));
            alpine.start();
            while (alpine.isRunning()) {
                Thread.sleep(100);
            }
            logs = alpine.getLogs();
        }
        System.err.println(logs);
    }

}
