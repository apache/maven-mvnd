/*
 * Copyright 2020 the original author or authors.
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
package org.jboss.fuse.mvnd.daemon;

import java.util.function.Predicate;
import org.jboss.fuse.mvnd.common.Message;

public interface Connection {

    static Connection getCurrent() {
        return Holder.CURRENT;
    }

    static void setCurrent(Connection connection) {
        Holder.CURRENT = connection;
    }

    void dispatch(Message message);

    <T extends Message> T request(Message request, Class<T> responseType, Predicate<T> matcher);

    class Holder {
        static Connection CURRENT;
    }

}
