package Server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.distribution.NormalDistribution;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.sun.net.httpserver.HttpExchange;

import jni.GetThreadID;
import monitoring.rtSample;
import utility.TruncatedNormal;

public abstract class TierHttpHandler implements Runnable {

	private SimpleTask lqntask = null;
	private Long stime = null;
	private TruncatedNormal dist = null;
	private ThreadLocalRandom rnd = null;
	private HttpExchange req = null;
	private ThreadMXBean mgm = null;
	private Integer tid = null;
	private String webPageTpl = null;
	private String name = null;
	ProcessBuilder processBuilder = null;

	public TierHttpHandler(SimpleTask lqntask, HttpExchange req, long stime) {
		this.setLqntask(lqntask);
		// this.dist = new ExponentialDistribution(stime);
		//this.dist = new NormalDistribution(stime, stime / 5.0);
		this.stime = stime;
		this.dist=new TruncatedNormal(stime, stime/100.0, 0, Integer.MAX_VALUE);
		this.rnd = ThreadLocalRandom.current();
		this.req = req;
		// this.mgm = ManagementFactory.getThreadMXBean();

		try {
			final ClassLoader loader = this.getClass().getClassLoader();
			this.webPageTpl = CharStreams
					.toString(new InputStreamReader(loader.getResourceAsStream(this.getWebPageName()), Charsets.UTF_8));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public abstract void handleResponse(HttpExchange req, String requestParamValue)
			throws InterruptedException, IOException;

	public abstract String getWebPageName();

	public abstract String getName();

	public void doWorkCPU() {
		//long delay = Long.valueOf(Math.round(dist.sample() * 1000000));
		
		java.util.Random r = new java.util.Random();
		double noise = r.nextGaussian() * Math.sqrt(this.stime/500) + this.stime;
		
		long delay = Long.valueOf(Math.round(noise * 1000000));
		
		long start = this.mgm.getCurrentThreadCpuTime();
		while ((this.mgm.getCurrentThreadCpuTime() - start) < delay) {
		}
	}

	public void doWorkSleep(float executing) throws InterruptedException {
		Double isTime = dist.sample();
		isTime = isTime > 0 ? isTime : 0;
		// Double isTime = dist.getMean();
		SimpleTask.getLogger().debug(String.format("executing %f", executing));
		SimpleTask.getLogger().debug(String.format("ncore %.3f", this.lqntask.getHwCore()));
		SimpleTask.getLogger().debug(String.format("%s sleeps for: %.3f", this.lqntask.getName(), isTime));
		Float d = isTime.floatValue() * (executing / this.getLqntask().getHwCore().floatValue());
		// Float d= Double.valueOf(dist.getMean()).floatValue() * (executing /
		// this.getLqntask().getHwCore().floatValue());
		SimpleTask.getLogger().debug(String.format("actual sleep:%d", Math.max(Math.round(d), Math.round(isTime))));
		TimeUnit.MILLISECONDS.sleep(Math.max(Math.round(d), Math.round(isTime)));
		SimpleTask.getLogger().debug("work done");
	}

	public String handleGetRequest(HttpExchange req) {
		if (req.getRequestURI().toString().split("\\?").length > 1) {
			return req.getRequestURI().toString().split("\\?")[1].split("=")[1];
		} else {
			return "";
		}
	}

	@Override
	public void run() {
		this.mgm = ManagementFactory.getThreadMXBean();
		if (!this.mgm.isThreadCpuTimeSupported()) {
			System.err.println("ThreadCpuTime in not suppoted");
		}
		this.mgm.setThreadCpuTimeEnabled(true);
		try {
			if (this.req != null) {
				this.handleResponse(this.req, this.handleGetRequest(this.req));
			}
		} catch (InterruptedException e) {
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			Map<String, String> params = this.getLqntask().queryToMap(this.req.getRequestURI().getQuery());
			int qlen = this.getLqntask().getState().get(params.get("entry") + "_ex").get()
					+ this.getLqntask().getState().get(params.get("entry") + "_bl").get();
			this.getLqntask().getRts().addSample(
					new rtSample(this.getLqntask().getEnqueueTime().get(params.get("id")), System.nanoTime(), qlen));

			this.lqntask.ncmp.addAndGet(1);
		}
	}

	public void addToCGV2Group(String gname) {
		if (this.lqntask.getIsCgv2()) {
			try {
				int tid = GetThreadID.get_tid();
				// aggiungo questo thread al gruppo dei serventi del tier
				BufferedWriter out;
				try {
					out = new BufferedWriter(new FileWriter(
							"/sys/fs/cgroup/" + this.getLqntask().getName() + "/" + gname + "/cgroup.threads", true));
					out.write(String.valueOf(tid));
					out.flush();
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void measureIngress() {
		Integer ex = null;
		Integer bl = null;
		// synchronized (this) {
		bl = this.lqntask.getState().get(String.format("%s_bl", this.getName())).decrementAndGet();
		ex = this.lqntask.getState().get(String.format("%s_ex", this.getName())).incrementAndGet();
		// }
		SimpleTask.getLogger().debug(String.format("%s ingress (%d %d)", this.getName(), ex, bl));
	}

	public void measureReturn() {
		int ex = this.lqntask.getState().get(String.format("%s_ex", this.getName())).incrementAndGet();
		SimpleTask.getLogger().debug(String.format("%s return-%s", this.getName(), ex));

	}

	public void measureEgress() {
		int ex = this.lqntask.getState().get(String.format("%s_ex", this.getName())).decrementAndGet();
		SimpleTask.getLogger().debug(String.format("%s egress-%s", this.getName(), ex));

	}

	public String getWebPageTpl() {
		return webPageTpl;
	}

	public void setWebPageTpl(String webPageTpl) {
		this.webPageTpl = webPageTpl;
	}

	public SimpleTask getLqntask() {
		return lqntask;
	}

	public void setLqntask(SimpleTask lqntask) {
		this.lqntask = lqntask;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void updateAffinity(int start, int end) {
		String[] commands = new String[] { "taskset", "-pc", String.format("%d-%d", start, end),
				String.valueOf(this.tid) };
		try {
			Process p = Runtime.getRuntime().exec(commands);
			BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = null;
			while ((line = is.readLine()) != null) {
				// System.out.println(line);
			}
			is.close();
			p.destroy();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void cpuLimit() {
		// create cgroup (lo assumo fatto internamente chimo solo l'update dei parametri
		// ma questo lo faccio fare direttamtne al controllore da python)
		// cgcreate -g cpu:/t1
		// get value
		// cgget t1 -r cpu.cfs_period_us -r cpu.cfs_quota_us
		// set value
		// cgset t1 -r cpu.cfs_period_us=100000 -r cpu.cfs_quota_us=-1
	}

//	public Jedis getJedis() {
//		return jedis;
//	}
//
//	public void setJedis(Jedis jedis) {
//		this.jedis = jedis;
//	}

}
