/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.fuse.mvnd.jpm;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * Interface representing a process
 */
public interface Process extends Serializable {

    /**
     * Retrieves the PID of the process
     * @return the pid
     */
    int getPid();

    /**
     * Check if this process is still running
     * @return <code>true</code> if the process is running
     * @throws IOException if an error occurs
     */
    boolean isRunning() throws IOException;

    /**
     * Destroy the process.
     *
     * @throws IOException If an error occurs.
     */
    void destroy() throws IOException;

    static Process create(File dir, String command) throws IOException {
        return ProcessImpl.create(dir, command);
    }

    static Process attach(int pid) throws IOException {
        return ProcessImpl.attach(pid);
    }

}
