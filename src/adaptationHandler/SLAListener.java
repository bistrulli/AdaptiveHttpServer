package adaptationHandler;

import Ctrl.Ctrl;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class SLAListener extends JedisPubSub {

	private Ctrl ctrl = null;

	public SLAListener(Ctrl ctrl) {
		this.ctrl = ctrl;
	}

	@Override
	public void onPMessage(String pattern, String channel, String message) {
		switch (message) {
		case "set": {
			System.out.println("sla change");
			Jedis jedis = this.ctrl.getTask().getJedisPool().getResource();
			String sla=jedis.get(this.ctrl.getTask().getName() + "_sla");
			System.out.println(sla);
			jedis.close();
			break;
		}
		}
	}
}