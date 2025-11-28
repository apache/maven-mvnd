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

import javax.xml.stream.XMLStreamException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.maven.model.v4.MavenStaxReader;

public class MvndTestUtil {

    private MvndTestUtil() {}

    public static Map<String, String> properties(Path pomXmlPath) {
        try (Reader runtimeReader = Files.newBufferedReader(pomXmlPath, StandardCharsets.UTF_8)) {
            final MavenStaxReader rxppReader = new MavenStaxReader();
            return rxppReader.read(runtimeReader).getProperties();
        } catch (IOException | XMLStreamException e) {
            throw new RuntimeException("Could not read or parse " + pomXmlPath);
        }
    }

    public static String version(Path pomXmlPath) {
        try (Reader runtimeReader = Files.newBufferedReader(pomXmlPath, StandardCharsets.UTF_8)) {
            final MavenStaxReader rxppReader = new MavenStaxReader();
            return rxppReader.read(runtimeReader).getVersion();
        } catch (IOException | XMLStreamException e) {
            throw new RuntimeException("Could not read or parse " + pomXmlPath);
        }
    }
}
