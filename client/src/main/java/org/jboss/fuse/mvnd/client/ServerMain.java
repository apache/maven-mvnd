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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ServerMain {

    public static void main(String[] args) throws Exception {
        final String uidStr = Environment.DAEMON_UID.systemProperty().orFail().asString();
        final Path mavenHome = Environment.MAVEN_HOME.systemProperty().orFail().asPath();
        URL[] classpath = Stream.concat(
                Stream.concat(Files.list(mavenHome.resolve("lib/ext")),
                        Files.list(mavenHome.resolve("lib")))
                        .filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .filter(Files::isRegularFile),
                Stream.of(mavenHome.resolve("conf"), mavenHome.resolve("conf/logging")))
                .map(Path::normalize)
                .map(Path::toUri)
                .map(uri -> {
                    try {
                        return uri.toURL();
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toArray(URL[]::new);
        ClassLoader loader = new URLClassLoader(classpath, null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                try {
                    return super.findClass(name);
                } catch (ClassNotFoundException e) {
                    return ServerMain.class.getClassLoader().loadClass(name);
                }
            }
        };
        Thread.currentThread().setContextClassLoader(loader);
        Class<?> clazz = loader.loadClass("org.jboss.fuse.mvnd.daemon.Server");
        try (AutoCloseable server = (AutoCloseable) clazz.getConstructor(String.class).newInstance(uidStr)) {
            ((Runnable) server).run();
        }
    }

}
