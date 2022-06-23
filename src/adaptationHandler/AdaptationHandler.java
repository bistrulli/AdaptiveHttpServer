package adaptationHandler;

import java.io.IOException;
import java.net.InetSocketAddress;

import Server.SimpleTask;
import net.spy.memcached.MemcachedClient;

public class AdaptationHandler implements Runnable {

	private SimpleTask task = null;
	private MemcachedClient memcachedClient = null;

	public AdaptationHandler(SimpleTask task, String jedisHost) {
		this.task = task;
		try {
			this.memcachedClient = new MemcachedClient(new InetSocketAddress(jedisHost, 11211));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		try {
			Integer swCore =null;
			Float hwCore = null;
			Object core=null;
			if((core=memcachedClient.get(this.task.getName() + "_sw"))!=null)
				swCore = Integer.valueOf(String.valueOf(core));
			if((core=memcachedClient.get(this.task.getName() + "_hw"))!=null)
				hwCore = Float.valueOf(String.valueOf(core));
			if (hwCore != null) {
				//int swcore = Math.max(1, Double.valueOf(Math.ceil(hwCore)).intValue());
				int swcore=swCore;
				System.out.println(this.task.getName()+"-HW:"+hwCore+"-SW:"+swcore);
				try {
					this.task.setThreadPoolSize(swcore);
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
					System.out.println(String.format("%s-%s", String.valueOf(swcore), String.valueOf(hwCore)));
				}
				this.task.setHwCore(hwCore);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public SimpleTask getTask() {
		return task;
	}

	public void setTask(SimpleTask task) {
		this.task = task;
	}

}
