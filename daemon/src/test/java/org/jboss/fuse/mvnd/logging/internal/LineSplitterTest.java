/*
 * Copyright (c) 2015-2016 salesforce.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.fuse.mvnd.logging.internal;

import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;

public class LineSplitterTest {
    private final String STRING = "\u0442\u0435\u0441\u0442"; // "test" in Russian
    private final byte[] BYTES;

    public LineSplitterTest() throws Exception {
        this.BYTES = STRING.getBytes(UTF_8);

        // sanity check
        Assertions.assertEquals(4, STRING.length());
        Assertions.assertEquals(8, BYTES.length);
    }

    @Test
    public void testUTF8MultibyteReassembly() throws Exception {
        LineSplitter adaptor = new LineSplitter(UTF_8);

        Assertions.assertTrue(adaptor.split(BYTES, 0, 1).isEmpty());
        Assertions.assertTrue(adaptor.split(BYTES, 1, 6).isEmpty());
        Assertions.assertTrue(adaptor.split(BYTES, 7, 1).isEmpty());

        Assertions.assertEquals(STRING, adaptor.flush());
    }

    @Test
    public void testBytesBufferOverflow() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LineSplitter.BYTES_SIZE * 2 + 10; i++) {
            sb.append('a');
        }

        LineSplitter adaptor = new LineSplitter(UTF_8);
        byte[] bytes = sb.toString().getBytes(UTF_8);
        adaptor.split(bytes, 0, bytes.length);

        Assertions.assertEquals(sb.toString(), adaptor.flush());
    }

    @Test
    public void testLineSplit() throws Exception {
        LineSplitter adaptor = new LineSplitter(UTF_8);

        Assertions.assertEquals(Arrays.asList(""), adaptor.split("\n"));
        Assertions.assertEquals(Arrays.asList(""), adaptor.split("\r"));
        Assertions.assertEquals(Arrays.asList(""), adaptor.split("\r\n"));
        Assertions.assertEquals(Arrays.asList("", ""), adaptor.split("\r\r"));

        adaptor.split("a");
        Assertions.assertEquals("a", adaptor.flush());

        Collection<String> strings = adaptor.split("a\nb\rc\rd");
        Assertions.assertEquals(Arrays.asList("a", "b", "c"), strings);
        Assertions.assertEquals("d", adaptor.flush());
    }
}
