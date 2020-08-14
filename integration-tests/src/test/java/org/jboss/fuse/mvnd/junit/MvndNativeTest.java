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
package org.jboss.fuse.mvnd.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.jboss.fuse.mvnd.client.Client;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MvndTestExtension.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MvndNativeTest {

    /**
     * The path to the root directory of a test project relative to the current maven module directory. E.g.
     * <code>@MvndTest(projectDir = "src/test/projects/my-project")</code>
     */
    String projectDir();

    /**
     * Timeout for {@link Client#execute(org.jboss.fuse.mvnd.common.ClientOutput, java.util.List)} in seconds
     */
    long timeoutSec() default 300;
}
