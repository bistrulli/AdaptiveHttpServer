package test_informal;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class testJedisPool {
	public static void main(String[] args) {
		JedisPoolConfig cfg = new JedisPoolConfig();
		cfg.setMaxTotal(128);
		JedisPool pool = new JedisPool(cfg,"localhost");
		Jedis jedis = pool.getResource();
		System.out.println(jedis.get("driver"));
	}
}