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
package org.jboss.fuse.mvnd.it;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.jboss.fuse.mvnd.assertj.MatchInOrderAmongOthers;
import org.jboss.fuse.mvnd.assertj.TestClientOutput;
import org.jboss.fuse.mvnd.common.Message;
import org.jboss.fuse.mvnd.junit.MvndTest;

@MvndTest(projectDir = "src/test/projects/single-module")
public class SingleModuleTest extends SingleModuleNativeIT {

    protected void assertJVM(TestClientOutput o, Properties props) {
        final List<String> filteredMessages = o.getMessages().stream()
                .filter(m -> m.getType() == Message.MOJO_STARTED)
                .map(m -> m.toString())
                .collect(Collectors.toList());

        Assertions.assertThat(filteredMessages)
                .is(new MatchInOrderAmongOthers<>(
                        "\\Q:single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-clean-plugin")
                                + ":clean {execution: default-clean}\\E",
                        "\\Q:single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-resources-plugin")
                                + ":resources {execution: default-resources}\\E",
                        "\\Q:single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-compiler-plugin")
                                + ":compile {execution: default-compile}\\E",
                        "\\Q:single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-resources-plugin")
                                + ":testResources {execution: default-testResources}\\E",
                        "\\Q:single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-compiler-plugin")
                                + ":testCompile {execution: default-testCompile}\\E",
                        "\\Q:single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-surefire-plugin")
                                + ":test {execution: default-test}\\E",
                        "\\Q:single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-install-plugin")
                                + ":install {execution: default-install}\\E"));

    }

}
