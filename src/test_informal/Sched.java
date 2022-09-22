package test_informal;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicInteger; 

import jni.GetThreadID;

public class Sched implements Runnable {
	public static AtomicInteger nrq = new AtomicInteger(0);
	public ThreadMXBean mgm = null;

	public Sched() {
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
		System.out.println("PID dello scheduler:" + tid);
		BufferedWriter out;
		try {
			out = new BufferedWriter(new FileWriter("/sys/fs/cgroup/N1/sched/cgroup.threads", true));
			out.write(String.valueOf(tid));
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		while (true) {
			this.doWorkCPU(1000d, this.mgm);
			Sched.nrq.incrementAndGet();
		}
	}
}