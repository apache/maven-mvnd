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
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.apache.maven.api.cli.mvn.MavenInvokerRequest;
import org.apache.maven.api.cli.mvn.MavenOptions;
import org.apache.maven.cling.invoker.PlexusContainerCapsuleFactory;
import org.apache.maven.cling.invoker.mvn.resident.DefaultResidentMavenInvoker;
import org.codehaus.plexus.logging.LoggerManager;
import org.mvndaemon.mvnd.common.Environment;
import org.mvndaemon.mvnd.logging.internal.Slf4jLoggerManager;

public class DaemonPlexusContainerCapsuleFactory
        extends PlexusContainerCapsuleFactory<
                MavenOptions, MavenInvokerRequest<MavenOptions>, DefaultResidentMavenInvoker.LocalContext> {
    private final Slf4jLoggerManager slf4jLoggerManager = new Slf4jLoggerManager();

    @Override
    protected LoggerManager createLoggerManager() {
        return slf4jLoggerManager;
    }

    @Override
    protected List<Path> parseExtClasspath(DefaultResidentMavenInvoker.LocalContext context) throws Exception {
        return Stream.of(Environment.MVND_EXT_CLASSPATH.asString().split(","))
                .filter(s -> s != null && !s.isEmpty())
                .map(Paths::get)
                .toList();
    }
}
