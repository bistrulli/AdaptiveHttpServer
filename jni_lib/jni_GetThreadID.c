#define _GNU_SOURCE
#include <jni.h>
#include <syscall.h>
#include <stdio.h>
#include <sched.h>   //cpu_set_t , CPU_SET
#include "jni_GetThreadID.h"

JNIEXPORT jint JNICALL
Java_jni_GetThreadID_get_1tid(JNIEnv *env, jobject obj) {
	// Here to initiate a system call, read the pid in the task_struct structure above, the estimated implementation is a line: return current-> pid / / current is a pointer to the current thread (task_struct);
	// I feel that the process of system call must be studied well.
	// __NR_gettid: The system call number macro definition, look at the system file /usr/include/asm/unistd_64.h file to know that it is 186, the following code is equivalent to: syscall (186)
    jint tid = syscall(__NR_gettid);
    return tid;
}

JNIEXPORT void JNICALL
Java_jni_GetThreadID_setAffinity(JNIEnv *env, jobject obj, jint pid,jint scpu,jint ecpu) {
	
	//we can set one or more bits here, each one representing a single CPU
	cpu_set_t cpuset; 
	
	CPU_ZERO(&cpuset);       //clears the cpuset
	CPU_SET( scpu , &cpuset); //set CPU start on cpuset
	CPU_SET( ecpu , &cpuset); //set CPU end on cpuset
	
	/*
	* cpu affinity for the calling thread 
	* first parameter is the pid, 0 = calling thread
	* second parameter is the size of your cpuset
	* third param is the cpuset in which your thread will be
	* placed. Each bit represents a CPU
	*/
	sched_setaffinity(pid, sizeof(cpuset), &cpuset);
	
}