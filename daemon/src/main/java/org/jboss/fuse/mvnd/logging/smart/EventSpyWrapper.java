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

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.eventspy.EventSpy;

@Singleton
@Named
public class EventSpyWrapper implements EventSpy {

    @Override
    public void init(Context context) throws Exception {
        AbstractLoggingSpy.instance().init(context);
    }

    @Override
    public void onEvent(Object event) throws Exception {
        AbstractLoggingSpy.instance().onEvent(event);
    }

    @Override
    public void close() throws Exception {
        AbstractLoggingSpy.instance().close();
    }
}
