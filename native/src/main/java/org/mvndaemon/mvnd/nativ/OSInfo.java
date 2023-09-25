/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.mvndaemon.mvnd.nativ;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

/**
 * Provides OS name and architecture name.
 *
 * @author leo
 */
public class OSInfo {

    public static final String X86 = "x86";
    public static final String X86_64 = "x86_64";
    public static final String IA64_32 = "ia64_32";
    public static final String IA64 = "ia64";
    public static final String PPC = "ppc";
    public static final String PPC64 = "ppc64";
    public static final String ARM64 = "arm64";

    private static final HashMap<String, String> archMapping = new HashMap<>();

    static {
        // x86 mappings
        archMapping.put(X86, X86);
        archMapping.put("i386", X86);
        archMapping.put("i486", X86);
        archMapping.put("i586", X86);
        archMapping.put("i686", X86);
        archMapping.put("pentium", X86);

        // x86_64 mappings
        archMapping.put(X86_64, X86_64);
        archMapping.put("amd64", X86_64);
        archMapping.put("em64t", X86_64);
        archMapping.put("universal", X86_64); // Needed for openjdk7 in Mac

        // Itenium 64-bit mappings
        archMapping.put(IA64, IA64);
        archMapping.put("ia64w", IA64);

        // Itenium 32-bit mappings, usually an HP-UX construct
        archMapping.put(IA64_32, IA64_32);
        archMapping.put("ia64n", IA64_32);

        // PowerPC mappings
        archMapping.put(PPC, PPC);
        archMapping.put("power", PPC);
        archMapping.put("powerpc", PPC);
        archMapping.put("power_pc", PPC);
        archMapping.put("power_rs", PPC);

        // TODO: PowerPC 64bit mappings
        archMapping.put(PPC64, PPC64);
        archMapping.put("power64", PPC64);
        archMapping.put("powerpc64", PPC64);
        archMapping.put("power_pc64", PPC64);
        archMapping.put("power_rs64", PPC64);

        archMapping.put("aarch64", ARM64);
    }

    public static void main(String[] args) {
        if (args.length >= 1) {
            if ("--os".equals(args[0])) {
                System.out.print(getOSName());
                return;
            } else if ("--arch".equals(args[0])) {
                System.out.print(getArchName());
                return;
            }
        }

        System.out.print(getNativeLibFolderPathForCurrentOS());
    }

    public static String getNativeLibFolderPathForCurrentOS() {
        return getOSName() + "/" + getArchName();
    }

    public static String getOSName() {
        return translateOSNameToFolderName(System.getProperty("os.name"));
    }

    public static boolean isAndroid() {
        return System.getProperty("java.runtime.name", "").toLowerCase().contains("android");
    }

    public static boolean isAlpine() {
        try {
            Process p = Runtime.getRuntime().exec("cat /etc/os-release | grep ^ID");
            p.waitFor();

            InputStream in = p.getInputStream();
            try {
                return readFully(in).toLowerCase().contains("alpine");
            } finally {
                in.close();
            }

        } catch (Throwable e) {
            return false;
        }
    }

    static String getHardwareName() {
        try {
            Process p = Runtime.getRuntime().exec("uname -m");
            p.waitFor();

            InputStream in = p.getInputStream();
            try {
                return readFully(in);
            } finally {
                in.close();
            }
        } catch (Throwable e) {
            System.err.println("Error while running uname -m: " + e.getMessage());
            return "unknown";
        }
    }

    private static String readFully(InputStream in) throws IOException {
        int readLen = 0;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[32];
        while ((readLen = in.read(buf, 0, buf.length)) >= 0) {
            b.write(buf, 0, readLen);
        }
        return b.toString();
    }

    static String resolveArmArchType() {
        if (System.getProperty("os.name").contains("Linux")) {
            String armType = getHardwareName();
            // armType (uname -m) can be armv5t, armv5te, armv5tej, armv5tejl, armv6, armv7, armv7l, aarch64, i686
            if (armType.startsWith("armv6")) {
                // Raspberry PI
                return "armv6";
            } else if (armType.startsWith("armv7")) {
                // Generic
                return "armv7";
            } else if (armType.startsWith("armv5")) {
                // Use armv5, soft-float ABI
                return "arm";
            } else if (armType.equals("aarch64")) {
                // Use arm64
                return "arm64";
            }

            // Java 1.8 introduces a system property to determine armel or armhf
            // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=8005545
            String abi = System.getProperty("sun.arch.abi");
            if (abi != null && abi.startsWith("gnueabihf")) {
                return "armv7";
            }
        }
        // Use armv5, soft-float ABI
        return "arm";
    }

    public static String getArchName() {
        String osArch = System.getProperty("os.arch");
        // For Android
        if (isAndroid()) {
            return "android-arm";
        }

        if (osArch.startsWith("arm")) {
            osArch = resolveArmArchType();
        } else {
            String lc = osArch.toLowerCase(Locale.US);
            if (archMapping.containsKey(lc)) return archMapping.get(lc);
        }
        return translateArchNameToFolderName(osArch);
    }

    static String translateOSNameToFolderName(String osName) {
        if (osName.contains("Windows")) {
            return "Windows";
        } else if (osName.contains("Mac") || osName.contains("Darwin")) {
            return "Mac";
            //        } else if (isAlpine()) {
            //            return "Linux-Alpine";
        } else if (osName.contains("Linux")) {
            return "Linux";
        } else if (osName.contains("AIX")) {
            return "AIX";
        } else {
            return osName.replaceAll("\\W", "");
        }
    }

    static String translateArchNameToFolderName(String archName) {
        return archName.replaceAll("\\W", "");
    }
}
