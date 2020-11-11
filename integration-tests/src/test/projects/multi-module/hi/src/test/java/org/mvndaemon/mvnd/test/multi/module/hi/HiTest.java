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
package org.mvndaemon.mvnd.test.multi.module.hi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HiTest {

    @Test
    void greet() throws IOException {
        final String actual = new Hi().greet();
        Files.write(Paths.get("target/hi.txt"), actual.getBytes(StandardCharsets.UTF_8));
        Assertions.assertEquals("Hi", actual);

        /* Have some random delay so that hi and hello may finish in random order */
        if (Math.random() >= 0.5) {
            try {
                System.out.println("HiTest sleeps for 500 ms");
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
