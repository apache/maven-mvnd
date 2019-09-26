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
package org.jboss.fuse.mvnd.logging.smart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.Display;

public class MavenLoggingSpy extends AbstractLoggingSpy {

    private Terminal terminal;
    private Display display;

    public MavenLoggingSpy() {
    }

    @Override
    public void init(Context context) throws Exception {
        terminal = (Terminal) context.getData().get("terminal");
        if (terminal == null) {
            terminal = TerminalBuilder.terminal();
        }
        display = new Display(terminal, false);
        super.init(context);
    }

    @Override
    public void close() throws Exception {
        display.update(Collections.emptyList(), 0);
        for (String event : events) {
            terminal.writer().print(event);
        }
        terminal.flush();
        terminal.close();
        terminal = null;
        display = null;
        super.close();
    }

    protected void update() {
        Size size = terminal.getSize();
        display.resize(size.getRows(), size.getColumns());
        List<AttributedString> lines = new ArrayList<>();
        lines.add(new AttributedString("Building..."));
        for (ProjectBuild build : projects.values()) {
            lines.add(build.toDisplay());
        }
        display.update(lines, -1);
    }

}
