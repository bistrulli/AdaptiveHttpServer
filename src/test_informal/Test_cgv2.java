package test_informal;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// creo il threadpool di serventi
		ExecutorService sched = java.util.concurrent.Executors.newFixedThreadPool(1);
		sched.submit(new Runnable() {
			@Override
			public void run() {
				int tid=GetThreadID.get_tid();
				System.out.println("PID dello scheduler:" + tid);
				while (true) {
					try {
						TimeUnit.MILLISECONDS.sleep(1000);
						System.out.println("Sched alive");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
		// creo il threadpool dello scheduler
		ExecutorService serv = java.util.concurrent.Executors.newFixedThreadPool(1);
		serv.submit(new Runnable() {
			@Override
			public void run() {
				int tid=GetThreadID.get_tid();
				System.out.println("PID dell servente:" + tid);
				while (true) {
					try {
						TimeUnit.MILLISECONDS.sleep(1000);
						System.out.println("Srv alive");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
		// ogni servente si aggiunge al gruppo srv
		// ogni scheduler si aggiunge al gruppo sched
		try {
			TimeUnit.SECONDS.sleep(120);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
