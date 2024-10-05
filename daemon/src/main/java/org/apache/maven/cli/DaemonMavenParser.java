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
package org.apache.maven.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.cli.ParseException;
import org.apache.maven.api.cli.Options;
import org.apache.maven.api.cli.ParserException;
import org.apache.maven.api.cli.ParserRequest;
import org.apache.maven.api.cli.extensions.CoreExtension;
import org.apache.maven.api.cli.mvn.MavenInvokerRequest;
import org.apache.maven.api.cli.mvn.MavenOptions;
import org.apache.maven.cling.invoker.mvn.BaseMavenParser;
import org.apache.maven.cling.invoker.mvn.DefaultMavenInvokerRequest;
import org.mvndaemon.mvnd.common.Environment;

public class DaemonMavenParser extends BaseMavenParser<MavenOptions, MavenInvokerRequest<MavenOptions>> {
    @Override
    protected DefaultMavenInvokerRequest<MavenOptions> getInvokerRequest(
            ParserRequest parserRequest,
            Path cwd,
            Path installationDirectory,
            Path userHomeDirectory,
            Map<String, String> userProperties,
            Map<String, String> systemProperties,
            Path topDirectory,
            Path rootDirectory,
            ArrayList<CoreExtension> extensions,
            Options options) {
        return new DefaultMavenInvokerRequest<>(
                parserRequest,
                cwd,
                installationDirectory,
                userHomeDirectory,
                userProperties,
                systemProperties,
                topDirectory,
                rootDirectory,
                parserRequest.in(),
                parserRequest.out(),
                parserRequest.err(),
                extensions,
                (DaemonMavenOptions) options);
    }

    @Override
    protected Path getRootDirectory(ParserRequest parserRequest, Path cwd, Path topDirectory) throws ParserException {
        Map<String, String> env = parserRequest
                .lookup()
                .lookup(DaemonMavenCling.Environment.class)
                .getEnvironment();
        System.setProperty("maven.multiModuleProjectDirectory", env.get("maven.multiModuleProjectDirectory"));
        return super.getRootDirectory(parserRequest, cwd, topDirectory);
    }

    @Override
    protected MavenOptions parseArgs(String source, List<String> args) throws ParserException {
        try {
            return CommonsCliDaemonMavenOptions.parse(source, args.toArray(new String[0]));
        } catch (ParseException e) {
            throw new ParserException("Failed to parse source " + source, e.getCause());
        }
    }

    @Override
    protected MavenOptions assembleOptions(List<MavenOptions> parsedOptions) {
        return LayeredDaemonMavenOptions.layerDaemonMavenOptions(
                parsedOptions.stream().map(o -> (DaemonMavenOptions) o).toList());
    }

    @Override
    protected List<CoreExtension> readCoreExtensionsDescriptor(String extensionsFile, Path cwd) throws ParserException {
        return Stream.of(Environment.MVND_CORE_EXTENSIONS.asString().split(";"))
                .filter(s -> s != null && !s.isEmpty())
                .map(s -> {
                    String[] parts = s.split(":");
                    return CoreExtension.newBuilder()
                            .groupId(parts[0])
                            .artifactId(parts[1])
                            .version(parts[2])
                            .build();
                })
                .toList();
    }
}
