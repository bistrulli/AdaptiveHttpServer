#!/bin/bash

gcc -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -fPIC -shared jni_GetThreadID.c -o libGetThreadID.so
LD_LIBRARY_PATH=$(pwd)
export LD_LIBRARY_PATH