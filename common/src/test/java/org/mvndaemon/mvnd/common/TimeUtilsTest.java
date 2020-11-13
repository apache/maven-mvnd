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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimeUtilsTest {

    @Test
    public void testToTimeAsString() {
        assertEquals("600ms", TimeUtils.printDuration(TimeUtils.toDuration("600ms")));
        assertEquals("-1", TimeUtils.printDuration(TimeUtils.toDuration("-1")));
        assertEquals("0ms", TimeUtils.printDuration(TimeUtils.toDuration("0ms")));
        assertEquals("1s", TimeUtils.printDuration(TimeUtils.toDuration("1000ms")));
        assertEquals("1m600ms", TimeUtils.printDuration(TimeUtils.toDuration("1minute 600ms")));
        assertEquals("1m1s100ms", TimeUtils.printDuration(TimeUtils.toDuration("1m1100ms")));
        assertEquals("5m10s300ms", TimeUtils.printDuration(310300));
        assertEquals("5s500ms", TimeUtils.printDuration(5500));
        assertEquals("1h50m", TimeUtils.printDuration(6600000));
        assertEquals("2d3h4m", TimeUtils.printDuration(Duration.parse("P2DT3H4M").toMillis()));
        assertEquals("2d4m", TimeUtils.printDuration(Duration.parse("P2DT4M").toMillis()));
    }

}
