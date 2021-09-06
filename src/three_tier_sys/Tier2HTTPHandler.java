package three_tier_sys;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpClient.Version;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;

import com.google.common.collect.Maps;
import com.hubspot.jinjava.Jinjava;
import com.sun.net.httpserver.HttpExchange;

import Server.SimpleTask;
import Server.TierHttpHandler;
import redis.clients.jedis.Jedis;

@SuppressWarnings("restriction")
public class Tier2HTTPHandler extends TierHttpHandler {

	public Tier2HTTPHandler(SimpleTask lqntask, HttpExchange req, long stime) {
		super(lqntask, req, stime);
	}

	public void handleResponse(HttpExchange req, String requestParamValue) throws InterruptedException {
		this.measureIngress();

		Jedis jedis = this.getLqntask().getJedisPool().getResource();

		Jinjava jinjava = new Jinjava();
		Map<String, Object> context = Maps.newHashMap();
		context.put("task", "Tier2");
		context.put("entry", "e2");

		String renderedTemplate = jinjava.render(this.getWebPageTpl(), context);

		if (!this.getLqntask().isEmulated()) {
			jedis.close();
			this.doWorkCPU();
		} else {
			// get all entry currentyly executing on this task
			Float executing = 0f;
			String[] entries = this.getLqntask().getEntries().keySet().toArray(new String[0]);
			for (String e : entries) {
				String n = jedis.get(e + "_ex");
				if (n != null) {
					executing += Float.valueOf(n);
				}
			}
			jedis.close();
			this.doWorkSleep(executing);
		}
		
		this.measureEgress();

		try {
			req.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
			req.getResponseHeaders().set("Cache-Control", "no-store, no-cache, max-age=0, must-revalidate");
			OutputStream outputStream = req.getResponseBody();
			req.sendResponseHeaders(200, renderedTemplate.length());
			outputStream.write(renderedTemplate.getBytes());
			outputStream.flush();
			outputStream.close();
			outputStream = null;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public String getWebPageName() {
		return "tier2.html";
	}

	@Override
	public String getName() {
		return "e2";
	}
}
