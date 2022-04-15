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
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import adaptationHandler.AdaptationHandler;
import monitoring.StateSampler;
import monitoring.rtSampler;

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

	private static AtomicBoolean toStopGracefully = new AtomicBoolean(false);

	public ArrayBlockingQueue<HttpExchange> backlog = null;
	private AdaptationHandler adaptHandler = null;
	private HashMap<String, Class> entries = null;
	private HashMap<String, Long> sTimes = null;
	private HashMap<String, AtomicInteger> state = null;
	// private JedisPool jedisPool;
	private boolean isGenerator = false;
	private static Logger logger = LoggerFactory.getLogger(SimpleTask.class);

	private String jedisHost = null;

	String name = null;

	ConcurrentLinkedQueue<Integer> tids = null;

	private HashMap<String, Long> enqueueTime = null;
	rtSampler rts = null;
	TCPServer sts = null;
	private Boolean isCgv2 = false;

	public SimpleTask(String address, int port, HashMap<String, Class> entries, HashMap<String, Long> sTimes, int tsize,
			boolean isEmulated, String name, String jedisHost, Long aHperiod, Long rtSamplingPeriod,
			Long stSamplerPeriod) {
		this.setEnqueueTime(new HashMap<String, Long>());
		this.setName(name);
		this.threadpoolSize = tsize;
		this.entries = entries;
		this.setEmulated(isEmulated);
		this.jedisHost = jedisHost;
		this.initState();
		this.sts = new TCPServer(port + 10000, this);
		this.sts.start();
		try {
			this.server = HttpServer.create(new InetSocketAddress(port), this.backlogsize);
			this.setPort(port);
			this.server.createContext("/", new AcquireHttpHandler(this));
			this.server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(20));
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.initThreadPoolExecutor();
		this.tids = new ConcurrentLinkedQueue<Integer>();
		this.sTimes = sTimes;

		if (aHperiod != null) {
			ScheduledExecutorService se = Executors.newSingleThreadScheduledExecutor();
			this.adaptHandler = new AdaptationHandler(this, this.jedisHost);
			se.scheduleAtFixedRate(this.adaptHandler, 0, aHperiod, TimeUnit.MILLISECONDS);
		}

		if (rtSamplingPeriod != null) {
			ScheduledExecutorService se = Executors.newSingleThreadScheduledExecutor();
			this.rts = new rtSampler(this.jedisHost, this.getName());
			se.scheduleAtFixedRate(rts, 0, rtSamplingPeriod, TimeUnit.MILLISECONDS);
		}
	}

	public SimpleTask(String address, int port, HashMap<String, Class> entries, HashMap<String, Long> sTimes, int tsize,
			boolean isEmulated, String name, String jedisHost, Long aHperiod, Long rtSamplingPeriod,
			Long stSamplerPeriod, Boolean isCgv2) {
		this(address, port, entries, sTimes, tsize, isEmulated, name, jedisHost, aHperiod, rtSamplingPeriod,
				stSamplerPeriod);
		this.isCgv2 = isCgv2;
	}

	public SimpleTask(HashMap<String, Class> entries, HashMap<String, Long> sTimes, int tsize, String name,
			String jedisHost, Long stSamplerPeriod,Long rtSamplingPeriod) {
		this.setEnqueueTime(new HashMap<String, Long>());
		this.setName(name);
		this.jedisHost = jedisHost;
		this.isGenerator = true;
		this.threadpoolSize = tsize;
		this.entries = entries;
		this.sTimes = sTimes;
		this.initThreadPoolExecutor();
		this.threadpool.allowCoreThreadTimeOut(true);
		this.jedisHost = jedisHost;
		this.initState();
		this.sts = new TCPServer(3333, this);
		this.sts.start();
		if (stSamplerPeriod != null) {
			ScheduledExecutorService se = Executors.newSingleThreadScheduledExecutor();
			StateSampler client_sampler = new StateSampler(jedisHost, this);
			se.scheduleAtFixedRate(client_sampler, 0, stSamplerPeriod, TimeUnit.MILLISECONDS);
		}
		if (rtSamplingPeriod != null) {
			ScheduledExecutorService se = Executors.newSingleThreadScheduledExecutor();
			this.rts = new rtSampler(this.jedisHost, this.getName());
			se.scheduleAtFixedRate(rts, 0, rtSamplingPeriod, TimeUnit.MILLISECONDS);
		}
	}

	public void setThreadPoolSize(int size) throws Exception {
		if (size <= 0) {
			throw new Exception("Threadpool size has to be >0");
		}
		if (size <= this.maxThreadSize) {
			this.threadpoolSize = size;
			// this.threadpool.setMaximumPoolSize(size);
			this.threadpool.setCorePoolSize(size);
		} else {
			throw new Exception("Maximum threadpool size reached");
		}
	}

	public void start() {
		if (!this.isGenerator && this.getServer() != null)
			this.getServer().start();
		if (this.isGenerator) {
			// only in case of workload generator, I assume that there is only one client
			// task
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

//	public JedisPool getJedisPool() {
//		return jedisPool;
//	}

//	public void setJedisPool(JedisPool jedisPool) {
//		this.jedisPool = jedisPool;
//	}

	public static Double sampleExp(double rate, ThreadLocalRandom rnd) {
		return Double.valueOf((-1.0 / rate) * Math.log(1 - rnd.nextDouble()));
	}

	public AdaptationHandler getAdaptHandler() {
		return adaptHandler;
	}

	public void setAdaptHandler(AdaptationHandler adaptHandler) {
		this.adaptHandler = adaptHandler;
	}

	public void stop() {
		if (this.server != null)
			this.server.stop(2);
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

//	public void initJedisPool(int poolSize, String poolAddr) {
//		JedisPoolConfig cfg = new JedisPoolConfig();
//		cfg.setMaxTotal(poolSize);
//		cfg.setFairness(true);
//		//cfg.setMaxWaitMillis(10000);
//		this.jedisPool = new JedisPool(cfg, poolAddr);
//	}
//
//	public void initJedisPool() {
//		this.initJedisPool(500, this.jedisHost);
//	}

	public void initThreadPoolExecutor() {
		
		this.threadpool=(ThreadPoolExecutor) Executors.newCachedThreadPool();
		
//		this.threadpool  = new ThreadPoolExecutor(this.threadpoolSize, Integer.MAX_VALUE, 1L,
//				TimeUnit.NANOSECONDS, new SynchronousQueue<Runnable>(), new ThreadPoolExecutor.DiscardPolicy());
		
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

	public static AtomicBoolean getToStopGracefully() {
		return toStopGracefully;
	}

	public static void setToStopGracefully(AtomicBoolean toStopGracefully) {
		SimpleTask.toStopGracefully = toStopGracefully;
	}

	public String getJedisHost() {
		return jedisHost;
	}

	public void setJedisHost(String jedisHost) {
		this.jedisHost = jedisHost;
	}

	private void initState() {
		this.state = new HashMap<String, AtomicInteger>();
		if (this.isGenerator) {
			this.state.put("think", new AtomicInteger(0));
		} else {
			for (String key : this.entries.keySet()) {
				this.state.put(key + "_bl", new AtomicInteger(0));
				this.state.put(key + "_ex", new AtomicInteger(0));
			}
		}
	}

	public HashMap<String, AtomicInteger> getState() {
		return state;
	}

	public void setState(HashMap<String, AtomicInteger> state) {
		this.state = state;
	}

	public rtSampler getRts() {
		return rts;
	}

	public void setRts(rtSampler rts) {
		this.rts = rts;
	}

	public Boolean getIsCgv2() {
		return isCgv2;
	}

	public void setIsCgv2(Boolean isCgv2) {
		this.isCgv2 = isCgv2;
	}
}
