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
package org.mvndaemon.mvnd.cli;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jline.utils.ExecHelper;
import org.mvndaemon.mvnd.common.JavaVersion;
import org.mvndaemon.mvnd.common.Os;
import org.mvndaemon.mvnd.nativ.CLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnvHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvHelper.class);

    private EnvHelper() {}

    public static void environment(String workingDir, Map<String, String> clientEnv) {
        environment(workingDir, clientEnv, LOGGER::warn);
    }

    public static void environment(String workingDir, Map<String, String> clientEnv, Consumer<String> log) {
        Map<String, String> requested = new TreeMap<>(clientEnv);
        Map<String, String> actual = new TreeMap<>(System.getenv());
        requested.put("PWD", Os.current().isCygwin() ? toCygwin(workingDir) : workingDir);
        List<String> diffs = Stream.concat(requested.keySet().stream(), actual.keySet().stream())
                .sorted()
                .distinct()
                .filter(s -> !s.startsWith("JAVA_MAIN_CLASS_"))
                .filter(key -> !Objects.equals(requested.get(key), actual.get(key)))
                .collect(Collectors.toList());
        try {
            for (String key : diffs) {
                String vr = requested.get(key);
                int rc = CLibrary.setenv(key, vr);
                if (Os.current() == Os.WINDOWS ^ rc != 0) {
                    log.accept(String.format("Error setting environment value %s = %s", key, vr));
                }
            }
            setEnv(requested);
            chDir(workingDir);
        } catch (Exception e) {
            log.accept("Environment mismatch ! Could not set the environment (" + e + ")");
        }
        Map<String, String> nactual = new TreeMap<>(System.getenv());
        diffs = Stream.concat(requested.keySet().stream(), actual.keySet().stream())
                .sorted()
                .distinct()
                .filter(s -> !s.startsWith("JAVA_MAIN_CLASS_"))
                .filter(key -> !Objects.equals(requested.get(key), nactual.get(key)))
                .collect(Collectors.toList());
        if (!diffs.isEmpty()) {
            log.accept("A few environment mismatches have been detected between the client and the daemon.");
            diffs.forEach(key -> {
                String vr = requested.get(key);
                String va = nactual.get(key);
                log.accept(String.format(
                        "   %s -> %s instead of %s",
                        key, va != null ? "'" + va + "'" : "<null>", vr != null ? "'" + vr + "'" : "<null>"));
            });
            log.accept("If the difference matters to you, stop the running daemons using mvnd --stop and");
            log.accept("start a new daemon from the current environment by issuing any mvnd build command.");
        }
    }

    static String toCygwin(String path) {
        if (path.length() >= 3 && ":\\".equals(path.substring(1, 3))) {
            try {
                String p = path.endsWith("\\") ? path.substring(0, path.length() - 1) : path;
                return ExecHelper.exec(false, "cygpath", p).trim();
            } catch (IOException e) {
                String root = path.substring(0, 1);
                String p = path.substring(3);
                return "/cygdrive/" + root.toLowerCase(Locale.ROOT) + "/" + p.replace('\\', '/');
            }
        }
        return path;
    }

    static void chDir(String workingDir) throws Exception {
        CLibrary.chdir(workingDir);
        System.setProperty("user.dir", workingDir);
        // change current dir for the java.io.File class
        Class<?> fileClass = Class.forName("java.io.File");
        if (JavaVersion.getJavaSpec() >= 11.0) {
            Field fsField = fileClass.getDeclaredField("fs");
            fsField.setAccessible(true);
            Object fs = fsField.get(null);
            Field userDirField = fs.getClass().getDeclaredField("userDir");
            userDirField.setAccessible(true);
            userDirField.set(fs, workingDir);
        }
        // change current dir for the java.nio.Path class
        Object fs = FileSystems.getDefault();
        Class<?> fsClass = fs.getClass();
        while (fsClass != Object.class) {
            if ("sun.nio.fs.UnixFileSystem".equals(fsClass.getName())) {
                Field defaultDirectoryField = fsClass.getDeclaredField("defaultDirectory");
                defaultDirectoryField.setAccessible(true);
                String encoding = System.getProperty("sun.jnu.encoding");
                Charset charset = encoding != null ? Charset.forName(encoding) : Charset.defaultCharset();
                defaultDirectoryField.set(fs, workingDir.getBytes(charset));
            } else if ("sun.nio.fs.WindowsFileSystem".equals(fsClass.getName())) {
                Field defaultDirectoryField = fsClass.getDeclaredField("defaultDirectory");
                Field defaultRootField = fsClass.getDeclaredField("defaultRoot");
                defaultDirectoryField.setAccessible(true);
                defaultRootField.setAccessible(true);
                Path wdir = Paths.get(workingDir);
                Path root = wdir.getRoot();
                defaultDirectoryField.set(fs, wdir.toString());
                defaultRootField.set(fs, root.toString());
            }
            fsClass = fsClass.getSuperclass();
        }
    }

    @SuppressWarnings("unchecked")
    static void setEnv(Map<String, String> newenv) throws Exception {
        Map<String, String> env = System.getenv();
        // The map is an unmodifiable view of the environment map, so find a Map field
        for (Field field : env.getClass().getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                Object obj = field.get(env);
                Map<String, String> map = (Map<String, String>) obj;
                map.clear();
                map.putAll(newenv);
            }
        }
        // OpenJDK 8-17 on Windows
        if (Os.current() == Os.WINDOWS) {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theCaseInsensitiveEnvironmentField =
                    processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
            cienv.clear();
            cienv.putAll(newenv);
        }
    }
}
