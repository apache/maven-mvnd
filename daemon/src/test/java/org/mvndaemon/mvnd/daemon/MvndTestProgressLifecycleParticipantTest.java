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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MvndTestProgressLifecycleParticipantTest {

    @Test
    void injectsOnlyForSurefireVersionsThatSupportForkNode() {
        // forkNode SPI exists since 3.0.0-M5 -> anything older must be skipped so the build never fails
        assertFalse(MvndTestProgressLifecycleParticipant.supportsForkNode(null));
        assertFalse(MvndTestProgressLifecycleParticipant.supportsForkNode("2.22.2"));
        assertFalse(MvndTestProgressLifecycleParticipant.supportsForkNode("3.0.0-M4"));

        assertTrue(MvndTestProgressLifecycleParticipant.supportsForkNode("3.0.0-M5"));
        assertTrue(MvndTestProgressLifecycleParticipant.supportsForkNode("3.0.0-M8"));
        assertTrue(MvndTestProgressLifecycleParticipant.supportsForkNode("3.5.6"));
        assertTrue(MvndTestProgressLifecycleParticipant.supportsForkNode("4.0.0"));
    }
}
