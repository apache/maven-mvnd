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
 *
 * Backward-compatible stat() shim for x86_64 targeting glibc < 2.33.
 *
 * Before glibc 2.33, stat() was a macro expanding to __xstat(_STAT_VER, ...).
 * GraalVM 25 native-image calls stat() directly (compiled against glibc 2.33+
 * headers where stat is a real function). This shim redirects stat() to
 * __xstat() which has been available since glibc 2.2.5 on x86_64.
 */

struct stat;
extern int __xstat(int ver, const char *path, struct stat *buf);

int stat(const char *path, struct stat *buf) {
    /* _STAT_VER_LINUX on x86_64 is 1 */
    return __xstat(1, path, buf);
}
