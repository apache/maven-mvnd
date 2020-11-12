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
package org.mvndaemon.mvnd.client;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mvndaemon.mvnd.common.OsUtils;

public class OsUtilsTest {

    /**
     * This test needs to be in the client module as long as the common module is on Java 8
     */
    @Test
    void findProcessRssInKb() {
        long rss = OsUtils.findProcessRssInKb(ProcessHandle.current().pid());
        Assertions.assertThat(rss).isGreaterThan(0);
    }
}
