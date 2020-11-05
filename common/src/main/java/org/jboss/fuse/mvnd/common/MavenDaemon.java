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
package org.jboss.fuse.mvnd.common;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class MavenDaemon {

    public static void main(String[] args) throws Exception {
        // Disable URL caching so that  the JVM does not try to cache resources
        // loaded from jars that are built by a previous run
        new File("txt").toURI().toURL().openConnection().setDefaultUseCaches(false);

        final Path mvndHome = Environment.MVND_HOME.asPath();
        URL[] classpath = Stream.concat(
                /* jars */
                Stream.of("mvn/lib/ext", "mvn/lib", "mvn/boot")
                        .map(mvndHome::resolve)
                        .flatMap((Path p) -> {
                            try {
                                return Files.list(p);
                            } catch (java.io.IOException e) {
                                throw new RuntimeException("Could not list " + p, e);
                            }
                        })
                        .filter(p -> {
                            final String fileName = p.getFileName().toString();
                            return fileName.endsWith(".jar") && !fileName.startsWith("mvnd-client-");
                        })
                        .filter(Files::isRegularFile),
                /* resources */
                Stream.of(mvndHome.resolve("mvn/conf"), mvndHome.resolve("mvn/conf/logging")))

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
                    return MavenDaemon.class.getClassLoader().loadClass(name);
                }
            }
        };
        Thread.currentThread().setContextClassLoader(loader);
        Class<?> clazz = loader.loadClass("org.jboss.fuse.mvnd.daemon.Server");
        try (AutoCloseable server = (AutoCloseable) clazz.getConstructor().newInstance()) {
            ((Runnable) server).run();
        }
    }

}
