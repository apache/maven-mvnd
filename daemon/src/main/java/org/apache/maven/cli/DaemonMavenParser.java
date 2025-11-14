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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.ParseException;
import org.apache.maven.api.cli.extensions.CoreExtension;
import org.apache.maven.api.cli.mvn.MavenOptions;
import org.apache.maven.cling.invoker.mvn.MavenParser;
import org.mvndaemon.mvnd.common.Environment;

public class DaemonMavenParser extends MavenParser {
    @Override
    protected MavenOptions parseArgs(String source, List<String> args) throws ParseException {
        return CommonsCliDaemonMavenOptions.parse(source, args.toArray(new String[0]));
    }

    @Override
    protected Map<String, String> populateSystemProperties(LocalContext context) {
        HashMap<String, String> systemProperties = new HashMap<>(super.populateSystemProperties(context));
        Map<String, String> env = context.parserRequest
                .lookup()
                .lookup(DaemonMavenCling.Environment.class)
                .get();
        systemProperties.putAll(env);
        return systemProperties;
    }

    @Override
    protected List<CoreExtension> readCoreExtensionsDescriptorFromFile(Path extensionsFile, boolean allowMetaVersions) {
        return filterCoreExtensions(super.readCoreExtensionsDescriptorFromFile(extensionsFile, allowMetaVersions));
    }

    protected static List<CoreExtension> filterCoreExtensions(List<CoreExtension> coreExtensions) {
        String exclusionsString = Environment.MVND_CORE_EXTENSIONS_EXCLUDE.asString();
        Set<String> exclusions = Arrays.stream(exclusionsString.split(","))
                .filter(e -> !e.trim().isEmpty())
                .collect(Collectors.toSet());
        if (!exclusions.isEmpty()) {
            return coreExtensions.stream()
                    .filter(e -> !exclusions.contains(e.getGroupId() + ":" + e.getArtifactId()))
                    .collect(Collectors.toList());
        } else {
            return coreExtensions;
        }
    }
}
