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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class MessageTest {

    @Test
    public void testBigMessage() throws IOException {
        StringBuilder stringToWrite = new StringBuilder();
        for (int i = 0; i < 66000; ++i) {
            stringToWrite.append("a");
        }
        Message msg = Message.log("project", stringToWrite.toString());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream daos = new DataOutputStream(baos)) {
            msg.write(daos);
        }

        Message msg2;
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (DataInputStream dis = new DataInputStream(bais)) {
            msg2 = Message.read(dis);
        }

        assertTrue(msg2 instanceof Message.ProjectEvent);
        assertEquals(stringToWrite.toString(), ((Message.ProjectEvent) msg2).getMessage());
    }

    @Test
    void buildExceptionSerialization() throws Exception {
        Message msg = new Message.BuildException(new NullPointerException());
        assertNull(((Message.BuildException) msg).getMessage());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream daos = new DataOutputStream(baos)) {
            msg.write(daos);
        }

        Message msg2;
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (DataInputStream dis = new DataInputStream(bais)) {
            msg2 = Message.read(dis);
        }

        assertTrue(msg2 instanceof Message.BuildException);
        assertNull(((Message.BuildException) msg2).getMessage());
    }
}
