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
package org.mvndaemon.mvnd.common;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public final class ClassLoaderHelper {

    private ClassLoaderHelper() {
        // no instances
    }

    /**
     * Creates mvndaemon class loader based on {@link Environment#MVND_HOME} and known layout.
     *
     * @param  filter non-null filter applied to findClass method.
     * @param  parent nullable parent class loader.
     * @return        a built {@link ClassLoader} that uses passed in filter and optionally parent.
     */
    public static ClassLoader createLoader(final Predicate<String> filter, final ClassLoader parent) {

        requireNonNull(filter);
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
        return new URLClassLoader(classpath, parent) {

            private final ClassLoader fallback = parent != null ? parent : ClassLoaderHelper.class.getClassLoader();

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                try {
                    if (filter.test(name)) {
                        return super.findClass(name);
                    }
                } catch (ClassNotFoundException e) {
                    return fallback.loadClass(name);
                }
                throw new ClassNotFoundException(name);
            }

            @Override
            public URL getResource(String name) {
                URL url = null;
                if (filter.test(name)) {
                    url = super.getResource(name);
                }
                if (url == null) {
                    url = fallback.getResource(name);
                }
                return url;
            }
        };
    }
}
