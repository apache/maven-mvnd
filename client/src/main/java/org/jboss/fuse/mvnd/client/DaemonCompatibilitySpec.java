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


import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class DaemonCompatibilitySpec {

    private final Path javaHome;
    private final List<String> options;

    /**
     * @param javaHome make sure the Path is a result of {@link Path#toRealPath(java.nio.file.LinkOption...)}
     * @param options the options
     */
    public DaemonCompatibilitySpec(Path javaHome, List<String> options) {
        this.javaHome = Objects.requireNonNull(javaHome, "javaHome");
        this.options = Objects.requireNonNull(options, "options");
    }

    public Result isSatisfiedBy(DaemonInfo daemon) {
        if (!javaHomeMatches(daemon)) {
            return new Result(false, () -> "Java home is different.\n" + diff(daemon));
        }
        if (!daemonOptsMatch(daemon)) {
            return new Result(false, () -> "At least one daemon option is different.\n" + diff(daemon));
        }
        return new Result(true, () -> {throw new RuntimeException("No reason if DaemonCompatibilityResult.compatible == true");});
    }

    private String diff(DaemonInfo context) {
        final StringBuilder sb = new StringBuilder("Wanted: ");
        appendFields(sb);
        sb.append("\nActual: ");
        context.appendNonKeyFields(sb).append("uid=").append(context.getUid()).append('\n');
        return sb.toString();
    }

    private boolean daemonOptsMatch(DaemonInfo daemon) {
        return daemon.getOptions().containsAll(options)
            && daemon.getOptions().size() == options.size();
    }

    private boolean javaHomeMatches(DaemonInfo daemon) {
        return javaHome.equals(Paths.get(daemon.getJavaHome()));
    }

    StringBuilder appendFields(StringBuilder sb) {
        return sb.append("javaHome=").append(javaHome)
                .append(", options=").append(options);
    }

    @Override
    public String toString() {
        return appendFields(new StringBuilder("DaemonCompatibilitySpec{")).append('}').toString();
    }

    public static class Result {
        private final boolean compatible;
        private final Supplier<String> why;

        Result(boolean compatible, Supplier<String> why) {
            super();
            this.compatible = compatible;
            this.why = why;
        }

        public boolean isCompatible() {
            return compatible;
        }

        public String getWhy() {
            return why.get();
        }
    }
}
