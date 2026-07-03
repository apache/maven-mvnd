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

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.mvndaemon.mvnd.common.Environment;

/**
 * Under Maven 3.9, {@code afterProjectsRead} model mutation is honored by Surefire, so this participant injects an
 * mvnd {@code <forkNode>} into surefire {@code maven-surefire-plugin} and {@code maven-failsafe-plugin}
 * configurations. Surefire then loads {@code MvndForkNodeFactory} (added to its plugin realm by
 * {@link org.mvndaemon.mvnd.cache.invalidating.InvalidatingPluginRealmCache}) and reports per-test events tagged
 * with the injected {@code <projectId>}. Skips projects where the user already configured a {@code <forkNode>} or
 * when the feature is disabled.
 */
@Named
@Singleton
public class MvndTestProgressLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    private static final String FORK_NODE_IMPL = "org.mvndaemon.mvnd.forknode.MvndForkNodeFactory";
    private static final String SUREFIRE_KEY = "org.apache.maven.plugins:maven-surefire-plugin";
    private static final String FAILSAFE_KEY = "org.apache.maven.plugins:maven-failsafe-plugin";

    @Override
    public void afterProjectsRead(MavenSession session) {
        if (!isEnabled()) {
            return;
        }
        for (MavenProject project : session.getProjects()) {
            injectForPlugin(project, SUREFIRE_KEY);
            injectForPlugin(project, FAILSAFE_KEY);
        }
    }

    private void injectForPlugin(MavenProject project, String pluginKey) {
        if (project.getBuild() == null) {
            return;
        }
        Plugin plugin = project.getBuild().getPluginsAsMap().get(pluginKey);
        if (plugin == null) {
            return;
        }
        if (!supportsForkNode(plugin.getVersion())) {
            // Never inject into a Surefire/Failsafe that cannot load the fork-node SPI; it would fail the build.
            return;
        }
        String projectId = project.getArtifactId();
        plugin.setConfiguration(withForkNode((Xpp3Dom) plugin.getConfiguration(), projectId));
        for (PluginExecution execution : plugin.getExecutions()) {
            execution.setConfiguration(withForkNode((Xpp3Dom) execution.getConfiguration(), projectId));
        }
    }

    private Xpp3Dom withForkNode(Xpp3Dom config, String projectId) {
        if (config == null) {
            config = new Xpp3Dom("configuration");
        }
        if (config.getChild("forkNode") != null) {
            return config; // respect a user-configured fork node
        }
        Xpp3Dom forkNode = new Xpp3Dom("forkNode");
        forkNode.setAttribute("implementation", FORK_NODE_IMPL);
        Xpp3Dom pid = new Xpp3Dom("projectId");
        pid.setValue(projectId);
        forkNode.addChild(pid);
        config.addChild(forkNode);
        return config;
    }

    private static boolean isEnabled() {
        return Environment.MVND_TEST_PROGRESS
                .asOptional()
                .map(Boolean::parseBoolean)
                .orElse(Boolean.TRUE);
    }

    /**
     * The {@code forkNode} extension SPI ({@code ForkNodeFactory} / {@code SurefireForkNodeFactory}) exists only in
     * Surefire {@code >= 3.0.0-M5}. Injecting {@code <forkNode>} into anything older makes the build fail hard
     * ("unknown parameter forkNode" or a missing implementation class), so guard on the resolved plugin version.
     */
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
