/*
 * Copyright (c) 2015-2016 salesforce.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.fuse.mvnd.logging.internal;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * File origin: https://github.com/takari/concurrent-build-logger/blob/concurrent-build-logger-0.1.0/src/main/java/io/takari/maven/logging/internal/LineSplitter.java
 */
class LineSplitter {

    public static final int BYTES_SIZE = 1024;

    private final CharsetDecoder decoder;

    private final StringBuilder chars = new StringBuilder();

    private final ByteBuffer bytes = ByteBuffer.allocate(BYTES_SIZE);

    public LineSplitter() {
        this(Charset.defaultCharset()); // charset used by sysout
    }

    public LineSplitter(Charset charset) {
        this.decoder = charset.newDecoder();
    }

    public Collection<String> split(String string) {
        // TODO hexdump pending bytes
        bytes.clear();
        chars.append(string);

        return split();
    }

    // valid EOL sequences according to BufferedReader#readLine
    // a line feed ('\n')
    // a carriage return ('\r')
    // a carriage return followed immediately by a linefeed ('\r' '\n')
    private Collection<String> split() {
        List<String> strings = new ArrayList<>();

        int prev = 0, curr = 0;
        for (; curr < chars.length(); curr++) {
            char ch = chars.charAt(curr);
            if (ch == '\n' || ch == '\r') {
                strings.add(chars.substring(prev, curr));
                if (curr < chars.length() - 1 && ch == '\r' && chars.charAt(curr + 1) == '\n') {
                    curr++; // crlf
                }
                prev = curr + 1;
            }
        }

        // compact chars buffer
        chars.delete(0, prev);
        chars.trimToSize();

        return strings;
    }

    public Collection<String> split(byte[] bytes, int offset, int length) {
        int pos = offset;
        do {
            int len = Math.min(this.bytes.remaining(), length - pos + offset);
            this.bytes.put(bytes, pos, len);
            decodeBytes();
            pos += len;
        } while (pos < offset + length - 1);

        return split();
    }


    private void decodeBytes() {
        // the idea of this code is to decode as many bytes as possible
        // and preserve remaining bytes for use in further invocations

        // TODO there has got to be an easier way!
        CharBuffer charBuffer = CharBuffer.allocate(bytes.position());
        bytes.flip();
        decoder.reset();
        decoder.decode(bytes, charBuffer, true);
        decoder.flush(charBuffer);
        charBuffer.flip();
        chars.append(charBuffer);

        bytes.compact();
    }

    public String flush() {
        String string = chars.toString();
        chars.setLength(0);
        chars.trimToSize();
        return string;
    }

}
