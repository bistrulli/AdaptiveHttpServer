package app;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import Server.SimpleTask;
import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import two_tier_sys.Tier1HTTPHandler;

public class two_tier_app {

	static String[] queues = new String[] { "think", "e1_bl", "e1_ex"};
	private static int initPop = -1;
	private static Boolean isEmu = false;

	public static void main(String[] args) {

		two_tier_app.getCliOptions(args);

		Jedis jedis = new Jedis("localhost");

		two_tier_app.resetState(jedis, two_tier_app.initPop);
		jedis.close();

		SimpleTask[] Sys = two_tier_app.genSystem(two_tier_app.initPop);
		two_tier_app.startSystem(Sys);
	}

	public static void resetState(Jedis jedis, int initDriver) {
		Transaction a = jedis.multi();
		for (int i = 0; i < two_tier_app.queues.length; i++) {
			if (i == 0)
				a.set(two_tier_app.queues[i], String.valueOf(initDriver));
			else
				a.set(two_tier_app.queues[i], String.valueOf(0));
		}
		a.exec();
		a.close();
	}

	public static SimpleTask[] genSystem(int initDriver) {
		// instantiate client task
		HashMap<String, Class> clientEntries = new HashMap<>();
		HashMap<String, Long> clientEntries_stimes = new HashMap<>();
		clientEntries.put("think", two_tier_sys.Client.class);
		clientEntries_stimes.put("think", 1000l);
		final SimpleTask client = new SimpleTask(clientEntries, clientEntries_stimes, initDriver, "Client");

		// instatiate tier1 class
		HashMap<String, Class> t1Entries = new HashMap<>();
		HashMap<String, Long> t1Entries_stimes = new HashMap<>();
		t1Entries.put("e1", Tier1HTTPHandler.class);
		t1Entries_stimes.put("e1", 100l);

		final SimpleTask t1 = new SimpleTask("localhost", 3000, t1Entries, t1Entries_stimes,initDriver,two_tier_app.isEmu, "t1");
		t1.setHwCore(1f);
		return new SimpleTask[] { t1, client };
	}

	public static void startSystem(SimpleTask[] tasks) {
		for (SimpleTask simpleTask : tasks) {
			new Thread(new Runnable() {
				public void run() {
					simpleTask.start();
				}
			}).start();
			try {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}
	}

	public static void stopSystem(SimpleTask[] tasks) {
		for (int i = tasks.length - 1; i >= 0; i--) {
			System.out.println("stopping " + tasks[i].getName());
			tasks[i].stop();
		}
	}

	public static void getCliOptions(String[] args) {

		int c;
		LongOpt[] longopts = new LongOpt[2];
		longopts[0] = new LongOpt("initPop", LongOpt.REQUIRED_ARGUMENT, null, 0);
		longopts[1] = new LongOpt("cpuEmu", LongOpt.REQUIRED_ARGUMENT, null, 1);
		

		Getopt g = new Getopt("ddctrl", args, "", longopts);
		g.setOpterr(true);
		while ((c = g.getopt()) != -1) {
			switch (c) {
			case 0:
				two_tier_app.initPop = Integer.valueOf(g.getOptarg());
				break;
			case 1:
				try {
					two_tier_app.isEmu =Integer.valueOf(g.getOptarg())>0?true: false;
				}catch (NumberFormatException e) {
					System.err.println(String.format("%s is not valid, it must be 0 or 1.", g.getOptarg()));
				}
				break;
			default:
				break;
			}
		}
	}
}
