package monitoring;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.lang3.ArrayUtils;

import redis.clients.jedis.Jedis;
import us.hebi.matlab.mat.types.Matrix;

public class StateSampler implements Runnable {
	
	private Jedis j = null;
	private String[] keys = null;
	private int tick = -1;
	private int limitTick=-1;
	private CountDownLatch waitExp=null;
	private Matrix q =null;
	
	public StateSampler(String[] keys,int limitTick, Matrix q, CountDownLatch waitExp) {
		this.j=new Jedis();
		this.keys=keys;
		this.tick=0;
		this.limitTick=limitTick;
		this.waitExp=waitExp;
		this.q=q;
	}

	private List<String> getState() {
		return this.j.mget(this.keys);
	}

	@Override
	public void run() {
		if (this.tick < limitTick) {
			List<String> state = this.getState();
			for (int k = 0; k < state.size(); k++) {
				if (state.get(k) == null) {
					state.set(k, "-1");
				}
				this.q.setDouble(new int[] {this.tick, k}, Double.valueOf(state.get(k)));
			}
			//System.out.println(String.format("%.3f",Integer.valueOf(this.tick).floatValue()/Integer.valueOf(this.limitTick).floatValue()));
			this.tick++;
		} else {
			this.waitExp.countDown();
		}
	}

}
