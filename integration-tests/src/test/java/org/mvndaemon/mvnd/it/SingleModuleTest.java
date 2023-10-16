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
package org.mvndaemon.mvnd.it;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.mvndaemon.mvnd.assertj.MatchInOrderAmongOthers;
import org.mvndaemon.mvnd.assertj.TestClientOutput;
import org.mvndaemon.mvnd.common.Message;
import org.mvndaemon.mvnd.junit.MvndTest;

@MvndTest(projectDir = "src/test/projects/single-module")
class SingleModuleTest extends SingleModuleNativeIT {

    protected void assertJVM(TestClientOutput o, Properties props) {
        final List<String> filteredMessages = o.getMessages().stream()
                .filter(m -> m.getType() == Message.MOJO_STARTED)
                .map(Object::toString)
                .collect(Collectors.toList());

        Assertions.assertThat(filteredMessages)
                .is(new MatchInOrderAmongOthers<>(
                        mojoStarted(props, "maven-clean-plugin", "clean", "default-clean"),
                        mojoStarted(props, "maven-resources-plugin", "resources", "default-resources"),
                        mojoStarted(props, "maven-compiler-plugin", "compile", "default-compile"),
                        mojoStarted(props, "maven-resources-plugin", "testResources", "default-testResources"),
                        mojoStarted(props, "maven-compiler-plugin", "testCompile", "default-testCompile"),
                        mojoStarted(props, "maven-surefire-plugin", "test", "default-test"),
                        mojoStarted(props, "maven-install-plugin", "install", "default-install")));
    }

    String mojoStarted(Properties props, String pluginArtifactId, String mojo, String executionId) {
        return "\\Q"
                + Message.mojoStarted(
                                "single-module",
                                "org.apache.maven.plugins",
                                pluginArtifactId,
                                pluginArtifactId.replace("maven-", "").replace("-plugin", ""),
                                props.getProperty(pluginArtifactId + ".version"),
                                mojo,
                                executionId)
                        .toString()
                + "\\E";
    }
}
