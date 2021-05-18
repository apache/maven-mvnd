/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.reproducer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class BootstrapMain {

    public static void main(String[] args) throws IOException {
        File baseDir = new File(args[0]);

        String vbMain = new String(readBytesFromInputStream(BootstrapMain.class.getResourceAsStream("Model.java")));
        vbMain = vbMain.replaceAll("REPLACETHIS", "Hello world");

        final Path file = Paths.get(baseDir.getAbsolutePath(),
                                                        "target",
                                                        "generated-sources",
                                                        "bootstrap",
                                                        "org", "reproducer", "Model.java");

        try {
            Files.deleteIfExists(file);
            Files.createDirectories(file.getParent());
            Files.copy(new ByteArrayInputStream(vbMain.getBytes()), file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to write file", e);
        }
    }

    public static byte[] readBytesFromInputStream(InputStream input) throws IOException {
        try {
            byte[] buffer = createBytesBuffer(input);
            ByteArrayOutputStream output = new ByteArrayOutputStream(buffer.length);

            int n = 0;
            while (-1 != (n = input.read(buffer))) {
                output.write(buffer, 0, n);
            }
            return output.toByteArray();
        } finally {
            try {
                input.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private static byte[] createBytesBuffer(InputStream input) throws IOException {
        return new byte[Math.max(input.available(), 8192)];
    }
}
