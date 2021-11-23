package test_informal;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import jni.GetThreadID;

public class Test_cgv2 {

	public static void main(String[] args) {
		int tid = GetThreadID.get_tid();

		System.out.println("PID del main process:" + tid);

		// aggiungo questo processo al gruppo principale del tier
		BufferedWriter out;
		try {
			out = new BufferedWriter(new FileWriter("/sys/fs/cgroup/tier1/cgroup.procs", true));
			out.write(String.valueOf(tid));
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// creo il threadpool di serventi
		ExecutorService sched = java.util.concurrent.Executors.newFixedThreadPool(1);
		Sched s = new Sched();
		sched.submit(s);
		// creo il threadpool dello scheduler
		ExecutorService serv = java.util.concurrent.Executors.newFixedThreadPool(1);
		Srv s1 = new Srv();
		serv.submit(s1);
		
		int tick=0;
		try {
			while (tick<100) {
				TimeUnit.MILLISECONDS.sleep(500);
				System.out.println(String.format("tSched=%d, tSrv=%d",s.nrq.floatValue()/(0.5*tick),s1.nrq.floatValue()/(0.5*tick)));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
