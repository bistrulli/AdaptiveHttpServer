package test_informal;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import jni.GetThreadID;

public class Srv implements Runnable {
	public Integer nrq = 0;
	public ThreadMXBean mgm = null;

	public Srv() {
		this.nrq = 0;
		this.mgm = ManagementFactory.getThreadMXBean();
	}

	private void doWorkCPU(Double stime, ThreadMXBean mgm) {
		long delay = Long.valueOf(Math.round(stime * 1000000));
		long start = mgm.getCurrentThreadCpuTime();
		while ((mgm.getCurrentThreadCpuTime() - start) < delay) {
		}
	}

	@Override
	public void run() {
		int tid = GetThreadID.get_tid();
		System.out.println("PID dell servente:" + tid);
		// aggiungo questo thread gruppo dei serventi
		BufferedWriter out;
		try {
			out = new BufferedWriter(new FileWriter("/sys/fs/cgroup/tier1/srv/cgroup.threads", true));
			out.write(String.valueOf(tid));
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		while (true) {
			ThreadMXBean mgm = ManagementFactory.getThreadMXBean();
			while (true) {
				this.doWorkCPU(100d, this.mgm);
				this.nrq+=1;
			}
		}
	}
}
