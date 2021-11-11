package memcachedPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentLinkedQueue;

public class memcachedPool {
	ConcurrentLinkedQueue<PooledMemcachedClient> pool = null;
	Integer poolMaxSize = -1;
	String host=null;
	Integer port=null;

	public memcachedPool(String host, int port) {
		this.pool = new ConcurrentLinkedQueue<PooledMemcachedClient>();
		this.port=port;
		this.host=host;
	}

	public ConcurrentLinkedQueue<PooledMemcachedClient> getPool() {
		return pool;
	}

	public void setPool(ConcurrentLinkedQueue<PooledMemcachedClient> pool) {
		this.pool = pool;
	}

	public Integer getPoolMaxSize() {
		return poolMaxSize;
	}

	public void setPoolMaxSize(Integer poolMaxSize) {
		this.poolMaxSize = poolMaxSize;
	}
	
	public PooledMemcachedClient getConnection() {
		PooledMemcachedClient memcachedClient = this.pool.poll();
		if(memcachedClient==null) {
			try {
				memcachedClient = new PooledMemcachedClient(new InetSocketAddress(this.host, this.port),this);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return memcachedClient;
	}
}
