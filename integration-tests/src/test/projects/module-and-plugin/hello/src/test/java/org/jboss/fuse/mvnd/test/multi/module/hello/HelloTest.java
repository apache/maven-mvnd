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
