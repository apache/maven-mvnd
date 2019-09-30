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
package org.jboss.fuse.mvnd.daemon;

import java.util.List;

import static org.jboss.fuse.mvnd.daemon.DaemonState.Busy;
import static org.jboss.fuse.mvnd.daemon.DaemonState.Idle;

public class DaemonInfo {

    private final String uid;
    private final String javaHome;
    private final String mavenHome;
    private final long pid;
    private final int address;
    private final int idleTimeout;
    private final String locale;
    private final List<String> options;
    private final DaemonState state;
    private final long lastIdle;
    private final long lastBusy;

    public DaemonInfo(String uid, String javaHome, String mavenHome,
                      long pid, int address, int idleTimeout,
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

    public long getPid() {
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
        return "DaemonInfo{" +
                "uid='" + uid + '\'' +
                ", javaHome='" + javaHome + '\'' +
                ", mavenHome='" + mavenHome + '\'' +
                ", pid=" + pid +
                ", address=" + address +
                ", idleTimeout=" + idleTimeout +
                ", locale='" + locale + '\'' +
                ", options=" + options +
                ", state=" + state +
                ", lastIdle=" + lastIdle +
                ", lastBusy=" + lastBusy +
                '}';
    }
}
