package adaptationHandler;

import java.util.List;
import java.util.concurrent.TimeUnit;

import Server.SimpleTask;
import redis.clients.jedis.Jedis;

public class AdaptationHandler2 extends Thread {

	private SimpleTask task = null;
	private Jedis jedis = null;

	public AdaptationHandler2(SimpleTask task,String jedisHost) {
		this.task = task;
		this.jedis = new Jedis(jedisHost);
	}

	@Override
	public void run() {
		super.run();
		try {
			while (!this.isInterrupted()) {
				List<String> u = this.jedis.mget(new String[] { this.task.getName()+"_sw", this.task.getName()+"_hw" });

				Integer swCore = null;
				Float hwCore = null;

				if (u.get(0) != null)
					swCore = Integer.valueOf(u.get(0));
				if (u.get(1) != null)
					hwCore = Float.valueOf(u.get(1));

				try {
//					if (swCore != null)
//						this.task.setThreadPoolSize(swCore);
					if (hwCore != null) {
						int swcore=Math.max(1, Double.valueOf(Math.ceil(hwCore)).intValue()); 
						try {
							this.task.setThreadPoolSize(swcore);
						}catch (IllegalArgumentException e) {
							e.printStackTrace();
							System.out.println(String.format("%s-%s",String.valueOf(swcore),String.valueOf(hwCore)));
						}
						this.task.setHwCore(hwCore);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				TimeUnit.MILLISECONDS.sleep(10L);
			}
		} catch (InterruptedException e) {
		}
	}

	public SimpleTask getTask() {
		return task;
	}

	public void setTask(SimpleTask task) {
		this.task = task;
	}

}
