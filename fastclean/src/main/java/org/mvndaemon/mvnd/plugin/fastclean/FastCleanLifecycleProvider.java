/*
 * Copyright 2021 the original author or authors.
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
package org.mvndaemon.mvnd.plugin.fastclean;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.mapping.LifecyclePhase;
import org.slf4j.LoggerFactory;

@Singleton
@Named
public final class FastCleanLifecycleProvider extends AbstractEventSpy {

    @Inject
    public FastCleanLifecycleProvider(DefaultLifecycles defaultLifecycles) {
        LoggerFactory.getLogger(getClass()).warn("FastCleanLifecycleProvider initialized");

        defaultLifecycles.get("clean").getDefaultLifecyclePhases()
                .put("clean", new LifecyclePhase("org.mvndaemon.mvnd:mvnd-fastclean-maven-plugin:clean"));
    }

}
