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
package org.mvndaemon.mvnd.forknode;

/**
 * Zero-dependency marker used by the daemon to locate this module's jar on disk
 * (via {@code getProtectionDomain().getCodeSource().getLocation()}) so it can be added to the Surefire plugin realm.
 * Deliberately imports nothing from Surefire so it links in the daemon realm.
 */
public final class MvndSurefireProgressLocator {
    private MvndSurefireProgressLocator() {}
}
