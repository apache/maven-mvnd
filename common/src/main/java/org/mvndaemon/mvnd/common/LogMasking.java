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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Masks values that should not appear in the daemon log: property and environment values whose key names a
 * password or similar, as they come in on the command line or in the client environment.
 */
public final class LogMasking {

    private static final Pattern SENSITIVE_KEY =
            Pattern.compile("(?i).*(password|passwd|passphrase|secret|token|credential).*");

    private static final String MASK = "****";

    private LogMasking() {}

    public static boolean isSensitive(String key) {
        return key != null && SENSITIVE_KEY.matcher(key).matches();
    }

    /**
     * The value to log for {@code key}: the value itself, or a mask when the key looks sensitive.
     */
    public static String value(String key, String value) {
        return value != null && isSensitive(key) ? MASK : value;
    }

    /**
     * Command-line arguments with the values of sensitive {@code -Dkey=value} (also {@code -D key=value} and
     * {@code --define key=value}) definitions masked.
     */
    public static List<String> args(List<String> args) {
        if (args == null) {
            return null;
        }
        List<String> result = new ArrayList<>(args.size());
        boolean defineValue = false;
        for (String arg : args) {
            if (defineValue) {
                result.add(definition(arg));
                defineValue = false;
            } else if (arg != null && arg.startsWith("-D") && arg.length() > 2) {
                result.add("-D" + definition(arg.substring(2)));
            } else {
                result.add(arg);
                defineValue = "-D".equals(arg) || "--define".equals(arg);
            }
        }
        return result;
    }

    private static String definition(String keyValue) {
        if (keyValue == null) {
            return null;
        }
        int eq = keyValue.indexOf('=');
        if (eq < 0) {
            return keyValue;
        }
        String key = keyValue.substring(0, eq);
        return isSensitive(key) ? key + '=' + MASK : keyValue;
    }
}
