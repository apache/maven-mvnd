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
package org.mvndaemon.mvnd.nativ;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MvndNativeLoaderTest {

    // contentsEquals reads in blocks of this size
    private static final int BLOCK_SIZE = 8192;

    @Test
    public void testLoadJansi() {
        MvndNativeLoader.initialize();
    }

    @Test
    public void contentsEqualsIgnoresBytesPastWhatWasRead() throws Exception {
        byte[] data = randomBytes(BLOCK_SIZE + 192);
        // Seen on Fedora, where the JDK's Inflater links the system zlib (zlib-ng):
        // reading a jar entry changed buffer bytes past the count read() returned.
        InputStream scribbling = new ByteArrayInputStream(data) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                int n = super.read(b, off, len);
                if (n > 0 && n < len) {
                    b[off + n] ^= 0x55;
                }
                return n;
            }
        };
        assertNull(contentsEquals(scribbling, new ByteArrayInputStream(data)));
    }

    @Test
    public void contentsEqualsReportsDifferentBytes() throws Exception {
        byte[] data = randomBytes(BLOCK_SIZE + 192);
        byte[] other = data.clone();
        other[BLOCK_SIZE + 100] ^= 0x55;
        assertEquals(
                "Content differs", contentsEquals(new ByteArrayInputStream(data), new ByteArrayInputStream(other)));
    }

    private static String contentsEquals(InputStream in1, InputStream in2) throws Exception {
        Method m = MvndNativeLoader.class.getDeclaredMethod("contentsEquals", InputStream.class, InputStream.class);
        m.setAccessible(true);
        return (String) m.invoke(null, in1, in2);
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new Random(1).nextBytes(data);
        return data;
    }
}
