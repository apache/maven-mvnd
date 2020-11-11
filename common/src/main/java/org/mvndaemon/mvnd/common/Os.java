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

import java.util.Locale;

public enum Os {
    LINUX(true),
    MAC(true),
    WINDOWS(false) {
        private boolean cygwin;
        {
            String pwd = System.getenv("PWD");
            cygwin = pwd != null && pwd.startsWith("/");
        }

        @Override
        public boolean isCygwin() {
            return cygwin;
        }
    },
    UNKNOWN(false) {

        @Override
        public boolean isUnixLike() {
            throw new UnsupportedOperationException("Cannot tell isUnixLike() for an " + UNKNOWN.name() + " OS");
        }

    };

    private static final Os CURRENT;
    static {
        final String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.startsWith("osx") || osName.startsWith("mac os x")) {
            CURRENT = MAC;
        } else if (osName.contains("win")) {
            CURRENT = WINDOWS;
        } else if (osName.contains("linux")) {
            CURRENT = LINUX;
        } else {
            CURRENT = UNKNOWN;
        }
    }

    private final boolean unixLike;

    public static Os current() {
        return CURRENT;
    }

    Os(boolean unixLike) {
        this.unixLike = unixLike;
    }

    public boolean isUnixLike() {
        return unixLike;
    }

    public boolean isCygwin() {
        return false;
    }

}
