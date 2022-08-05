package monitoring;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

import Server.SimpleTask;

public class rtSampler extends Thread {

	private ConcurrentLinkedQueue<rtSample> rt = null;
	private String name = null;
	private File logFile = null;
	private FileWriter logW = null;
	private Integer nr = 100;
	private SimpleTask t = null;

	public rtSampler(String monirotHost, String name, SimpleTask t) {
		this.rt = new ConcurrentLinkedQueue<rtSample>();
		this.name = name;
		this.logFile = new File(String.format("%s_t1.log", this.name));
		try {
			this.logW = new FileWriter(this.logFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.t = t;
	}

	@Override
	public void run() {
		rtSample sample = null;
		while (true) {
			if (this.rt.size() >= this.nr) {
				try {
					this.logW.write(System.nanoTime()+"\n");
					this.logW.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
				double rtAvg = 0.0;
				for (int nsamples = 0; nsamples < this.nr; nsamples++) {
					sample = this.rt.poll();
					if (sample.getEnd() != null && sample.getStart() != null) {
						rtAvg += sample.getEnd() - sample.getStart();
					}
				}
				this.t.getJedisPool().getResource().set(this.t.getName()+"_rt",
						"%f".formatted(new Object[] { rtAvg / this.nr }));
			}
		}
	}

	public void addSample(rtSample sample) {
		this.rt.add(sample);
	}

}
