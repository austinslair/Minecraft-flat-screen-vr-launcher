/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>
#include <string.h>

#include "log.h"
#include "utils.h"

// Uncomment to try redirect signal handling to JVM
#define TRY_SIG2JVM

#define FULL_VERSION "1.8.0-internal"
#define DOT_VERSION "1.8"

static const char* const_progname = "java";
static const char* const_launcher = "openjdk";
static const char** const_jargs = NULL;
static const char** const_appclasspath = NULL;
static const jboolean const_javaw = JNI_FALSE;
static const jboolean const_cpwildcard = JNI_TRUE;
static const jint const_ergo_class = 0; // DEFAULT_POLICY
static struct sigaction old_sa[NSIG];

void (*__old_sa)(int signal, siginfo_t *info, void *reserved);
int (*JVM_handle_linux_signal)(int signo, siginfo_t* siginfo, void* ucontext, int abort_if_unrecognized);

/*
 * Java output capture runs on another thread and can lose the final pipe contents if
 * JLI_Launch calls exit() or the process is killed during VM creation. Persist a tiny
 * breadcrumb directly into latestlog.txt before each native transition instead.
 */
static void voxyquest_breadcrumb(const char *message) {
    const char *path = getenv("VOXYQUEST_LAUNCH_LOG");
    if (path == NULL || path[0] == '\0') return;

    FILE *file = fopen(path, "a");
    if (file == NULL) return;
    fprintf(file, "%s\n", message);
    fflush(file);
    fsync(fileno(file));
    fclose(file);
}

void android_sigaction(int signum, siginfo_t *info, void *reserved) {
  if (JVM_handle_linux_signal == NULL) {
      /* Do not dereference sa_sigaction when the previous disposition is SIG_DFL,
       * SIG_IGN, or a one-argument handler. The old implementation could turn an
       * otherwise diagnosable JVM startup signal into an immediate native crash. */
      struct sigaction *previous = &old_sa[signum];
      if (previous->sa_handler == SIG_IGN) {
          return;
      }
      if (previous->sa_handler == SIG_DFL) {
          signal(signum, SIG_DFL);
          raise(signum);
          return;
      }
      if ((previous->sa_flags & SA_SIGINFO) != 0 && previous->sa_sigaction != NULL) {
          previous->sa_sigaction(signum, info, reserved);
      } else if (previous->sa_handler != NULL) {
          previous->sa_handler(signum);
      }
  } else {
      // Based on https://github.com/PojavLauncherTeam/openjdk-multiarch-jdk8u/blob/aarch64-shenandoah-jdk8u272-b10/hotspot/src/os/linux/vm/os_linux.cpp#L4688-4693
      int orig_errno = errno;  // Preserve errno value over signal handler.
      JVM_handle_linux_signal(signum, info, reserved, true);
      errno = orig_errno;
  }
}
typedef jint JNI_CreateJavaVM_func(JavaVM **pvm, void **penv, void *args);

typedef jint JLI_Launch_func(int argc, char ** argv, /* main argc, argc */
        int jargc, const char** jargv,          /* java args */
        int appclassc, const char** appclassv,  /* app classpath */
        const char* fullversion,                /* full version defined */
        const char* dotversion,                 /* dot version defined */
        const char* pname,                      /* program name */
        const char* lname,                      /* launcher name */
        jboolean javaargs,                      /* JAVA_ARGS */
        jboolean cpwildcard,                    /* classpath wildcard*/
        jboolean javaw,                         /* windows-only javaw */
        jint ergo                               /* ergonomics class policy */
);

static jint launchJVM(int margc, char** margv) {
   voxyquest_breadcrumb("VoxyQuest native JVM: opening libjli.so");
   void* libjli = dlopen("libjli.so", RTLD_LAZY | RTLD_GLOBAL);

   // Boardwalk: silence
   // LOGD("JLI lib = %x", (int)libjli);
   if (NULL == libjli) {
       voxyquest_breadcrumb("VoxyQuest native JVM: libjli.so open failed");
       LOGE("JLI lib = NULL: %s", dlerror());
       return -1;
   }
   voxyquest_breadcrumb("VoxyQuest native JVM: libjli.so ready");
   LOGD("Found JLI lib");

   JLI_Launch_func *pJLI_Launch =
          (JLI_Launch_func *)dlsym(libjli, "JLI_Launch");
    // Boardwalk: silence
    // LOGD("JLI_Launch = 0x%x", *(int*)&pJLI_Launch);

   if (NULL == pJLI_Launch) {
       voxyquest_breadcrumb("VoxyQuest native JVM: JLI_Launch symbol missing");
       LOGE("JLI_Launch = NULL");
       return -1;
   }

   voxyquest_breadcrumb("VoxyQuest native JVM: calling JLI_Launch");
   LOGD("Calling JLI_Launch");

   jint result = pJLI_Launch(margc, margv,
                   0, NULL, // sizeof(const_jargs) / sizeof(char *), const_jargs,
                   0, NULL, // sizeof(const_appclasspath) / sizeof(char *), const_appclasspath,
                   FULL_VERSION,
                   DOT_VERSION,
                   *margv, // (const_progname != NULL) ? const_progname : *margv,
                   *margv, // (const_launcher != NULL) ? const_launcher : *margv,
                   (const_jargs != NULL) ? JNI_TRUE : JNI_FALSE,
                   const_cpwildcard, const_javaw, const_ergo_class);
   voxyquest_breadcrumb("VoxyQuest native JVM: JLI_Launch returned");
   return result;
}

/*
 * Class:     com_oracle_dalvik_VMLauncher
 * Method:    launchJVM
 * Signature: ([Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_oracle_dalvik_VMLauncher_launchJVM(JNIEnv *env, jclass clazz, jobjectArray argsArray) {
  voxyquest_breadcrumb("VoxyQuest native JVM: JNI launcher entered");
#ifdef TRY_SIG2JVM
  voxyquest_breadcrumb("VoxyQuest native JVM: opening libjvm.so for signal bridge");
  void* libjvm = dlopen("libjvm.so", RTLD_LAZY | RTLD_GLOBAL);
  if (NULL == libjvm) {
      voxyquest_breadcrumb("VoxyQuest native JVM: libjvm.so signal bridge open failed");
      LOGE("JVM lib = NULL: %s", dlerror());
      return -1;
  }
  JVM_handle_linux_signal = dlsym(libjvm, "JVM_handle_linux_signal");
  if (JVM_handle_linux_signal == NULL) {
      voxyquest_breadcrumb("VoxyQuest native JVM: JVM signal bridge symbol unavailable");
  } else {
      voxyquest_breadcrumb("VoxyQuest native JVM: JVM signal bridge ready");
  }
#endif

   jint res = 0;
   // int i;
   //Prepare the signal trapper
   voxyquest_breadcrumb("VoxyQuest native JVM: installing signal handlers");
   struct sigaction catcher;
   memset(&catcher,0,sizeof(struct sigaction));
   catcher.sa_sigaction = android_sigaction;
   catcher.sa_flags = SA_SIGINFO|SA_RESTART;
   // SA_RESETHAND;
#define CATCHSIG(X) sigaction(X, &catcher, &old_sa[X])
    CATCHSIG(SIGILL);
    CATCHSIG(SIGABRT);
    CATCHSIG(SIGBUS);
    CATCHSIG(SIGFPE);
#ifdef TRY_SIG2JVM
    CATCHSIG(SIGSEGV);
#endif
    CATCHSIG(SIGSTKFLT);
    CATCHSIG(SIGPIPE);
    CATCHSIG(SIGXFSZ);
   //Signal trapper ready
   voxyquest_breadcrumb("VoxyQuest native JVM: signal handlers ready");

    // Save dalvik JNIEnv pointer for JVM launch thread
    dalvikJNIEnvPtr_ANDROID = env;

    if (argsArray == NULL) {
        voxyquest_breadcrumb("VoxyQuest native JVM: argument array was null");
        LOGE("Args array null, returning");
        //handle error
        return 0;
    }

    int argc = (*env)->GetArrayLength(env, argsArray);
    voxyquest_breadcrumb("VoxyQuest native JVM: converting Java arguments");
    char **argv = convert_to_char_array(env, argsArray);
    voxyquest_breadcrumb("VoxyQuest native JVM: Java arguments ready");

    LOGD("Done processing args");

    voxyquest_breadcrumb("VoxyQuest native JVM: entering JLI wrapper");
    res = launchJVM(argc, argv);
    voxyquest_breadcrumb("VoxyQuest native JVM: JLI wrapper returned");

    LOGD("Going to free args");
    free_char_array(env, argsArray, argv);

    LOGD("Free done");
    voxyquest_breadcrumb("VoxyQuest native JVM: JNI launcher returning");

    return res;
}
