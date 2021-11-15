package monitoring;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import Server.SimpleTask;
import net.spy.memcached.MemcachedClient;

public class StateSampler implements Runnable {
	
	private MemcachedClient memClient = null;
	private SimpleTask task=null;
	
	public StateSampler(String monitorHost,SimpleTask task) {
		this.task=task;
		try {
			this.memClient=new MemcachedClient(new InetSocketAddress(monitorHost, 11211));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void sampleState() {
		HashMap<String, AtomicInteger> state = this.task.getState();
		for (String key: state.keySet()) {
			AtomicInteger st=((AtomicInteger) state.get(key));
			try {
				this.memClient.set(key, 3600, String.valueOf(st.get())).get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {
		this.sampleState();
	}

}
