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
package org.mvndaemon.mvnd.client.svm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.slf4j.MDC;
import org.slf4j.impl.StaticMDCBinder;
import org.slf4j.spi.MDCAdapter;

@TargetClass(MDC.class)
final class StaticMDCBinderSubstitution {

    @Substitute
    private static MDCAdapter bwCompatibleGetMDCAdapterFromBinder() throws NoClassDefFoundError {
        return StaticMDCBinder.SINGLETON.getMDCA();
    }

}
