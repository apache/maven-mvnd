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

import static org.mvndaemon.mvnd.common.DaemonState.Busy;
import static org.mvndaemon.mvnd.common.DaemonState.Idle;

import java.util.List;

/**
 * File origin:
 * https://github.com/gradle/gradle/blob/v5.6.2/subprojects/launcher/src/main/java/org/gradle/launcher/daemon/registry/DaemonInfo.java
 */
public class DaemonInfo {

    public static final int TOKEN_SIZE = 16;

    private final String id;
    private final String javaHome;
    private final String mvndHome;
    private final int pid;
    private final String address;
    private final byte[] token;
    private final String locale;
    private final List<String> options;
    private final DaemonState state;
    private final long lastIdle;
    private final long lastBusy;

    public DaemonInfo(
            String id,
            String javaHome,
            String mavenHome,
            int pid,
            String address,
            byte[] token,
            String locale,
            List<String> options,
            DaemonState state,
            long lastIdle,
            long lastBusy) {
        this.id = id;
        this.javaHome = javaHome;
        this.mvndHome = mavenHome;
        this.pid = pid;
        this.address = address;
        this.token = token;
        this.locale = locale;
        this.options = options;
        this.state = state;
        this.lastIdle = lastIdle;
        this.lastBusy = lastBusy;
    }

    public String getId() {
        return id;
    }

    public String getJavaHome() {
        return javaHome;
    }

    public String getMvndHome() {
        return mvndHome;
    }

    public int getPid() {
        return pid;
    }

    public String getAddress() {
        return address;
    }

    public byte[] getToken() {
        return token;
    }

    public String getLocale() {
        return locale;
    }

    public List<String> getOptions() {
        return options;
    }

    public DaemonState getState() {
        return state;
    }

    public long getLastIdle() {
        return lastIdle;
    }

    public long getLastBusy() {
        return lastBusy;
    }

    public DaemonInfo withState(DaemonState state) {
        long lb, li;
        if (this.state == Idle && state == Busy) {
            li = lastIdle;
            lb = System.currentTimeMillis();
        } else if (this.state == Busy && state == Idle) {
            li = System.currentTimeMillis();
            lb = lastBusy;
        } else {
            li = lastIdle;
            lb = lastBusy;
        }
        return new DaemonInfo(id, javaHome, mvndHome, pid, address, token, locale, options, state, li, lb);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DaemonInfo{id=").append(id);
        appendNonKeyFields(sb);
        return sb.append('}').toString();
    }

    public StringBuilder appendNonKeyFields(StringBuilder sb) {
        return sb.append("javaHome=")
                .append(javaHome)
                .append(", options=")
                .append(options)
                .append(", mavenHome=")
                .append(mvndHome)
                .append(", pid=")
                .append(pid)
                .append(", address=")
                .append(address)
                .append(", locale=")
                .append(locale)
                .append(", state=")
                .append(state)
                .append(", lastIdle=")
                .append(lastIdle)
                .append(", lastBusy=")
                .append(lastBusy);
    }
}
