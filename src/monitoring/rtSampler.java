package monitoring;

import java.util.concurrent.LinkedBlockingQueue;

public class rtSampler {

	private LinkedBlockingQueue<rtSample> rt = null;

	public rtSampler() {
		this.rt = new LinkedBlockingQueue<rtSample>();
	}

	public void addSample(rtSample sample) {
		this.rt.add(sample);
	}
	
	public LinkedBlockingQueue<rtSample> getSamples() {
		return this.rt;
	}

}
