/*
 * Copyright 2011 the original author or authors.
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
package org.jboss.fuse.mvnd.client;

import java.util.List;

import static org.jboss.fuse.mvnd.client.DaemonState.Busy;
import static org.jboss.fuse.mvnd.client.DaemonState.Idle;

/**
 * File origin:
 * https://github.com/gradle/gradle/blob/v5.6.2/subprojects/launcher/src/main/java/org/gradle/launcher/daemon/registry/DaemonInfo.java
 */
public class DaemonInfo {

    private final String uid;
    private final String javaHome;
    private final String mavenHome;
    private final int pid;
    private final int address;
    private final int idleTimeout;
    private final String locale;
    private final List<String> options;
    private final DaemonState state;
    private final long lastIdle;
    private final long lastBusy;

    public DaemonInfo(String uid, String javaHome, String mavenHome,
            int pid, int address, int idleTimeout,
            String locale, List<String> options,
            DaemonState state, long lastIdle, long lastBusy) {
        this.uid = uid;
        this.javaHome = javaHome;
        this.mavenHome = mavenHome;
        this.pid = pid;
        this.address = address;
        this.idleTimeout = idleTimeout;
        this.locale = locale;
        this.options = options;
        this.state = state;
        this.lastIdle = lastIdle;
        this.lastBusy = lastBusy;
    }

    public String getUid() {
        return uid;
    }

    public String getJavaHome() {
        return javaHome;
    }

    public String getMavenHome() {
        return mavenHome;
    }

    public int getPid() {
        return pid;
    }

    public int getAddress() {
        return address;
    }

    public int getIdleTimeout() {
        return idleTimeout;
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
        return new DaemonInfo(uid, javaHome, mavenHome, pid, address,
                idleTimeout, locale, options, state, li, lb);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DaemonInfo{uid=").append(uid);
        appendNonKeyFields(sb);
        return sb.append('}').toString();
    }

    public StringBuilder appendNonKeyFields(StringBuilder sb) {
        return sb.append("javaHome=").append(javaHome)
                .append(", options=").append(options)
                .append(", mavenHome=").append(mavenHome)
                .append(", pid=").append(pid)
                .append(", address=").append(address)
                .append(", idleTimeout=").append(idleTimeout)
                .append(", locale=").append(locale)
                .append(", state=").append(state)
                .append(", lastIdle=").append(lastIdle)
                .append(", lastBusy=").append(lastBusy);
    }
}
