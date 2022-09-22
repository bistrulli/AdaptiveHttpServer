package Ctrl;

import java.io.IOException;
import java.util.ArrayList;

import Server.SimpleTask;
import monitoring.rtSample;
import monitoring.rtSampler;
import us.hebi.matlab.mat.format.Mat5;
import us.hebi.matlab.mat.types.MatFile;
import us.hebi.matlab.mat.types.Matrix;

public class Ctrl extends Thread {

	private rtSampler rtSampler = null;
	private Integer nr = 20;
	private SimpleTask task = null;

	private double rtAvg = 0.0;
	private double alpha = 0.9;
	private double cores_min = 0.1;
	private double cores_max = 7;
	private double cores_ini = 1;
	private double t = 0;
	private int k = 0;
	private double qlen = 0;
	private double tauro = 0.25;

	private double t_km1 = 0;
	private double l_km1 = 0;
	private double e_km1 = 0;
	private double u_km1 = 0;
	private double cores_km1 = 0;
	private double t_k = 0;
	private double l_k = 0;
	private double ros_km1_meas = 0;
	private double taur_meas = 0;
	private double sigma_km1_meas = 0;
	private double cores_k = 0;
	private double e_k = 0;
	private double u_k = 0;
	private double ncp_km1=0;
	private ArrayList<Double> vcores=null;
	private ArrayList<Double> vrt=null;

	public Ctrl(SimpleTask task, rtSampler rtSampler) {
		this.task = task;
		this.rtSampler = rtSampler;

		this.vcores = new ArrayList<Double>();
		this.vrt = new ArrayList<Double>();
	}

	@Override
	public void run() {
		super.run();
		rtSample sample = null;
		while (true) {
			if (this.rtSampler.getSamples().size() >= this.nr) {
				double rtAvg_t = 0.0;
				double qlen_t = 0.0;
				this.t = System.nanoTime();
				for (int nsamples = 0; nsamples < this.nr; nsamples++) {
					sample = this.rtSampler.getSamples().poll();
					if (sample.getEnd() != null && sample.getStart() != null) {
						rtAvg_t += sample.getEnd() - sample.getStart();

					}
					qlen_t += sample.getQlen();
				}
				this.rtAvg = rtAvg_t / (1e09 * this.nr);
				this.qlen = qlen_t / this.nr;
				this.doCtrl();
			}
		}
	}

	private void doCtrl() {
		System.out.println("rt=%s, qlen=%s".formatted(new Object[] { this.rtAvg, this.qlen }));
		this.k++;

		this.t_k = this.t;
		
		this.l_k = this.qlen;
		this.ros_km1_meas = 0;
		this.taur_meas = 0;
		this.sigma_km1_meas = 0;
		this.cores_k = this.cores_ini;
		this.e_k = 0;
		this.u_k = 0;

		if (this.k > 1) {
			double Ts = (t_k - t_km1) / 1e09;
			ros_km1_meas = (this.task.getNcmp().get()-this.ncp_km1) / Ts;
			taur_meas = this.qlen / ros_km1_meas;
			sigma_km1_meas = cores_km1 / ros_km1_meas;
			e_k = this.tauro - taur_meas;
			u_k = u_km1 + e_k - alpha * e_km1;
			cores_k = sigma_km1_meas * l_k / ((1 - alpha) * u_k + alpha * taur_meas);

			cores_k = Math.min(cores_max, Math.max(cores_min, cores_k));
			//this.task.setHwCore(Double.valueOf(cores_k).floatValue());
//			try {
//				this.task.setThreadPoolSize(Double.valueOf(Math.ceil(cores_k)).intValue());
//			} catch (Exception e1) {
//				e1.printStackTrace();
//			}

			u_k = (alpha * cores_k * taur_meas - l_k * sigma_km1_meas) / ((alpha - 1) * cores_k);
			
			this.vcores.add(cores_k);
			this.vrt.add(this.rtAvg);
			
			
			//devo salvare il mat con i dati dell'esperimento
			if(k>100) {
				System.out.println("saving mat");
				MatFile matFile = Mat5.newMatFile();
				Matrix rtMatrix = Mat5.newMatrix(1,this.vrt.size());
				Matrix coreMatrix=Mat5.newMatrix(1,this.vcores.size());
				for (int i=0; i<this.vrt.size();i++) {
					rtMatrix.setDouble(0, i, this.vrt.get(i));
					coreMatrix.setDouble(0, i,this.vcores.get(i));
				}
				matFile.addArray("rt", rtMatrix);
				matFile.addArray("core", coreMatrix);
				try {
					Mat5.writeToFile(matFile, "res.mat");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
				
		}

		this.t_km1 = t_k;
		this.l_km1 = l_k;
		this.e_km1 = e_k;
		this.u_km1 = u_k;
		this.ncp_km1=this.task.getNcmp().get();
		this.cores_km1 = cores_k;
		this.t = System.nanoTime();
	}
}
