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

import javax.xml.stream.XMLStreamException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.ParseException;
import org.apache.maven.api.cli.ParserException;
import org.apache.maven.api.cli.extensions.CoreExtension;
import org.apache.maven.api.cli.mvn.MavenOptions;
import org.apache.maven.cling.internal.extension.io.CoreExtensionsStaxReader;
import org.apache.maven.cling.invoker.mvn.MavenParser;
import org.mvndaemon.mvnd.common.Environment;

public class DaemonMavenParser extends MavenParser {
    @Override
    protected MavenOptions parseArgs(String source, List<String> args) throws ParserException {
        try {
            return CommonsCliDaemonMavenOptions.parse(source, args.toArray(new String[0]));
        } catch (ParseException e) {
            throw new ParserException("Failed to parse source " + source + ": " + e.getMessage(), e.getCause());
        }
    }

    @Override
    protected Map<String, String> populateSystemProperties(LocalContext context) throws ParserException {
        HashMap<String, String> systemProperties = new HashMap<>(super.populateSystemProperties(context));
        Map<String, String> env = context.parserRequest
                .lookup()
                .lookup(DaemonMavenCling.Environment.class)
                .get();
        systemProperties.putAll(env);
        return systemProperties;
    }

    @Override
    protected List<CoreExtension> readCoreExtensionsDescriptor(LocalContext context) {
        String coreExtensionsFilePath = Environment.MVND_CORE_EXTENSIONS_FILE_PATH.asString();
        if (!coreExtensionsFilePath.isEmpty()) {
            try {
                return readCoreExtensionsDescriptor(Path.of(coreExtensionsFilePath));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            return new ArrayList<>();
        }
    }

    private List<CoreExtension> readCoreExtensionsDescriptor(Path extensionsFile)
            throws IOException, XMLStreamException {

        CoreExtensionsStaxReader parser = new CoreExtensionsStaxReader();
        List<CoreExtension> extensions;
        try (InputStream is = Files.newInputStream(extensionsFile)) {
            extensions = parser.read(is).getExtensions();
        }
        return filterCoreExtensions(extensions);
    }

    private static List<CoreExtension> filterCoreExtensions(List<CoreExtension> coreExtensions) {
        String exclusionsString = Environment.MVND_CORE_EXTENSIONS_EXCLUDE.asString();
        Set<String> exclusions = Arrays.stream(exclusionsString.split(","))
                .filter(e -> e != null && !e.trim().isEmpty())
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
