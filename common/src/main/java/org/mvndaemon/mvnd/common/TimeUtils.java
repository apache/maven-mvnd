/*
 * Copyright 2020 the original author or authors.
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

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time utils.
 */
public final class TimeUtils {

    private static final int ONE_UNIT = 1;
    public static final long DAYS_MILLIS = TimeUnit.DAYS.toMillis(ONE_UNIT);
    public static final long HOURS_MILLIS = TimeUnit.HOURS.toMillis(ONE_UNIT);
    public static final long MINUTES_MILLIS = TimeUnit.MINUTES.toMillis(ONE_UNIT);
    public static final long SECONDS_MILLIS = TimeUnit.SECONDS.toMillis(ONE_UNIT);

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?<n>-?\\d+)" +
                    "|" +
                    "(" +
                        "((?<d>\\d+)\\s*d(ay(s)?)?)?" + "\\s*" +
                        "((?<h>\\d+)\\s*h(our(s)?)?)?" + "\\s*" +
                        "((?<m>\\d+)\\s*m(in(ute(s)?)?)?)?" + "\\s*" +
                        "((?<s>\\d+(\\.\\d+)?)\\s*s(ec(ond(s)?)?)?)?" + "\\s*" +
                        "((?<l>\\d+(\\.\\d+)?)\\s*m(illi)?s(ec(ond)?(s)?)?)?" +
                    ")",
            Pattern.CASE_INSENSITIVE);

    private TimeUtils() {
    }

    public static boolean isPositive(Duration dur) {
        return dur.getSeconds() > 0 || dur.getNano() != 0;
    }

    public static String printDuration(Duration uptime) {
        return printDuration(uptime.toMillis());
    }

    /**
     * This will print time in human readable format from milliseconds.
     * Examples:
     *    500 -> 500ms
     *    1300 -> 1s300ms
     *    310300 -> 5m10s300ms
     *    6600000 -> 1h50m
     *
     * @param  millis time in milliseconds
     * @return           time in string
     */
    public static String printDuration(long millis) {
        if (millis < 0) {
            return Long.toString(millis);
        }
        final StringBuilder sb = new StringBuilder();
        if (millis >= DAYS_MILLIS) {
            sb.append(millis / DAYS_MILLIS).append("d");
            millis %= DAYS_MILLIS;
        }
        if (millis >= HOURS_MILLIS) {
            sb.append(millis / HOURS_MILLIS).append("h");
            millis %= HOURS_MILLIS;
        }
        if (millis >= MINUTES_MILLIS) {
            sb.append(millis / MINUTES_MILLIS).append("m");
            millis %= MINUTES_MILLIS;
        }
        if (millis >= SECONDS_MILLIS) {
            sb.append(millis / SECONDS_MILLIS).append("s");
            millis %= SECONDS_MILLIS;
        }
        if (millis >= ONE_UNIT || sb.length() == 0) {
            sb.append(millis / ONE_UNIT).append("ms");
        }
        return sb.toString();
    }

    public static Duration toDuration(String source) throws IllegalArgumentException {
        return Duration.ofMillis(toMilliSeconds(source));
    }

    public static long toMilliSeconds(String source) throws IllegalArgumentException {
        Matcher matcher = DURATION_PATTERN.matcher(source);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unable to parse duration: '" + source + "'");
        }
        String n = matcher.group("n");
        if (n != null) {
            return Long.parseLong(n);
        } else {
            String d = matcher.group("d");
            String h = matcher.group("h");
            String m = matcher.group("m");
            String s = matcher.group("s");
            String l = matcher.group("l");
            return (d != null ? TimeUnit.DAYS.toMillis(Long.parseLong(d)) : 0)
                + (h != null ? TimeUnit.HOURS.toMillis(Long.parseLong(h)) : 0)
                + (m != null ? TimeUnit.MINUTES.toMillis(Long.parseLong(m)) : 0)
                + (s != null ? TimeUnit.SECONDS.toMillis(Long.parseLong(s)) : 0)
                + (l != null ? TimeUnit.MILLISECONDS.toMillis(Long.parseLong(l)) : 0);
        }
    }

}
