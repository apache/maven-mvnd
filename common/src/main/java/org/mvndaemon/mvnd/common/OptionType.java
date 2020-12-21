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
package org.mvndaemon.mvnd.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;
import org.mvndaemon.mvnd.common.Environment.DocumentedEnumEntry;

public enum OptionType {
    /**
     * <code>true</code> or <code>false</code>; empty string is also interpreted as <code>true</code> - so
     * <code>-Dmvnd.noBuffering</code> is equivalent to <code>-Dmvnd.noBuffering=true</code>
     */
    BOOLEAN,
    /**
     * An unlabeled whole number of milliseconds or a whole number followed by an optional space and a unit
     * which may be one of the following (in EBNF notation): <code>d[ay[s]]</code>, <code>h[our[s]]</code>,
     * <code>m[in[ute[s]]]</code>, <code>s[ec[ond[s]]]</code> or <code>m[illi]s[ec[ond[s]]]</code>.
     * <p>
     * Examples: <code>7 days</code>, <code>7days</code>, <code>7d</code>, <code>100 milliseconds</code>,
     * <code>100 millis</code>, <code>100ms</code>, <code>100</code>
     */
    DURATION {
        @Override
        public String normalize(String value) {
            return TimeUtils.printDuration(TimeUtils.toMilliSeconds(value));
        }
    },
    /** A whole number */
    INTEGER,
    /**
     * An amount of memory as accepted by the <code>-Xm*</code> family of HotSpot JVM options. Examples:
     * <code>1024m</code>, <code>2g</code>, <code>5G</code>
     */
    MEMORY_SIZE,
    /** A local file system path */
    PATH,
    /** A string */
    STRING,
    /** No value */
    VOID;

    public String normalize(String value) {
        return value;
    }

    public static Stream<DocumentedEnumEntry<OptionType>> documentedEntries() {
        Properties props = new Properties();
        OptionType[] values = values();
        final String cliOptionsPath = values[0].getClass().getSimpleName() + ".javadoc.properties";
        try (InputStream in = Environment.class.getResourceAsStream(cliOptionsPath)) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + cliOptionsPath, e);
        }
        return Stream.of(values)
                .filter(opt -> opt != VOID)
                .sorted(Comparator.comparing(OptionType::name))
                .map(env -> new DocumentedEnumEntry<>(env, props.getProperty(env.name())));
    }
}
