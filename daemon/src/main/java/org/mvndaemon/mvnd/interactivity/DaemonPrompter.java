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
package org.mvndaemon.mvnd.interactivity;

import javax.inject.Named;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.apache.maven.logging.ProjectBuildLogAppender;
import org.codehaus.plexus.components.interactivity.AbstractInputHandler;
import org.codehaus.plexus.components.interactivity.InputHandler;
import org.codehaus.plexus.components.interactivity.OutputHandler;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.eclipse.sisu.Priority;
import org.eclipse.sisu.Typed;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.daemon.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
@Priority(20)
@Typed({Prompter.class, InputHandler.class, OutputHandler.class})
public class DaemonPrompter extends AbstractInputHandler implements Prompter, InputHandler, OutputHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DaemonPrompter.class);

    @Override
    public String prompt(String message) throws PrompterException {
        return prompt(message, null, null);
    }

    @Override
    public String prompt(String message, String defaultReply) throws PrompterException {
        return prompt(message, null, defaultReply);
    }

    @Override
    public String prompt(String message, List possibleValues) throws PrompterException {
        return prompt(message, possibleValues, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String prompt(String message, List possibleValues, String defaultReply) throws PrompterException {
        return doPrompt(message, possibleValues, defaultReply, false);
    }

    @Override
    public String promptForPassword(String message) throws PrompterException {
        return doPrompt(message, null, null, true);
    }

    @Override
    public void showMessage(String message) throws PrompterException {
        try {
            doDisplay(message);
        } catch (IOException e) {
            throw new PrompterException("Failed to present prompt", e);
        }
    }

    @Override
    public String readLine() throws IOException {
        return doPrompt(null, false);
    }

    @Override
    public String readPassword() throws IOException {
        return doPrompt(null, true);
    }

    @Override
    public void write(String line) throws IOException {
        doDisplay(line);
    }

    @Override
    public void writeLine(String line) throws IOException {
        doDisplay(line + "\n");
    }

    String doPrompt(String message, List<Object> possibleValues, String defaultReply, boolean password)
            throws PrompterException {
        String formattedMessage = formatMessage(message, possibleValues, defaultReply);
        String line;
        do {
            try {
                line = doPrompt(formattedMessage, password);
                if (line == null && defaultReply == null) {
                    throw new IOException("EOF");
                }
            } catch (IOException e) {
                throw new PrompterException("Failed to prompt user", e);
            }
            if (line == null || line.isEmpty()) {
                line = defaultReply;
            }
            if (line != null && (possibleValues != null && !possibleValues.contains(line))) {
                try {
                    doDisplay("Invalid selection.\n");
                } catch (IOException e) {
                    throw new PrompterException("Failed to present feedback", e);
                }
            }
        } while (line == null || (possibleValues != null && !possibleValues.contains(line)));
        return line;
    }

    private String formatMessage(String message, List<Object> possibleValues, String defaultReply) {
        StringBuilder formatted = new StringBuilder(message.length() * 2);
        formatted.append(message);
        if (possibleValues != null && !possibleValues.isEmpty()) {
            formatted.append(" (");
            for (Iterator<?> it = possibleValues.iterator(); it.hasNext(); ) {
                String possibleValue = String.valueOf(it.next());
                formatted.append(possibleValue);
                if (it.hasNext()) {
                    formatted.append('/');
                }
            }
            formatted.append(')');
        }
        if (defaultReply != null) {
            formatted.append(' ').append(defaultReply).append(": ");
        }
        return formatted.toString();
    }

    private void doDisplay(String message) throws IOException {
        try {
            Connection con = Objects.requireNonNull(Connection.getCurrent());
            String projectId = ProjectBuildLogAppender.getProjectId();
            Message.ProjectEvent msg = Message.display(projectId, message);
            LOGGER.debug("Sending display request: {}", msg);
            con.dispatch(msg);
        } catch (Exception e) {
            throw new IOException("Unable to display message", e);
        }
    }

    private String doPrompt(String message, boolean password) throws IOException {
        try {
            Connection con = Objects.requireNonNull(Connection.getCurrent());
            String projectId = ProjectBuildLogAppender.getProjectId();
            String uid = UUID.randomUUID().toString();
            Message.Prompt msg = new Message.Prompt(projectId, uid, message, password);
            LOGGER.debug("Requesting prompt: {}", msg);
            Message.PromptResponse res = con.request(msg, Message.PromptResponse.class, r -> uid.equals(r.getUid()));
            LOGGER.debug("Received response: {}", res.getMessage());
            return res.getMessage();
        } catch (Exception e) {
            throw new IOException("Unable to prompt user", e);
        }
    }
}
