package test_informal;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.XMemcachedClient;

public class test_memcached {
	public static void main(String[] args) {
		try {
			MemcachedClient client = new XMemcachedClient("localhost", 11211);

			// store a value for one hour(synchronously).
			client.set("key-test", 3600, "0");
			// Retrieve a value.(synchronously).
			Object someObject = client.get("key");
			// Retrieve a value.(synchronously),operation timeout two seconds.
			someObject = client.get("key", 2000);

			// Touch cache item ,update it's expire time to 10 seconds.
			boolean success = client.touch("key", 10);

			// delete value
			client.delete("key");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
