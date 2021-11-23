package test_informal;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Iterator;
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
		
		int nSched=3;
		int nSrv=3;

		// creo il threadpool di serventi
		ExecutorService sched = java.util.concurrent.Executors.newFixedThreadPool(nSched);
		for (int i = 0; i < nSched; i++) {
			sched.submit(new Sched());
		}
		
		// creo il threadpool dello scheduler
		ExecutorService serv = java.util.concurrent.Executors.newFixedThreadPool(nSrv);
		for (int i = 0; i < nSrv; i++) {
			serv.submit(new Srv());
		}
		
		int tick=0;
		try {
			while (tick<100) {
				tick+=1;
				TimeUnit.MILLISECONDS.sleep(500);
				System.out.println(String.format("tSched=%.3f, tSrv=%.3f",Sched.nrq.floatValue()/(0.5*tick),Srv.nrq.floatValue()/(0.5*tick)));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
