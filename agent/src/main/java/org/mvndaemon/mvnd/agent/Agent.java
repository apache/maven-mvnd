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
package org.mvndaemon.mvnd.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import javassist.ClassPool;
import javassist.CtClass;

public class Agent {

    public static final String START_WITH_PIPES = "if (redirects != null\n"
            + "         && redirects[1] == ProcessBuilder$Redirect.INHERIT\n"
            + "         && redirects[2] == ProcessBuilder$Redirect.INHERIT) {\n"
            + "   redirects[1] = redirects[2] = ProcessBuilder$Redirect.PIPE;"
            + "   Process p = start(redirects);\n"
            + "   AgentHelper.pump(p.getInputStream(), System.out);\n"
            + "   AgentHelper.pump(p.getErrorStream(), System.err);\n"
            + "   return p;\n"
            + "}";

    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                if ("java/lang/ProcessBuilder".equals(className)) {
                    try {
                        ClassPool pool = ClassPool.getDefault();
                        CtClass clazz = pool.get("java.lang.ProcessBuilder");
                        pool.importPackage("org.mvndaemon.mvnd.pump");
                        clazz.getDeclaredMethod("start",
                                new CtClass[] { clazz.getClassPool().get("java.lang.ProcessBuilder$Redirect[]") })
                                .insertBefore(START_WITH_PIPES);
                        byte[] data = clazz.toBytecode();
                        clazz.detach();
                        return data;
                    } catch (Throwable e) {
                        System.err.println(e);
                        throw new IllegalClassFormatException(e.toString());
                    }
                } else {
                    return classfileBuffer;
                }
            }
        });
    }

}
