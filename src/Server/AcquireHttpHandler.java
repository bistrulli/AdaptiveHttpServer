package Server;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

public class AcquireHttpHandler implements HttpHandler {

	private SimpleTask task = null;
	HttpExchange req = null;
	ArrayList<Runnable> backlog = null;
	private ThreadLocalRandom rnd;

	public AcquireHttpHandler(SimpleTask task) {
		this.task = task;
		this.backlog = new ArrayList<Runnable>();
		this.rnd = ThreadLocalRandom.current();
	}

	public synchronized void measure(String entry, String snd) {
		Jedis jedis = this.getTask().getJedisPool().getResource();
		Transaction t = jedis.multi();

		if (snd.equals("think"))
			t.decr("think");
		else
			t.decr(String.format("%s_ex", snd));

		t.incr(String.format("%s_bl", entry));
		t.exec();
		t.close();
		jedis.close();
	}

	@Override
	public void handle(HttpExchange req) throws IOException {
		SimpleTask.getLogger().debug(String.format("%s recieved", this.task.getName()));
		Map<String, String> params = this.getTask().queryToMap(req.getRequestURI().getQuery());
		if (params.get("entry") == null || params.get("entry").equals("")) {
			SimpleTask.getLogger().error("Request with no specified entry");
		}
		if (params.get("snd") == null || params.get("snd").equals("")) {
			SimpleTask.getLogger().error("Request with no specified sender");
		}
		this.measure(params.get("entry"), params.get("snd"));
		try {
			Constructor<? extends Runnable> c = null;
			if (this.task.getEntries().get(params.get("entry")) == null) {
				SimpleTask.getLogger().error(String.format("No class registered for entry %s", params.get("entry")));
			}
			if (this.task.getsTimes().get(params.get("entry")) == null) {
				SimpleTask.getLogger()
						.error(String.format("No service time registered for entry %s", params.get("entry")));
			}
			// System.out.println(this.task.getEntries().get(params.get("entry")));
			c = this.task.getEntries().get(params.get("entry")).getDeclaredConstructor(SimpleTask.class,
					HttpExchange.class, long.class);

			// this.backlog.add(c.newInstance(this.getTask(),
			// req,this.task.getsTimes().get(params.get("entry"))));
			// PER QUESTA APPLICAZIONE NON SERVE GPS
			// SimpleTask.getLogger().debug("GPS choice made");
			// this.task.getThreadpool().submit(this.backlog.get(this.rnd.nextInt(this.backlog.size())));
			long stime=System.nanoTime();
			if(params.get("stime")!=null)
				stime=Long.valueOf(params.get("stime"));
			this.task.getEnqueueTime().put(params.get("id"),stime);

			// implemento fcfs usando la coda del threadpool.
			this.task.getThreadpool()
					.submit(c.newInstance(this.getTask(), req, this.task.getsTimes().get(params.get("entry"))));
		} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException e) {
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