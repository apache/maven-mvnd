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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mvndaemon.mvnd.common.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultClientTest {

    @Test
    void blankArgumentsAreDropped() {
        List<String> args = new ArrayList<>(Arrays.asList("clean", "", "  ", "install"));
        DefaultClient.removeBlankArguments(args);
        assertEquals(Arrays.asList("clean", "install"), args);
    }

    @Test
    void builderFromMavenConfigIsNotOverridden() {
        List<String> args = new ArrayList<>(Arrays.asList("clean"));
        DefaultClient.addDefaultOption(Environment.MVND_BUILDER, args, Arrays.asList("-b", "turbo"), () -> "smart");
        assertEquals(Arrays.asList("clean"), args);
    }

    @Test
    void builderFromCommandLineWinsOverDefault() {
        List<String> args = new ArrayList<>(Arrays.asList("--builder=turbo", "clean"));
        DefaultClient.addDefaultOption(Environment.MVND_BUILDER, args, Arrays.asList(), () -> "smart");
        assertEquals(Arrays.asList("clean", "-b", "turbo"), args);
    }

    @Test
    void defaultBuilderIsAddedOtherwise() {
        List<String> args = new ArrayList<>(Arrays.asList("clean"));
        DefaultClient.addDefaultOption(Environment.MVND_BUILDER, args, Arrays.asList("-T4"), () -> "smart");
        assertEquals(Arrays.asList("clean", "-b", "smart"), args);
    }

    @Test
    void readsMavenConfig(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("maven.config");
        Files.write(config, Arrays.asList("-bturbo", "", "  -T 4  ", "-Dfoo=bar"));
        assertEquals(Arrays.asList("-bturbo", "-T", "4", "-Dfoo=bar"), DaemonParameters.readMavenConfig(config));
        assertEquals(Arrays.asList(), DaemonParameters.readMavenConfig(dir.resolve("missing")));
    }
}
