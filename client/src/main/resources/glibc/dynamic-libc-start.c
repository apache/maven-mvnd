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

/* ref:
 *   https://elixir.bootlin.com/glibc/glibc-2.37.9000/source/csu/libc-start.c
 *   https://elixir.bootlin.com/glibc/glibc-2.33.9000/source/csu/elf-init.c#L68
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdint.h>

__asm__(".symver dlsym,dlsym@GLIBC_2.2.5");
__asm__(".symver dlvsym,dlvsym@GLIBC_2.2.5");

/* These magic symbols are provided by the linker.  */
extern void (*__init_array_start[])(int, char **, char **) __attribute__ ((visibility ("hidden")));
extern void (*__init_array_end[])(int, char **, char **) __attribute__ ((visibility ("hidden")));
extern void _init(void);

/* These functions are passed to __libc_start_main by the startup code.
   These get statically linked into each program. */

static void
__libc_csu_init(const int argc, char **const argv, char **const envp)
{
  _init();
  const __auto_type size = __init_array_end - __init_array_start;
  for (__auto_type i = 0; i < size; ++i)
    (*__init_array_start[i])(argc, argv, envp);
}

int
__dynamic_libc_start_main(int (*const main)(int, char **, char **),
                          const int argc,
                          char ** const argv,
                          __typeof(&__libc_csu_init) init,
                          void (*const fini)(void),
                          void (*const rtld_fini)(void),
                          void (*const stack_end))
{
  _Static_assert(sizeof(uintptr_t) >= sizeof(void*), "uintptr_t should contain a object pointer");
  _Static_assert(sizeof(uintptr_t) <= sizeof(&__dynamic_libc_start_main), "function pointer should contain an uintptr_t");

  const __auto_type __libc_start_main = (__typeof(&__dynamic_libc_start_main))(uintptr_t)dlsym(RTLD_DEFAULT, "__libc_start_main");
  if (!dlvsym(RTLD_DEFAULT, "__libc_start_main", "GLIBC_2.34")) {
    init = &__libc_csu_init; // old runtime glibc, ver < 2.34
  }

  return __libc_start_main(main, argc, argv, init, fini, rtld_fini, stack_end);
}
