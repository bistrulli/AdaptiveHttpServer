package jni;

import java.io.IOException;

public class GetThreadID {
	// Declare the native method
	public static native int get_tid();
	public static native void setAffinity(int pid,int scpu, int ecpu);
	
	static {
		try {
			NativeUtils.loadLibraryFromJar("/libGetThreadID.so");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}