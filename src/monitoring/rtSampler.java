package monitoring;

import java.util.concurrent.ConcurrentLinkedQueue;

public class rtSampler {

	private ConcurrentLinkedQueue<rtSample> rt = null;

	public rtSampler() {
		this.rt = new ConcurrentLinkedQueue<rtSample>();
	}

	public void addSample(rtSample sample) {
		this.rt.add(sample);
	}
	
	public ConcurrentLinkedQueue<rtSample> getSamples() {
		return this.rt;
	}

}
