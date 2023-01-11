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

package org.mvndaemon.mvnd.test.extension.with.api.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import javax.inject.Inject;

import org.mvndaemon.mvnd.test.extension.with.api.extension.TheApi;

@Mojo( name = "mojo")
public class TheMojo extends AbstractMojo {
    @Inject
    TheApi api;

    public void execute() throws MojoExecutionException {
        api.sayHello();
    }
}