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
package org.mvndaemon.mvnd.sync;

/**
 * Constants used for the inter-process communication protocol.
 */
public class IpcMessages {

    public static final String REQUEST_CONTEXT = "request-context";
    public static final String REQUEST_ACQUIRE = "request-acquire";
    public static final String REQUEST_CLOSE = "request-close";
    public static final String RESPONSE_CONTEXT = "response-context";
    public static final String RESPONSE_ACQUIRE = "response-acquire";
    public static final String RESPONSE_CLOSE = "response-close";

}
