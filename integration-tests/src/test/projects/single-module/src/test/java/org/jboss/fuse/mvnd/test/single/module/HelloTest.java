package org.jboss.fuse.mvnd.test.single.module;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HelloTest {
    @Test
    void hello() throws IOException {
        final String actual = new Hello().sayHello();
        Files.write(Paths.get("target/hello.txt"), actual.getBytes(StandardCharsets.UTF_8));
        Assertions.assertEquals("Hello", actual);
    }
}
