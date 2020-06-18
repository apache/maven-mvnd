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

import java.util.Properties;

import org.jboss.fuse.mvnd.client.ClientOutput;
import org.jboss.fuse.mvnd.junit.MvndTest;
import org.mockito.InOrder;
import org.mockito.Mockito;

@MvndTest(projectDir = "src/test/projects/single-module")
public class SingleModuleTest extends SingleModuleNativeIT {

    protected void assertJVM(ClientOutput output, Properties props) {
        final InOrder inOrder = Mockito.inOrder(output);
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-clean-plugin")
                        + ":clean {execution: default-clean}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-resources-plugin")
                        + ":resources {execution: default-resources}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-compiler-plugin")
                        + ":compile {execution: default-compile}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-resources-plugin")
                        + ":testResources {execution: default-testResources}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-compiler-plugin")
                        + ":testCompile {execution: default-testCompile}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-surefire-plugin")
                        + ":test {execution: default-test}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module:org.apache.maven.plugins:" + MvndTestUtil.plugin(props, "maven-install-plugin")
                        + ":install {execution: default-install}");
        inOrder.verify(output).projectStateChanged(
                "single-module",
                ":single-module");

        inOrder.verify(output).projectFinished("single-module");
    }

}
