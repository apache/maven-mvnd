/*
 * Copyright 2018 the original author or authors.
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

import java.nio.Buffer;

/**
 * File origin: https://github.com/gradle/gradle/blob/v5.6.2/subprojects/base-services/src/main/java/org/gradle/internal/io/BufferCaster.java
 */
public class BufferCaster {
    /**
     * Without this cast, when the code compiled by Java 9+ is executed on Java 8, it will throw
     * java.lang.NoSuchMethodError: Method flip()Ljava/nio/ByteBuffer; does not exist in class java.nio.ByteBuffer
     */
    @SuppressWarnings("RedundantCast")
    public static <T extends Buffer> Buffer cast(T byteBuffer) {
        return (Buffer) byteBuffer;
    }
}
