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
package org.jboss.fuse.mvnd.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageTest {

    @Test
    public void testBigMessage() throws IOException {
        StringBuilder stringToWrite = new StringBuilder();
        for (int i = 0; i < 66000; ++i) {
            stringToWrite.append("a");
        }
        Message msg = new Message.BuildMessage("project", stringToWrite.toString());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream daos = new DataOutputStream(baos)) {
            msg.write(daos);
        }

        Message msg2;
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (DataInputStream dis = new DataInputStream(bais)) {
            msg2 = Message.read(dis);
        }

        assertTrue(msg2 instanceof Message.BuildMessage);
        assertEquals(stringToWrite, ((Message.BuildMessage) msg2).getMessage());
    }
}
