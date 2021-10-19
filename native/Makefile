# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

include Makefile.common

.phony: all package native native-all deploy

all: package

MVNDNATIVE_OUT:=target/native-$(OS_NAME)-$(OS_ARCH)

CCFLAGS:= -I$(MVNDNATIVE_OUT) $(CCFLAGS)

download-includes:
	test -d target/inc || mkdir target/inc
	test -d target/inc/unix || mkdir target/inc/unix
	test -d target/inc/windows || mkdir target/inc/windows
	test -f target/inc/jni.h || wget -O target/inc/jni.h https://raw.githubusercontent.com/openjdk/jdk/jdk-11%2B28/src/java.base/share/native/include/jni.h
	test -f target/inc/unix/jni_md.h || wget -O target/inc/unix/jni_md.h https://raw.githubusercontent.com/openjdk/jdk/jdk-11%2B28/src/java.base/unix/native/include/jni_md.h
	test -f target/inc/windows/jni_md.h || wget -O target/inc/windows/jni_md.h https://raw.githubusercontent.com/openjdk/jdk/jdk-11%2B28/src/java.base/windows/native/include/jni_md.h

clean-native:
	rm -rf $(MVNDNATIVE_OUT)

$(MVNDNATIVE_OUT)/%.o: src/main/native/%.c
	@mkdir -p $(@D)
	$(info running: $(CC) $(CCFLAGS) -c $< -o $@)
	$(CC) $(CCFLAGS) -c $< -o $@

$(MVNDNATIVE_OUT)/$(LIBNAME): $(MVNDNATIVE_OUT)/mvndnative.o
	@mkdir -p $(@D)
	$(CC) $(CCFLAGS) -o $@ $(MVNDNATIVE_OUT)/mvndnative.o $(LINKFLAGS)

NATIVE_DIR=src/main/resources/org/mvndaemon/mvnd/nativ/$(OS_NAME)/$(OS_ARCH)
NATIVE_TARGET_DIR:=target/classes/org/mvndaemon/mvnd/nativ/$(OS_NAME)/$(OS_ARCH)
NATIVE_DLL:=$(NATIVE_DIR)/$(LIBNAME)

# For cross-compilation, install docker. See also https://github.com/dockcross/dockcross
# Disabled linux-armv6 build because of this issue; https://github.com/dockcross/dockcross/issues/190
native-all: linux-x86 linux-x86_64 linux-arm linux-armv7 \
	linux-arm64 linux-ppc64 win-x86 win-x86_64 mac-x86 mac-x86_64 mac-arm64 freebsd-x86 freebsd-x86_64

native: $(NATIVE_DLL)

$(NATIVE_DLL): $(MVNDNATIVE_OUT)/$(LIBNAME)
	@mkdir -p $(@D)
	cp $< $@
	@mkdir -p $(NATIVE_TARGET_DIR)
	cp $< $(NATIVE_TARGET_DIR)/$(LIBNAME)

linux-x86: download-includes
	./docker/dockcross-linux-x86 bash -c 'make clean-native native OS_NAME=Linux OS_ARCH=x86'

linux-x86_64: download-includes
	docker run -it --rm -v $$PWD:/workdir -e CROSS_TRIPLE=x86_64-linux-gnu multiarch/crossbuild make clean-native native OS_NAME=Linux OS_ARCH=x86_64

linux-arm: download-includes
	docker run -it --rm -v $$PWD:/workdir -e CROSS_TRIPLE=arm-linux-gnueabi multiarch/crossbuild make clean-native native OS_NAME=Linux OS_ARCH=arm

linux-armv7: download-includes
	docker run -it --rm -v $$PWD:/workdir -e CROSS_TRIPLE=arm-linux-gnueabihf multiarch/crossbuild make clean-native native OS_NAME=Linux OS_ARCH=armv7

linux-arm64: download-includes
	docker run -it --rm -v $$PWD:/workdir -e CROSS_TRIPLE=aarch64-linux-gnu multiarch/crossbuild make clean-native native OS_NAME=Linux OS_ARCH=arm64

linux-ppc64: download-includes
	docker run -it --rm -v $$PWD:/workdir -e CROSS_TRIPLE=powerpc64le-linux-gnu multiarch/crossbuild make clean-native native OS_NAME=Linux OS_ARCH=ppc64

win-x86: download-includes
	./docker/dockcross-windows-static-x86 bash -c 'make clean-native native CROSS_PREFIX=i686-w64-mingw32.static- OS_NAME=Windows OS_ARCH=x86'

win-x86_64: download-includes
	./docker/dockcross-windows-static-x64 bash -c 'make clean-native native CROSS_PREFIX=x86_64-w64-mingw32.static- OS_NAME=Windows OS_ARCH=x86_64'

mac-x86: download-includes
	docker run -it --rm -v $$PWD:/workdir -e CROSS_TRIPLE=i386-apple-darwin multiarch/crossbuild make clean-native native OS_NAME=Mac OS_ARCH=x86

mac-x86_64: download-includes
	docker run -it --rm -v $$PWD:/workdir -e CROSS_TRIPLE=x86_64-apple-darwin multiarch/crossbuild make clean-native native OS_NAME=Mac OS_ARCH=x86_64

mac-arm64: download-includes
	docker run -it --rm -v $$PWD:/src -e TARGET=arm64-apple-darwin mcandre/snek:darwin sh -c "make clean-native native CROSS_PREFIX=arm64-apple-darwin20.4- OS_NAME=Mac OS_ARCH=arm64"

freebsd-x86: download-includes
	docker run -it --rm -v $$PWD:/workdir empterdose/freebsd-cross-build:9.3 make clean-native native CROSS_PREFIX=i386-freebsd9- OS_NAME=FreeBSD OS_ARCH=x86

freebsd-x86_64: download-includes
	docker run -it --rm -v $$PWD:/workdir empterdose/freebsd-cross-build:9.3 make clean-native native CROSS_PREFIX=x86_64-freebsd9- OS_NAME=FreeBSD OS_ARCH=x86_64

#sparcv9:
#	$(MAKE) native OS_NAME=SunOS OS_ARCH=sparcv9



