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
package org.mvndaemon.mvnd.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class IoUtils {
    public static String readResource(ClassLoader cl, String resourcePath) {
        final StringBuilder result = new StringBuilder();
        final int bufSize = 1024;
        try (Reader in = new BufferedReader(
                new InputStreamReader(cl.getResourceAsStream(resourcePath), StandardCharsets.UTF_8), bufSize)) {
            int len = 0;
            char[] buf = new char[bufSize];
            while ((len = in.read(buf)) >= 0) {
                result.append(buf, 0, len);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read a class path resource: " + resourcePath, e);
        }
        return result.toString();
    }
}
