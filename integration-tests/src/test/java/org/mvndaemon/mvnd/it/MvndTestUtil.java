/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mvndaemon.mvnd.it;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class MvndTestUtil {

    private MvndTestUtil() {
    }

    public static String plugin(Properties props, String artifactId) {
        return artifactId + ":" + props.getProperty(artifactId + ".version");
    }

    public static Properties properties(Path pomXmlPath) {
        try (Reader runtimeReader = Files.newBufferedReader(pomXmlPath, StandardCharsets.UTF_8)) {
            final MavenXpp3Reader rxppReader = new MavenXpp3Reader();
            return rxppReader.read(runtimeReader).getProperties();
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException("Could not read or parse " + pomXmlPath);
        }
    }

    public static String version(Path pomXmlPath) {
        try (Reader runtimeReader = Files.newBufferedReader(pomXmlPath, StandardCharsets.UTF_8)) {
            final MavenXpp3Reader rxppReader = new MavenXpp3Reader();
            return rxppReader.read(runtimeReader).getVersion();
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException("Could not read or parse " + pomXmlPath);
        }
    }

}
