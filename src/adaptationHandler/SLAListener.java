package adaptationHandler;

import Ctrl.Ctrl;
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
			//qui il codice per gestire il cambiamento di SLA
			break;
		}
		}
	}
}