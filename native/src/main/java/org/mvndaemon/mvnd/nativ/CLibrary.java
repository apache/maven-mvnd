/*
 * Copyright (C) 2009-2017 the original author(s).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mvndaemon.mvnd.nativ;

/**
 * Interface to access some low level POSIX functions, loaded by
 * <a href="http://fusesource.github.io/hawtjni/">HawtJNI</a> Runtime
 * as <code>jansi</code> library.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 * @see    MvndNativeLoader
 */
@SuppressWarnings("unused")
public class CLibrary {

    static {
        MvndNativeLoader.initialize();
    }

    public static native int setenv(String name, String value);

    public static native int chdir(String path);

    public static native int getOsxMemoryInfo(long[] totalAndAvailMem);

}
