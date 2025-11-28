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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * Utility class to clean up direct {@link ByteBuffer} instances by explicitly invoking their cleaner.
 */
public class BufferHelper {

    private static final Object unsafe;
    private static final java.lang.reflect.Method invokeCleanerMethod;

    static {
        Object tmpUnsafe = null;
        Method tmpInvokeCleaner = null;
        try {
            Class<?> unsafeClass = Class.forName("jdk.internal.misc.Unsafe");
            Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            tmpUnsafe = theUnsafeField.get(null);
            tmpInvokeCleaner = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to access jdk.internal.misc.Unsafe#invokeCleaner", e);
        }
        unsafe = tmpUnsafe;
        invokeCleanerMethod = tmpInvokeCleaner;
    }

    /**
     * Attempts to clean a direct {@link ByteBuffer}, releasing its underlying memory.
     *
     * @param byteBuffer The direct ByteBuffer to clean.
     * @return {@code true} if the cleaner was successfully invoked, {@code false} otherwise.
     */
    public static boolean closeDirectByteBuffer(ByteBuffer byteBuffer) {
        return closeDirectByteBuffer(byteBuffer, null);
    }

    /**
     * Attempts to clean a direct {@link ByteBuffer}, releasing its underlying memory.
     * Logs any errors via the provided logger.
     *
     * @param byteBuffer The direct ByteBuffer to clean.
     * @param log        A consumer to log any potential errors, may be {@code null}.
     * @return {@code true} if the cleaner was successfully invoked, {@code false} otherwise.
     */
    public static boolean closeDirectByteBuffer(ByteBuffer byteBuffer, Consumer<String> log) {
        if (byteBuffer != null && byteBuffer.isDirect()) {
            try {
                invokeCleanerMethod.invoke(unsafe, byteBuffer);
                return true;
            } catch (Exception e) {
                if (log != null) {
                    log.accept("Failed to clean ByteBuffer: " + e);
                }
                return false;
            }
        }
        return false;
    }
}
