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
package org.jboss.fuse.mvnd.junit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.jboss.fuse.mvnd.common.DaemonInfo;
import org.jboss.fuse.mvnd.common.DaemonRegistry;
import org.jboss.fuse.mvnd.jpm.ProcessImpl;

public class TestRegistry extends DaemonRegistry {

    public TestRegistry(Path registryFile) {
        super(registryFile);
    }

    public void killAll() {
        List<DaemonInfo> daemons;
        final int timeout = 5000;
        final long deadline = System.currentTimeMillis() + timeout;
        while (!(daemons = getAll()).isEmpty()) {
            for (DaemonInfo di : daemons) {
                try {
                    new ProcessImpl(di.getPid()).destroy();
                } catch (IOException t) {
                    System.out.println("Daemon " + di.getUid() + ": " + t.getMessage());
                } catch (Exception t) {
                    System.out.println("Daemon " + di.getUid() + ": " + t);
                } finally {
                    remove(di.getUid());
                }
            }
            if (deadline < System.currentTimeMillis() && !getAll().isEmpty()) {
                throw new RuntimeException("Could not stop all mvnd daemons within " + timeout + " ms");
            }
        }
    }

}
