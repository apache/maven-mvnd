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


import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

public class DaemonCompatibilitySpec {

    private final String javaHome;
    private final List<String> options;

    public DaemonCompatibilitySpec(String javaHome, List<String> options) {
        this.javaHome = javaHome;
        this.options = options;
    }

    public boolean isSatisfiedBy(DaemonInfo daemon) {
        return whyUnsatisfied(daemon) == null;
    }

    public String whyUnsatisfied(DaemonInfo daemon) {
        if (!javaHomeMatches(daemon)) {
            return "Java home is different.\n" + description(daemon);
        } else if (!daemonOptsMatch(daemon)) {
            return "At least one daemon option is different.\n" + description(daemon);
        }
        return null;
    }

    private String description(DaemonInfo context) {
        return "Wanted: " + this + "\n"
            + "Actual: " + context + "\n";
    }

    private boolean daemonOptsMatch(DaemonInfo daemon) {
        return daemon.getOptions().containsAll(options)
            && daemon.getOptions().size() == options.size();
    }

    private boolean javaHomeMatches(DaemonInfo daemon) {
        return Objects.equals(
                Paths.get(daemon.getJavaHome()).normalize(),
                Paths.get(javaHome).normalize());
    }

    @Override
    public String toString() {
        return "DaemonCompatibilitySpec{" +
                "javaHome='" + javaHome + '\'' +
                ", daemonOpts=" + options +
                '}';
    }
}
