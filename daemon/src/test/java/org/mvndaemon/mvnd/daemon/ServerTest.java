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
package org.mvndaemon.mvnd.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.common.Message;

public class ServerTest {

    @Test
    void testMessageOrdering() {
        BlockingQueue<Message> messages = new PriorityBlockingQueue<>(64, Message.getMessageComparator());
        messages.addAll(Arrays.asList(
                Message.projectStopped("projectId"),
                Message.projectStarted("projectId"),
                Message.log("projectId", "message"),
                Message.executionFailure("projectId", true, "error")));

        assertEquals(Message.PROJECT_STARTED, messages.remove().getType());
        assertEquals(Message.EXECUTION_FAILURE, messages.remove().getType());
        assertEquals(Message.PROJECT_LOG_MESSAGE, messages.remove().getType());
        assertEquals(Message.PROJECT_STOPPED, messages.remove().getType());
    }
}
