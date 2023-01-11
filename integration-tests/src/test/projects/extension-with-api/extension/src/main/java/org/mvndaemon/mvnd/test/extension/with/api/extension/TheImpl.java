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
package org.mvndaemon.mvnd.test.extension.with.api.extension;

import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.eventspy.AbstractEventSpy;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.sisu.Typed;

@Named
@Typed({EventSpy.class, TheApi.class})
@Singleton
public class TheImpl extends AbstractEventSpy implements TheApi {
    private boolean init;

    @Override
    public void init(Context context) throws Exception {
        System.out.println("Api extension is initialized");
        init = true;
    }

    @Override
    public void sayHello() {
        if (init) {
            System.out.println("Hello World!");
        } else {
            throw new IllegalStateException("Not initialized");
        }
    }
}