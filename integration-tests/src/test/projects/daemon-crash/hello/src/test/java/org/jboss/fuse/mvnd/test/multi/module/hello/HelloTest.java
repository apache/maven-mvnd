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
package org.jboss.fuse.mvnd.test.multi.module.hello;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HelloTest {

    @Test
    void greet() throws IOException {
        final String actual = new Hello().greet();
        Assertions.assertEquals("Hello", actual);

        /* Make sure the plugin was run before this test */
        final String content = new String(Files.readAllBytes(Paths.get("target/hello.txt")), StandardCharsets.UTF_8);
        Assertions.assertTrue("Hi".equals(content) || "Hello".equals(content));
    }

}
