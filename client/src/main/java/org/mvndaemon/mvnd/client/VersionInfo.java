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
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.mvndaemon.mvnd.common.Environment;

/**
 * Builds the lines that {@code mvnd --version} prints in place of Maven, in the same layout as {@code mvn --version},
 * without needing a daemon or any Maven class on the client classpath.
 */
final class VersionInfo {

    private VersionInfo() {}

    /**
     * Whether the arguments ask for nothing but the version, so that it can be answered without a daemon. A
     * quiet or help request, or an mvnd command such as {@code --status}, still takes the regular path.
     */
    static boolean isPlainVersionRequest(List<String> args) {
        return Environment.MAVEN_VERSION.hasCommandLineOption(args)
                && !Environment.STATUS.hasCommandLineOption(args)
                && !Environment.STOP.hasCommandLineOption(args)
                && !Environment.PURGE.hasCommandLineOption(args)
                && !Environment.COMPLETION.hasCommandLineOption(args)
                && !args.contains("-q")
                && !args.contains("--quiet")
                && !args.contains("-h")
                && !args.contains("--help");
    }

    /**
     * Mirrors {@code CLIReportingUtils.showVersion()} of Maven 3.9.
     *
     * @param mavenLib the {@code mvn/lib} directory of the distribution, may be {@code null}
     * @param embeddedMavenVersion the Maven version recorded when mvnd was built, used if the Maven jar cannot be read
     * @param mavenHome the value of {@code maven.home} the daemon runs with, i.e. the {@code mvn} directory of the
     *     distribution
     * @param javaHome the Java home of the JVM the daemon runs in; if {@code null}, the client JVM is described
     * @param sys the client JVM system properties
     */
    static List<String> lines(
            Path mavenLib, String embeddedMavenVersion, Path mavenHome, Path javaHome, Properties sys) {
        List<String> lines = new ArrayList<>();
        Properties maven = readMavenBuildProperties(mavenLib);
        lines.add(mavenLine(maven, embeddedMavenVersion));
        lines.add(maven.getProperty("distributionShortName", "Maven") + " home: " + mavenHome);

        String javaVersion;
        String javaVendor;
        String runtime;
        if (javaHome != null) {
            Properties release = readProperties(javaHome.resolve("release"));
            javaVersion = unquote(release.getProperty("JAVA_VERSION"), "<unknown Java version>");
            javaVendor = unquote(release.getProperty("IMPLEMENTOR"), "<unknown vendor>");
            runtime = javaHome.toString();
        } else {
            javaVersion = sys.getProperty("java.version", "<unknown Java version>");
            javaVendor = sys.getProperty("java.vendor", "<unknown vendor>");
            runtime = sys.getProperty("java.home", "<unknown runtime>");
        }
        lines.add("Java version: " + javaVersion + ", vendor: " + javaVendor + ", runtime: " + runtime);

        lines.add("Default locale: " + Locale.getDefault() + ", platform encoding: "
                + sys.getProperty("file.encoding", "<unknown encoding>"));

        String osName = sys.getProperty("os.name", "");
        lines.add("OS name: \"" + osName.toLowerCase(Locale.ENGLISH) + "\", version: \""
                + sys.getProperty("os.version", "").toLowerCase(Locale.ENGLISH) + "\", arch: \""
                + sys.getProperty("os.arch", "").toLowerCase(Locale.ENGLISH) + "\", family: \"" + osFamily(osName)
                + "\"");
        return lines;
    }

    private static Properties readMavenBuildProperties(Path mavenLib) {
        if (mavenLib != null && Files.isDirectory(mavenLib)) {
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(mavenLib, "maven-core-*.jar")) {
                for (Path jar : jars) {
                    Properties p = readFromJar(jar, "org/apache/maven/messages/build.properties");
                    if (p.getProperty("version") != null) {
                        return p;
                    }
                }
            } catch (IOException e) {
                // fall through to the recorded version
            }
        }
        return new Properties();
    }

    private static String mavenLine(Properties maven, String embeddedMavenVersion) {
        String name = maven.getProperty("distributionName", "Apache Maven");
        String version = maven.getProperty("version");
        String build = maven.getProperty("buildNumber");
        if (version == null) {
            return name + " " + embeddedMavenVersion + " (embedded)";
        }
        return name + " " + version + (build != null && !build.trim().isEmpty() ? " (" + build + ")" : "");
    }

    private static Properties readFromJar(Path jar, String entryName) {
        Properties p = new Properties();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry != null) {
                try (InputStream is = zip.getInputStream(entry)) {
                    p.load(is);
                }
            }
        } catch (IOException e) {
            // empty properties
        }
        return p;
    }

    private static Properties readProperties(Path file) {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream is = Files.newInputStream(file)) {
                p.load(is);
            } catch (IOException e) {
                // empty properties
            }
        }
        return p;
    }

    private static String unquote(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v.isEmpty() ? fallback : v;
    }

    static String osFamily(String osName) {
        String n = osName.toLowerCase(Locale.ENGLISH);
        if (n.contains("windows")) {
            return "windows";
        } else if (n.contains("mac")) {
            return "mac";
        }
        return "unix";
    }
}
