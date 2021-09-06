package test_informal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import redis.clients.jedis.Jedis;

public class testRedis {

	public static class user extends Thread {

		private Jedis jedis = null;
		private boolean toStop = false;

		public user(Jedis jedis) {
			this.jedis = jedis;
		}

		public user() {
			this.jedis = new Jedis("localhost");
		}

		public void run() {
			while (!this.toStop) {
				this.jedis.incr("var");
				this.jedis.decr("var");
			}
		};
	}

	public static class timer extends Thread {

		CountDownLatch latch=new CountDownLatch(1);

		public timer(CountDownLatch latch) {
			this.latch=latch;
		}

		public void run() {
			System.out.println("coutdown");
			this.latch.countDown();
		};
	}

	public static void main(String[] args) {
		// Connecting to Redis server on localhost
		Jedis jedis = new Jedis("localhost");
		System.out.println("Connection to server sucessfully");
		System.out.println("Connection to server sucessfully");
		// set the data in redis string
		jedis.set("var", String.valueOf(0));
		int nt = 2;
		user[] users = new user[nt];
		for (int i = 0; i < users.length; i++) {
			users[i] = new user();
			users[i].start();
		}
		CountDownLatch latch=new CountDownLatch(1);
		ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
		exec.schedule(new timer(latch), 5, TimeUnit.SECONDS);
		try {
			
			latch.await();
			exec.shutdown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		for (int i = 0; i < users.length; i++) {
			users[i].toStop=true;
			users[i].interrupt();
			try {
				users[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Stored string in redis:: "+ jedis.get("var"));
	}
}
