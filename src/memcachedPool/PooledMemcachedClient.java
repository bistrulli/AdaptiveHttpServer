package memcachedPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import net.spy.memcached.MemcachedClient;

public class PooledMemcachedClient extends MemcachedClient {
	
	memcachedPool pool=null;

	public PooledMemcachedClient(List<InetSocketAddress> addrs) throws IOException {
		super(addrs);
	}

	public PooledMemcachedClient(InetSocketAddress[] ia) throws IOException {
		super(ia);
	}
	public PooledMemcachedClient(InetSocketAddress inetSocketAddress, memcachedPool memcachedPool) throws IOException {
		super(inetSocketAddress);
		this.pool=memcachedPool;
	}

	public PooledMemcachedClient(InetSocketAddress ia) throws IOException {
		super(ia);
	}

	public PooledMemcachedClient() throws IOException {
	}
	
	public void close() {
		this.pool.getPool().add(this);
	}
	
	
}
