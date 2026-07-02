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
package org.mvndaemon.mvnd.testprogress;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.surefire.api.event.ControlByeEvent;
import org.apache.maven.surefire.api.event.Event;
import org.apache.maven.surefire.extensions.EventHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MvndForkNodeFactoryTest {

    @AfterEach
    void clearListener() {
        MvndTestProgress.setListener(null);
    }

    @Test
    void alwaysDelegatesEvenWhenListenerThrows() {
        List<Event> delegated = new ArrayList<>();
        EventHandler<Event> real = delegated::add;

        // A listener that always blows up must not prevent delegation to the real handler.
        MvndTestProgress.setListener((p, c, m, comp, f, e, s) -> {
            throw new RuntimeException("boom");
        });

        EventHandler<Event> wrapper =
                new MvndForkNodeFactory.ProgressEventHandler("proj", real, new TestProgressAccumulator());

        wrapper.handleEvent(new ControlByeEvent());

        assertEquals(1, delegated.size(), "the real handler must always be called");
    }

    @Test
    void delegatesWhenNoListenerRegistered() {
        List<Event> delegated = new ArrayList<>();
        EventHandler<Event> real = delegated::add;

        EventHandler<Event> wrapper =
                new MvndForkNodeFactory.ProgressEventHandler("proj", real, new TestProgressAccumulator());

        wrapper.handleEvent(new ControlByeEvent());

        assertEquals(1, delegated.size(), "non-test events still pass through");
    }
}
