package monitoring;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.ArrayUtils;

import net.spy.memcached.MemcachedClient;

public class rtSampler implements Runnable {

	private ConcurrentLinkedQueue<Long> rt = null;
	private MemcachedClient memcachedClient = null;
	private String monirotHost = null;
	private String name=null;

	public rtSampler(String monirotHost,String name) {
		this.rt = new ConcurrentLinkedQueue<Long>();
		this.monirotHost = monirotHost;
		this.name=name;
		try {
			this.memcachedClient = new MemcachedClient(new InetSocketAddress(this.monirotHost, 11211));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		Long[] samples = this.rt.toArray(new Long[0]);
		this.saveRT(samples);
		this.rt.clear();
	}

	private void saveRT(Long[] samples) {
		double sum = 0;
		for (Long sample : samples) {
			sum += sample;
		}
		try {
			this.memcachedClient.set("rt_"+this.name, 3600, String.valueOf(sum / samples.length)).get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
	}

	public void addSample(long time) {
		this.rt.add(time);
	}

}
