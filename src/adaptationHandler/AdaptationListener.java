package adaptationHandler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import Server.SimpleTask;
import redis.clients.jedis.JedisPubSub;

public class AdaptationListener extends JedisPubSub {

	private SimpleTask task = null;
	private File logFile=null;
	private FileWriter logW=null;

	public AdaptationListener(SimpleTask task) {
		this.task = task;
		this.logFile = new File(String.format("%s_t3.log", this.task.getName()));
		try {
			this.logW = new FileWriter(this.logFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onPMessage(String pattern, String channel, String message) {
		switch (message) {
		case "set": {
			try {
				this.logW.write(System.nanoTime()+"\n");
				this.logW.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		}
		}
	}
}