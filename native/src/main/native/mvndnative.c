/*******************************************************************************
 * Copyright (C) 2009-2017 the original author(s).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
#include "mvndnative.h"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  return JNI_VERSION_1_2;
}

#define CLibrary_NATIVE(func) Java_org_mvndaemon_mvnd_nativ_CLibrary_##func

#if defined(_WIN32) || defined(_WIN64)

wchar_t* java_to_wchar(JNIEnv* env, jstring string) {
    jsize len = (*env)->GetStringLength(env, string);
    wchar_t* str = (wchar_t*) malloc(sizeof(wchar_t) * (len + 1));
    (*env)->GetStringRegion(env, string, 0, len, (jchar*) str);
    str[len] = L'\0';
    return str;
}

JNIEXPORT jint JNICALL CLibrary_NATIVE(chdir)(JNIEnv *env, jobject thiz, jstring path)
{
	jint rc = 0;
	wchar_t* nativePath = path != NULL ? java_to_wchar(env, path) : NULL;
	rc = (jint) SetCurrentDirectoryW(nativePath);
	if (nativePath) free(nativePath);
	return rc;
}

JNIEXPORT jint JNICALL CLibrary_NATIVE(setenv)(JNIEnv *env, jobject thiz, jstring name, jstring value)
{
	jint rc = 0;
	wchar_t* nativeName = name != NULL ? java_to_wchar(env, name) : NULL;
	wchar_t* nativeValue = value != NULL ? java_to_wchar(env, value) : NULL;
	rc = (jint) SetEnvironmentVariableW(nativeName, nativeValue);
	if (nativeName) free(nativeName);
	if (nativeValue) free(nativeValue);
	return rc;
}

#else

char* java_to_char(JNIEnv* env, jstring string) {
    size_t len = (*env)->GetStringLength(env, string);
    size_t bytes = (*env)->GetStringUTFLength(env, string);
    char* chars = (char*) malloc(bytes + 1);
    (*env)->GetStringUTFRegion(env, string, 0, len, chars);
    chars[bytes] = 0;
    return chars;
}

JNIEXPORT jint JNICALL CLibrary_NATIVE(chdir)(JNIEnv *env, jobject thiz, jstring path)
{
	jint rc = 0;
	char* nativePath = java_to_char(env, path);
	rc = (jint) chdir(nativePath);
	free(nativePath);
    return rc;
}

JNIEXPORT jint JNICALL CLibrary_NATIVE(setenv)(JNIEnv *env, jobject thiz, jstring name, jstring value)
{
	jint rc = 0;
	if (name) {
	    char* nativeName = java_to_char(env, name);
	    if (value) {
        	char* nativeValue = java_to_char(env, value);
	        rc = setenv(nativeName, nativeValue, 1);
	        free(nativeValue);
	    } else {
	        rc = unsetenv(nativeName);
	    }
	    free(nativeName);
	}
	return rc;
}

#endif

#if defined(__APPLE__)
#include <mach/mach.h>
#include <sys/types.h>
#include <sys/sysctl.h>

JNIEXPORT jint JNICALL CLibrary_NATIVE(getOsxMemoryInfo)(JNIEnv *env, jobject thiz, jlongArray totalAndAvailMem)
{
	jint rc = 0;

    // Get total physical memory
    int mib[2];
    mib[0] = CTL_HW;
    mib[1] = HW_MEMSIZE;
    int64_t total_memory = 0;
    size_t len = sizeof(total_memory);
    if ((rc = sysctl(mib, 2, &total_memory, &len, NULL, 0)) != 0) {
        return rc;
    }

    // Get VM stats
    vm_size_t page_size;
    mach_port_t mach_port;
    mach_msg_type_number_t count;
    vm_statistics64_data_t vm_stats;

    mach_port = mach_host_self();
    count = HOST_VM_INFO64_COUNT;
    if ((rc = host_page_size(mach_port, &page_size)) != 0) {
        return rc;
    }
    if ((rc = host_statistics64(mach_port, HOST_VM_INFO, (host_info64_t) &vm_stats, &count)) != 0) {
        return rc;
    }

    // Calculate available memory
    long long available_memory =
        ((int64_t) vm_stats.free_count + (int64_t) vm_stats.inactive_count
                         - (int64_t) vm_stats.speculative_count) * (int64_t) page_size;

    jlong elements[] = { (jlong) total_memory, (jlong) available_memory };
    (*env)->SetLongArrayRegion(env, totalAndAvailMem, 0, 2, elements);

	return rc;
}

#endif