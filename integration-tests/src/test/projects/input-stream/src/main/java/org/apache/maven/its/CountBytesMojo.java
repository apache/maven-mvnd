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
package org.apache.maven.its;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;

/**
 * Goal which counts the number of bytes available on stdin.
 */
@Mojo( name = "countbytes", requiresProject = false )
public class CountBytesMojo
    extends AbstractMojo
{

    public void execute()
        throws MojoExecutionException
    {
        getLog().info("Reading from standard input. Type 'exit' to stop.");

        int available = 0;
        byte[] bytes = new byte[] {};

        try {
            available = System.in.available();
            bytes = System.in.readAllBytes();
        } catch (Exception ex) {
            getLog().error("Failed to read from stdin", ex);
        }

        System.out.println("Saw " + available + " bytes available from stdin. Actually read " + bytes.length + " bytes from stdin.");
    }
}
