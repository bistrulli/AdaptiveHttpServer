#!/bin/bash

gcc -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -fPIC -shared jni_GetThread.c -o libGetThreadID.so
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$(pwd)