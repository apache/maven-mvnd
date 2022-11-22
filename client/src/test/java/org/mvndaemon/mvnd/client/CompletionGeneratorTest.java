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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.common.Environment.OptionOrigin;
import org.mvndaemon.mvnd.common.IoUtils;

public class CompletionGeneratorTest {

    @Test
    void generate() throws IOException {

        String template = IoUtils.readResource(
                Completion.class.getClassLoader(), "completion-templates/mvnd-bash-completion.bash");

        final String shortOpts = Stream.of(Environment.values())
                .filter(env -> !env.isInternal())
                .flatMap(env -> env.getOptionMap().entrySet().stream())
                .filter(optEntry -> optEntry.getValue() == OptionOrigin.mvnd)
                .map(Map.Entry::getKey)
                .filter(opt -> !opt.startsWith("--"))
                .sorted()
                .collect(Collectors.joining("|"));

        final String longOpts = Stream.of(Environment.values())
                .filter(env -> !env.isInternal())
                .flatMap(env -> env.getOptionMap().entrySet().stream())
                .filter(optEntry -> optEntry.getValue() == OptionOrigin.mvnd)
                .map(Map.Entry::getKey)
                .filter(opt -> opt.startsWith("--"))
                .sorted()
                .collect(Collectors.joining("|"));

        final String props = Stream.of(Environment.values())
                .filter(env -> !env.isInternal())
                .map(Environment::getProperty)
                .filter(Objects::nonNull)
                .sorted()
                .map(prop -> "-D" + prop)
                .collect(Collectors.joining("|"));

        template = template.replace("%mvnd_opts%", shortOpts);
        template = template.replace("%mvnd_long_opts%", longOpts);
        template = template.replace("%mvnd_properties%", props);

        final Path baseDir = Paths.get(System.getProperty("project.basedir", "."));

        final byte[] bytes = template.getBytes(StandardCharsets.UTF_8);
        Files.write(baseDir.resolve("../dist/src/main/distro/bin/mvnd-bash-completion.bash"), bytes);
    }
}
