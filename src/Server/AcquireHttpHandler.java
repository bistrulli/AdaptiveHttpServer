package Server;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class AcquireHttpHandler implements HttpHandler {

	private SimpleTask task = null;
	HttpExchange req = null;
	ArrayList<Runnable> backlog = null;
	private ThreadLocalRandom rnd = null;

	public AcquireHttpHandler(SimpleTask task) {
		this.task = task;
		this.backlog = new ArrayList<Runnable>();
		this.rnd = ThreadLocalRandom.current();
	}

	public void measure(String entry, String snd) {
		this.task.getState().get(String.format("%s_bl", entry)).incrementAndGet();
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
				throw new IllegalArgumentException(String.format("No class registered for entry %s", params.get("entry")));
			}
			if (this.task.getsTimes().get(params.get("entry")) == null) {
				SimpleTask.getLogger()
						.error(String.format("No service time registered for entry %s", params.get("entry")));
				throw new IllegalArgumentException(String.format("No service time registered for entry %s", params.get("entry")));
			}
			// System.out.println(this.task.getEntries().get(params.get("entry")));
			c = this.task.getEntries().get(params.get("entry")).getDeclaredConstructor(SimpleTask.class,
					HttpExchange.class, long.class);

			// this.backlog.add(c.newInstance(this.getTask(),
			// req,this.task.getsTimes().get(params.get("entry"))));
			// PER QUESTA APPLICAZIONE NON SERVE GPS
			// SimpleTask.getLogger().debug("GPS choice made");
			// this.task.getThreadpool().submit(this.backlog.get(this.rnd.nextInt(this.backlog.size())));
			
			
//			while(this.task.getThreadpoolSize()<this.task.getThreadpool().getActiveCount()) {
//				try {
//					TimeUnit.MILLISECONDS.sleep(3);
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
//			}
			
			long stime = System.nanoTime();
			if (params.get("stime") != null)
				stime = Long.valueOf(params.get("stime"));
			else
				stime = System.nanoTime();
			this.task.getEnqueueTime().put(params.get("id"), stime);
				
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