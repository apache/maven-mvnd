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
package org.jboss.fuse.mvnd.client;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.stream.Stream;

public class Layout {

    private static Layout ENV_INSTANCE;

    private final Path mavenHome;
    private final Path userDir;
    private final Path multiModuleProjectDirectory;

    public Layout(Path mavenHome, Path userDir, Path multiModuleProjectDirectory) {
        super();
        this.mavenHome = mavenHome;
        this.userDir = userDir;
        this.multiModuleProjectDirectory = multiModuleProjectDirectory;
    }

    public Path mavenHome() {
        return mavenHome;
    }

    public Path userDir() {
        return userDir;
    }

    public Path registry() {
        return mavenHome.resolve("daemon/registry.bin");
    }

    public Path daemonLog(String daemon) {
        return mavenHome.resolve("daemon/daemon-" + daemon + ".log");
    }

    public Path multiModuleProjectDirectory() {
        return multiModuleProjectDirectory;
    }

    public static boolean isNative() {
        return "executable".equals(System.getProperty("org.graalvm.nativeimage.kind"));
    }

    public static Layout getEnvInstance() {
        if (ENV_INSTANCE == null) {
            final Properties mvndProperties = loadMvndProperties();
            final Path pwd = Paths.get(".").toAbsolutePath().normalize();

            ENV_INSTANCE = new Layout(
                    findMavenHome(mvndProperties),
                    pwd,
                    findMultiModuleProjectDirectory(pwd));
        }
        return ENV_INSTANCE;
    }


    static Properties loadMvndProperties() {
        final Properties result = new Properties();
        final Path mvndPropsPath = Paths.get(System.getProperty("user.home")).resolve(".m2/mvnd.properties");
        if (Files.exists(mvndPropsPath)) {
            try (InputStream in = Files.newInputStream(mvndPropsPath)) {
                result.load(in);
            } catch (IOException e) {
                throw new RuntimeException("Could not read " + mvndPropsPath);
            }
        }
        return result;
    }

    static Path findMavenHome(Properties mvndProperties) {
        String rawValue = System.getenv("MAVEN_HOME");
        if (rawValue == null) {
            rawValue = System.getProperty("maven.home");
        }
        if (isNative()) {
            try {
                final Path nativeExecutablePath = Paths.get(Class.forName("org.graalvm.nativeimage.ProcessProperties").getMethod("getExecutableName").invoke(null).toString()).toAbsolutePath().normalize();
                final Path bin = nativeExecutablePath.getParent();
                if (bin.getFileName().toString().equals("bin")) {
                    final Path candidateMvnHome = bin.getParent();
                    final Path libExt = candidateMvnHome.resolve("lib/ext");
                    if (Files.isDirectory(libExt)) {
                        try (Stream<Path> files = Files.list(libExt)) {
                            if (files.filter(path -> path.getFileName().toString().startsWith("mvnd-")).findFirst().isPresent()) {
                                rawValue = candidateMvnHome.toString();
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Could not list " + libExt);
                        }
                    }
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                    | SecurityException | ClassNotFoundException e) {
                throw new RuntimeException("Could not invoke org.graalvm.nativeimage.ProcessProperties.getExecutableName() via reflection");
            }
        }
        if (rawValue == null) {
            rawValue = mvndProperties.getProperty("maven.home");
        }
        if (rawValue == null) {
            throw new IllegalStateException("Either environment variable MAVEN_HOME or maven.home property in ~/.m2/mvnd.properties or system property maven.home must be set");
        }
        return Paths.get(rawValue).toAbsolutePath().normalize();
    }

    static Path findMultiModuleProjectDirectory(Path pwd) {
        final String multiModuleProjectDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleProjectDirectory != null) {
            return Paths.get(multiModuleProjectDirectory).toAbsolutePath().normalize();
        }
        Path dir = pwd;
        do {
            if (Files.isDirectory(dir.resolve(".mvn"))) {
                return dir.toAbsolutePath().normalize();
            }
            dir = dir.getParent();
        } while (dir != null);
        throw new IllegalStateException("Could not detect maven.multiModuleProjectDirectory by climbing up from ["+ pwd +"] seeking a .mvn directory. You may want to create a .mvn directory in the root directory of your source tree.");
    }

    @Override
    public String toString() {
        return "Layout [mavenHome=" + mavenHome + ", userDir=" + userDir + ", multiModuleProjectDirectory="
                + multiModuleProjectDirectory + "]";
    }

}
