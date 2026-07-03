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
package org.mvndaemon.mvnd.daemon;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.lifecycle.MojoExecutionConfigurator;
import org.apache.maven.lifecycle.internal.DefaultMojoExecutionConfigurator;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.sisu.Priority;
import org.mvndaemon.mvnd.common.Environment;

/**
 * Overrides the default Maven 4 mojo-execution configurator to auto-inject an mvnd {@code <forkNode>} into surefire
 * {@code test} and failsafe {@code integration-test} executions, unless the user already configured one or the feature
 * is disabled. This is the Maven-4 mechanism: model mutation in {@code afterProjectsRead} is ignored under Maven 4, so
 * the injection must happen where Surefire's <em>effective</em> configuration is assembled.
 *
 * <p>The referenced factory lives in {@code org.mvndaemon.mvnd.forknode}, a package deliberately kept out of the
 * core-realm-exported {@code org.mvndaemon.mvnd.testprogress} prefix so the surefire plugin realm loads it from its own
 * class path (see {@code InvalidatingPluginRealmCache}) rather than importing it from the core realm.
 *
 * <p>The Sisu wiring here is load-bearing and non-obvious. Maven 4's concurrent {@code BuildPlanExecutor} selects the
 * configurator via {@code Map<String, MojoExecutionConfigurator>.get("default")}, so this component must:
 * <ul>
 *   <li>be {@code @Named("default")} (an empty {@code @Named} keys the map entry by fully-qualified class name, not
 *       {@code "default"}, so it would never be selected);</li>
 *   <li>declare {@code implements MojoExecutionConfigurator} explicitly (Sisu does not publish the interface it only
 *       inherits through the superclass, so without this it is absent from the map);</li>
 *   <li>use a no-argument constructor (Sisu silently drops a candidate from a collection binding when a constructor
 *       dependency such as {@code MessageBuilderFactory} cannot be resolved in that scope; the superclass no-arg
 *       constructor supplies a default {@code MessageBuilderFactory} itself);</li>
 *   <li>carry {@code @Priority(10)} to win the {@code "default"} key over maven-core's own binding.</li>
 * </ul>
 */
@Named("default")
@Singleton
@Priority(10)
public class MvndMojoExecutionConfigurator extends DefaultMojoExecutionConfigurator
        implements MojoExecutionConfigurator {

    private static final String FORK_NODE_IMPL = "org.mvndaemon.mvnd.forknode.MvndForkNodeFactory";

    public MvndMojoExecutionConfigurator() {
        super();
    }

    @Override
    public void configure(MavenProject project, MojoExecution mojoExecution, boolean allowPluginLevelConfig) {
        super.configure(project, mojoExecution, allowPluginLevelConfig);
        if (!isEnabled() || !isTestGoal(mojoExecution) || !supportsForkNode(mojoExecution.getVersion())) {
            // Never inject into a Surefire/Failsafe that cannot load the fork-node SPI; it would fail the build.
            return;
        }
        Xpp3Dom config = mojoExecution.getConfiguration();
        if (config == null) {
            config = new Xpp3Dom("configuration");
            mojoExecution.setConfiguration(config);
        }
        if (config.getChild("forkNode") != null) {
            return; // user already configured a fork node
        }
        Xpp3Dom forkNode = new Xpp3Dom("forkNode");
        forkNode.setAttribute("implementation", FORK_NODE_IMPL);
        Xpp3Dom projectId = new Xpp3Dom("projectId");
        projectId.setValue(project.getArtifactId());
        forkNode.addChild(projectId);
        config.addChild(forkNode);
    }

    private static boolean isEnabled() {
        return Environment.MVND_TEST_PROGRESS
                .asOptional()
                .map(Boolean::parseBoolean)
                .orElse(Boolean.TRUE);
    }

    private static boolean isTestGoal(MojoExecution e) {
        String artifactId = e.getArtifactId();
        String goal = e.getGoal();
        return ("maven-surefire-plugin".equals(artifactId) && "test".equals(goal))
                || ("maven-failsafe-plugin".equals(artifactId) && "integration-test".equals(goal));
    }

    /** The Surefire fork-node SPI exists since 3.0.0-M5; older versions must be skipped so the build never fails. */
    static boolean supportsForkNode(String version) {
        if (version == null) {
            return false;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-M(\\d+))?")
                .matcher(version);
        if (!m.find()) {
            return false;
        }
        int major = Integer.parseInt(m.group(1));
        if (major != 3) {
            return major > 3;
        }
        String milestone = m.group(4);
        return milestone == null || Integer.parseInt(milestone) >= 5;
    }
}
