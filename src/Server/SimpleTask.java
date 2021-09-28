package Server;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import adaptationHandler.AdaptationHandler2;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@SuppressWarnings("restriction")
public class SimpleTask {

	private Integer port = null;
	private HttpServer server = null;
	private ThreadPoolExecutor threadpool = null;
	private Integer threadpoolSize = 1;
	private Float hwCore = 1.0f;
	private int maxThreadSize = 1000;
	private boolean isEmulated = true;

	private Integer backlogsize = 2000;

	public ArrayBlockingQueue<HttpExchange> backlog = null;
	private AdaptationHandler2 adaptHandler = null;
	private HashMap<String, Class> entries = null;
	private HashMap<String, Long> sTimes = null;
	private JedisPool jedisPool;
	private boolean isGenerator = false;
	private static Logger logger = LoggerFactory.getLogger(SimpleTask.class);
	
	private String jedisHost=null;

	String name = null;

	ConcurrentLinkedQueue<Integer> tids = null;
	
	private HashMap<String, Long> enqueueTime=null;

	public SimpleTask(String address, int port, HashMap<String, Class> entries, HashMap<String, Long> sTimes, int tsize,
			boolean isEmulated, String name,String jedisHost) {
		this.setEnqueueTime(new HashMap<String, Long>());
		this.setName(name);
		this.threadpoolSize = tsize;
		this.entries = entries;
		this.setEmulated(isEmulated);
		this.jedisHost=jedisHost;
		try {
			this.server = HttpServer.create(new InetSocketAddress(port), this.backlogsize);
			this.setPort(port);
			this.server.createContext("/",new AcquireHttpHandler(this));
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.initThreadPoolExecutor();
		this.initJedisPool();
		this.tids = new ConcurrentLinkedQueue<Integer>();
		this.sTimes = sTimes;
		// if(this.isEmulated)
		this.adaptHandler = new AdaptationHandler2(this,this.jedisHost);
	}

	public SimpleTask(HashMap<String, Class> entries, HashMap<String, Long> sTimes, int tsize, String name,String jedisHost) {
		this.setEnqueueTime(new HashMap<String, Long>());
		this.setName(name);
		this.jedisHost=jedisHost;
		this.isGenerator = true;
		this.threadpoolSize = tsize;
		this.entries = entries;
		this.sTimes = sTimes;
		this.initThreadPoolExecutor();
		this.threadpool.allowCoreThreadTimeOut(true);
		this.initJedisPool();
		this.jedisHost=jedisHost;
	}

	public void setThreadPoolSize(int size) throws Exception {
		if (size <= 0) {
			throw new Exception("Threadpool size has to be >0");
		}
		if (size <= this.maxThreadSize) {
			// this.threadpool.setPoolSize(size, this);
			// this.threadpool.setMaximumPoolSize(size);
			this.threadpool.setCorePoolSize(size);
		} else {
			throw new Exception("Maximum threadpool size reached");
		}
	}

	public void start() {
		if (!this.isGenerator && this.adaptHandler != null)
			this.getAdaptHandler().start();
		if (!this.isGenerator && this.getServer() != null)
			this.getServer().start();
		if (this.isGenerator) {
			// only in case of workload generator, I assume that there is only one cliaent
			// type for each client task
			for (int i = 0; i < this.threadpoolSize; i++) {
				Constructor<? extends Runnable> c;
				try {
					Iterator<Map.Entry<String, Class>> iter = this.entries.entrySet().iterator();
					c = iter.next().getValue().getDeclaredConstructor(SimpleTask.class, Long.class);
					this.threadpool.submit(
							c.newInstance(this, this.sTimes.get(this.entries.entrySet().iterator().next().getKey())));
				} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
						| IllegalArgumentException | InvocationTargetException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public HttpServer getServer() {
		return server;
	}

	public void setServer(HttpServer server) {
		this.server = server;
	}

	public ThreadPoolExecutor getThreadpool() {
		return threadpool;
	}

	public void setThreadpool(ThreadPoolExecutor threadpool) {
		this.threadpool = threadpool;
	}

	public Integer getThreadpoolSize() {
		return threadpoolSize;
	}

	public int getMaxThreadSize() {
		return maxThreadSize;
	}

	public void setMaxThreadSize(int maxThreadSize) {
		this.maxThreadSize = maxThreadSize;
	}

	public Float getHwCore() {
		return hwCore;
	}

	public void setHwCore(Float hwCore) {
		this.hwCore = hwCore;
	}

	public ArrayBlockingQueue<HttpExchange> getBacklog() {
		return backlog;
	}

	public void setBacklog(ArrayBlockingQueue<HttpExchange> backlog) {
		this.backlog = backlog;
	}

	public JedisPool getJedisPool() {
		return jedisPool;
	}

	public void setJedisPool(JedisPool jedisPool) {
		this.jedisPool = jedisPool;
	}

	public static Double sampleExp(double rate, ThreadLocalRandom rnd) {
		return Double.valueOf((-1.0 / rate) * Math.log(1 - rnd.nextDouble()));
	}

	public AdaptationHandler2 getAdaptHandler() {
		return adaptHandler;
	}

	public void setAdaptHandler(AdaptationHandler2 adaptHandler) {
		this.adaptHandler = adaptHandler;
	}

	public void stop() {
		if (this.server != null)
			this.server.stop(2);
		if (this.getAdaptHandler() != null) {
			this.adaptHandler.interrupt();
		}
		this.threadpool.shutdownNow();
		try {
			this.threadpool.awaitTermination(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public ConcurrentLinkedQueue<Integer> getTids() {
		return tids;
	}

	public void setTids(ConcurrentLinkedQueue<Integer> tids) {
		this.tids = tids;
	}

	public HashMap<String, Class> getEntries() {
		return entries;
	}

	public void setEntries(HashMap<String, Class> entries) {
		this.entries = entries;
	}

	public HashMap<String, Long> getsTimes() {
		return sTimes;
	}

	public void setsTimes(HashMap<String, Long> sTimes) {
		this.sTimes = sTimes;
	}

	public void initJedisPool(int poolSize, String poolAddr) {
		JedisPoolConfig cfg = new JedisPoolConfig();
		cfg.setMaxTotal(poolSize);
		this.jedisPool = new JedisPool(cfg, poolAddr);
	}

	public void initJedisPool() {
		this.initJedisPool(2000, this.jedisHost);
	}

	public void initThreadPoolExecutor() {
		this.threadpool = new ThreadPoolExecutor(this.threadpoolSize, Integer.MAX_VALUE, 1,
				TimeUnit.NANOSECONDS, new LinkedBlockingQueue<Runnable>());
		this.threadpool.allowCoreThreadTimeOut(true);
		if (!this.isGenerator)
			this.server.setExecutor(null);
	}

	public boolean isEmulated() {
		return isEmulated;
	}

	public void setEmulated(boolean isEmulated) {
		this.isEmulated = isEmulated;
	}

	public static Logger getLogger() {
		return logger;
	}

	public static void setLogger(Logger logger) {
		SimpleTask.logger = logger;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public HashMap<String, Long> getEnqueueTime() {
		return enqueueTime;
	}

	public void setEnqueueTime(HashMap<String, Long> enqueueTime) {
		this.enqueueTime = enqueueTime;
	}
	
	public Map<String, String> queryToMap(String query) {
		Map<String, String> result = new HashMap<>();
		for (String param : query.split("&")) {
			String[] entry = param.split("=");
			if (entry.length > 1) {
				result.put(entry[0], entry[1]);
			} else {
				result.put(entry[0], "");
			}
		}
		return result;
	}

}
