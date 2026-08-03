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
package org.mvndaemon.mvnd.common.logging;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.common.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerminalOutputTest {

    @Test
    void renderBarZero() {
        assertEquals("[                    ]", TerminalOutput.renderBar(0));
    }

    @Test
    void renderBarPartial() {
        // 30% -> filled = round(6.0) = 6 -> 5 '=' + '>' + 14 spaces
        assertEquals("[=====>              ]", TerminalOutput.renderBar(30));
    }

    @Test
    void renderBarFull() {
        assertEquals("[====================]", TerminalOutput.renderBar(100));
    }

    @Test
    void suffixAllPassing() {
        AttributedStringBuilder asb = new AttributedStringBuilder();
        TerminalOutput.appendTestProgress(
                asb, Message.projectTestProgress("app", "com.acme.FooTest", "shouldWork", 12, 0, 0, 0));
        assertEquals(" [Tests: 12] FooTest#shouldWork", asb.toAttributedString().toString());
    }

    @Test
    void suffixFailuresRenderRed() {
        AttributedStringBuilder asb = new AttributedStringBuilder();
        TerminalOutput.appendTestProgress(
                asb, Message.projectTestProgress("app", "com.acme.FooTest", "shouldWork", 12, 1, 0, 0));
        AttributedString s = asb.toAttributedString();
        assertEquals(" [Tests: 12, Failures: 1] FooTest#shouldWork", s.toString());
        int failureDigit = s.toString().indexOf("Failures: ") + "Failures: ".length();
        assertEquals(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED), s.styleAt(failureDigit));
        int bracket = s.toString().indexOf('[');
        assertEquals(AttributedStyle.DEFAULT.faint(), s.styleAt(bracket));
    }

    @Test
    void suffixErrorsAndSkips() {
        AttributedStringBuilder asb = new AttributedStringBuilder();
        TerminalOutput.appendTestProgress(
                asb, Message.projectTestProgress("app", "com.acme.FooTest", "shouldWork", 5, 0, 2, 1));
        assertEquals(
                " [Tests: 5, Errors: 2, Skipped: 1] FooTest#shouldWork",
                asb.toAttributedString().toString());
    }

    @Test
    void suffixClassOnly() {
        AttributedStringBuilder asb = new AttributedStringBuilder();
        TerminalOutput.appendTestProgress(
                asb, Message.projectTestProgress("app", "com.acme.FooTest", null, 3, 0, 0, 0));
        assertEquals(" [Tests: 3] FooTest", asb.toAttributedString().toString());
    }
}
