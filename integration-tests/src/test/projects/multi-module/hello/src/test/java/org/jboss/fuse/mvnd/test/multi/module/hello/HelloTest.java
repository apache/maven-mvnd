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
        Files.write(Paths.get("target/hello.txt"), actual.getBytes(StandardCharsets.UTF_8));
        Assertions.assertEquals("Hello", actual);

        /* Have some random delay so that hi and hello may finish in random order */
        if (Math.random() >= 0.5) {
            try {
                System.out.println("HelloTest sleeps for 500 ms");
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
