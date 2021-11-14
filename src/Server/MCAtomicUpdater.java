package Server;

import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.MemcachedClient;

public class MCAtomicUpdater {

	public static void AtomicIncr(MemcachedClient memcache, Integer by, String key, int times) throws Exception {
//		int curr = 0;
//		while (true) {
//			CASValue casValue = memcache.gets(key);
//			if (casValue == null)
//				throw new Exception("key not existent");
//
//			CASResponse res = memcache.cas(key, casValue.getCas(),
//					String.valueOf(Integer.valueOf(String.valueOf(casValue.getValue())) + by));
//
//			if (CASResponse.NOT_FOUND.equals(res))
//				throw new Exception("key not found");
//			else if (CASResponse.OK.equals(res))
//				break;
//
//			curr++;
//		}
	}
}
