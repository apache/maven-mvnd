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
package org.mvndaemon.mvnd.client;

import java.util.List;

/**
 * Adjustments the client makes to the command line before it is sent to the daemon.
 * Kept apart from {@code DefaultClient}, which lives in a separate source root that the unit tests cannot see.
 */
final class ClientArguments {

    private ClientArguments() {}

    /**
     * Drops empty and blank arguments, like Maven does, so that e.g. an empty string passed by PowerShell is not
     * mistaken for a goal.
     */
    static void removeBlankArguments(List<String> args) {
        args.removeIf(arg -> arg.trim().isEmpty());
    }
}
