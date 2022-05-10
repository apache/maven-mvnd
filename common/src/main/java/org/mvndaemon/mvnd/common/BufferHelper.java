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
package org.mvndaemon.mvnd.common;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Consumer;

/**
 * Original code from
 * https://github.com/classgraph/classgraph/blob/latest/src/main/java/nonapi/io/github/classgraph/utils/FileUtils.java#L543
 */
public class BufferHelper {

    private static boolean PRE_JAVA_9 = System.getProperty("java.specification.version", "9").startsWith("1.");

    /** The DirectByteBuffer.cleaner() method. */
    private static Method directByteBufferCleanerMethod;

    /** The Cleaner.clean() method. */
    private static Method cleanerCleanMethod;

    //    /** The jdk.incubator.foreign.MemorySegment class (JDK14+). */
    //    private static Class<?> memorySegmentClass;
    //
    //    /** The jdk.incubator.foreign.MemorySegment.ofByteBuffer method (JDK14+). */
    //    private static Method memorySegmentOfByteBufferMethod;
    //
    //    /** The jdk.incubator.foreign.MemorySegment.ofByteBuffer method (JDK14+). */
    //    private static Method memorySegmentCloseMethod;

    /** The attachment() method. */
    private static Method attachmentMethod;

    /** The Unsafe object. */
    private static Object theUnsafe;

    /**
     * Get the clean() method, attachment() method, and theUnsafe field, called inside doPrivileged.
     */
    static void lookupCleanMethodPrivileged() {
        if (PRE_JAVA_9) {
            try {
                // See:
                // https://stackoverflow.com/a/19447758/3950982
                cleanerCleanMethod = Class.forName("sun.misc.Cleaner").getDeclaredMethod("clean");
                cleanerCleanMethod.setAccessible(true);
                final Class<?> directByteBufferClass = Class.forName("sun.nio.ch.DirectBuffer");
                directByteBufferCleanerMethod = directByteBufferClass.getDeclaredMethod("cleaner");
                attachmentMethod = directByteBufferClass.getMethod("attachment");
                attachmentMethod.setAccessible(true);
            } catch (final SecurityException e) {
                throw new RuntimeException(
                        "You need to grant classgraph RuntimePermission(\"accessClassInPackage.sun.misc\") "
                                + "and ReflectPermission(\"suppressAccessChecks\")",
                        e);
            } catch (final ReflectiveOperationException | LinkageError e) {
                // Ignore
            }
        } else {
            //boolean jdkSuccess = false;
            //    // TODO: This feature is in incubation now -- enable after it leaves incubation.
            //    // To enable this feature, need to:
            //    // -- add whatever the "jdk.incubator.foreign" module name is replaced with to <Import-Package>
            //    //    in pom.xml, as an optional dependency
            //    // -- add the same module name to module-info.java as a "requires static" optional dependency
            //    // -- build two versions of module.java: the existing one, for --release=9, and a new version,
            //    //    for --release=15 (or whatever the final release version ends up being when the feature is
            //    //    moved out of incubation).
            //    try {
            //        // JDK 14+ Invoke MemorySegment.ofByteBuffer(myByteBuffer).close()
            //        // https://stackoverflow.com/a/26777380/3950982
            //        memorySegmentClass = Class.forName("jdk.incubator.foreign.MemorySegment");
            //        memorySegmentCloseMethod = AutoCloseable.class.getDeclaredMethod("close");
            //        memorySegmentOfByteBufferMethod = memorySegmentClass.getMethod("ofByteBuffer",
            //                ByteBuffer.class);
            //        jdk14Success = true;
            //    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e1) {
            //        // Fall through
            //    }
            //if (!jdk14Success) { // In JDK9+, calling sun.misc.Cleaner.clean() gives a reflection warning on stderr,
            // so we need to call Unsafe.theUnsafe.invokeCleaner(byteBuffer) instead, which makes
            // the same call, but does not print the reflection warning.
            try {
                Class<?> unsafeClass;
                try {
                    unsafeClass = Class.forName("sun.misc.Unsafe");
                } catch (final ReflectiveOperationException | LinkageError e) {
                    throw new RuntimeException("Could not get class sun.misc.Unsafe", e);
                }
                final Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
                theUnsafeField.setAccessible(true);
                theUnsafe = theUnsafeField.get(null);
                cleanerCleanMethod = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
                cleanerCleanMethod.setAccessible(true);
            } catch (final SecurityException e) {
                throw new RuntimeException(
                        "You need to grant classgraph RuntimePermission(\"accessClassInPackage.sun.misc\") "
                                + "and ReflectPermission(\"suppressAccessChecks\")",
                        e);
            } catch (final ReflectiveOperationException | LinkageError ex) {
                // Ignore
            }
            //}
        }
    }

    static {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                lookupCleanMethodPrivileged();
                return null;
            }
        });
    }

    private static boolean closeDirectByteBufferPrivileged(final ByteBuffer byteBuffer, final Consumer<String> log) {
        if (!byteBuffer.isDirect()) {
            // Nothing to do
            return true;
        }
        try {
            if (PRE_JAVA_9) {
                if (attachmentMethod == null) {
                    if (log != null) {
                        log.accept("Could not unmap ByteBuffer, attachmentMethod == null");
                    }
                    return false;
                }
                // Make sure duplicates and slices are not cleaned, since this can result in duplicate
                // attempts to clean the same buffer, which trigger a crash with:
                // "A fatal error has been detected by the Java Runtime Environment: EXCEPTION_ACCESS_VIOLATION"
                // See: https://stackoverflow.com/a/31592947/3950982
                if (attachmentMethod.invoke(byteBuffer) != null) {
                    // Buffer is a duplicate or slice
                    return false;
                }
                // Invoke ((DirectBuffer) byteBuffer).cleaner().clean()
                if (directByteBufferCleanerMethod == null) {
                    if (log != null) {
                        log.accept("Could not unmap ByteBuffer, cleanerMethod == null");
                    }
                    return false;
                }
                try {
                    directByteBufferCleanerMethod.setAccessible(true);
                } catch (final Exception e) {
                    if (log != null) {
                        log.accept("Could not unmap ByteBuffer, cleanerMethod.setAccessible(true) failed");
                    }
                    return false;
                }
                final Object cleanerInstance = directByteBufferCleanerMethod.invoke(byteBuffer);
                if (cleanerInstance == null) {
                    if (log != null) {
                        log.accept("Could not unmap ByteBuffer, cleaner == null");
                    }
                    return false;
                }
                if (cleanerCleanMethod == null) {
                    if (log != null) {
                        log.accept("Could not unmap ByteBuffer, cleanMethod == null");
                    }
                    return false;
                }
                try {
                    cleanerCleanMethod.invoke(cleanerInstance);
                    return true;
                } catch (final Exception e) {
                    if (log != null) {
                        log.accept("Could not unmap ByteBuffer, cleanMethod.invoke(cleaner) failed: " + e);
                    }
                    return false;
                }
                //    } else if (memorySegmentOfByteBufferMethod != null) {
                //        // JDK 14+
                //        final Object memorySegment = memorySegmentOfByteBufferMethod.invoke(null, byteBuffer);
                //        if (memorySegment == null) {
                //            if (log != null) {
                //                log.log("Got null MemorySegment, could not unmap ByteBuffer");
                //            }
                //            return false;
                //        }
                //        memorySegmentCloseMethod.invoke(memorySegment);
                //        return true;
            } else {
                if (theUnsafe == null) {
                    if (log != null) {
                        log.accept("Could not unmap ByteBuffer, theUnsafe == null");
                    }
                    return false;
                }
                if (cleanerCleanMethod == null) {
                    if (log != null) {
                        log.accept("Could not unmap ByteBuffer, cleanMethod == null");
                    }
                    return false;
                }
                try {
                    cleanerCleanMethod.invoke(theUnsafe, byteBuffer);
                    return true;
                } catch (final IllegalArgumentException e) {
                    // Buffer is a duplicate or slice
                    return false;
                }
            }
        } catch (final ReflectiveOperationException | SecurityException e) {
            if (log != null) {
                log.accept("Could not unmap ByteBuffer: " + e);
            }
            return false;
        }
    }

    /**
     * Close a {@code DirectByteBuffer} -- in particular, will unmap a
     * {@link java.nio.MappedByteBuffer}.
     *
     * @param  byteBuffer The {@link ByteBuffer} to close/unmap.
     * @return            True if the byteBuffer was closed/unmapped (or if the ByteBuffer was null or non-direct).
     */
    public static boolean closeDirectByteBuffer(final ByteBuffer byteBuffer) {
        return closeDirectByteBuffer(byteBuffer, null);
    }

    /**
     * Close a {@code DirectByteBuffer} -- in particular, will unmap a
     * {@link java.nio.MappedByteBuffer}.
     *
     * @param  byteBuffer The {@link ByteBuffer} to close/unmap.
     * @param  log        The log.
     * @return            True if the byteBuffer was closed/unmapped (or if the ByteBuffer was null or non-direct).
     */
    public static boolean closeDirectByteBuffer(final ByteBuffer byteBuffer, final Consumer<String> log) {
        if (byteBuffer != null && byteBuffer.isDirect()) {
            return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                @Override
                public Boolean run() {
                    return closeDirectByteBufferPrivileged(byteBuffer, log);
                }
            });
        } else {
            // Nothing to unmap
            return false;
        }
    }

}
