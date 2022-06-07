package monitoring;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class rtSampler implements Runnable {

	private ConcurrentLinkedQueue<rtSample> rt = null;
	private String name = null;
	private File logFile = null;
	private FileWriter logW = null;

	public rtSampler(String monirotHost, String name) {
		this.rt = new ConcurrentLinkedQueue<rtSample>();
		this.name = name;
		this.logFile = new File(String.format("%s_rtlog.log", this.name));
		try {
			this.logW = new FileWriter(this.logFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		rtSample[] samples = this.rt.toArray(new rtSample[0]);
		try {
			this.saveRT(samples);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void saveRT(rtSample[] samples) throws Exception {
		rtSample sample = null;
		while ((sample = this.rt.poll()) != null) {
			if (sample.getEnd() != null && sample.getStart() != null) {
				this.logW.write(String.format("%d\t%d\n", sample.getRT(), sample.getEnd()));
				this.logW.flush();
			}
		}
	}

	public void addSample(rtSample sample) {
		this.rt.add(sample);
	}

}
