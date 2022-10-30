package Ctrl;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import Server.SimpleTask;
import monitoring.rtSample;
import monitoring.rtSampler;
import redis.clients.jedis.Jedis;
import us.hebi.matlab.mat.format.Mat5;
import us.hebi.matlab.mat.types.MatFile;
import us.hebi.matlab.mat.types.Matrix;

public class Ctrl extends Thread {

	private rtSampler rtSampler = null;
	private Integer nr = null;
	private SimpleTask task = null;

	private double rtAvg = 0.0;
	private double alpha = 0.7;
	private double cores_min = 0.1;
	private double cores_max = 28;
	private double cores_ini = 1;
	private double t = 0;
	private int k = 0;
	private double qlen = 0;
	private double tauro = 0.5;
	final private double period = 1e05;

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
	private double ncp_km1 = 0;
	private ArrayList<Double> vcores = null;
	private ArrayList<Double> vrt = null;
	private ArrayList<Double> vctrlTime = null;
	private ArrayList<Double> vU = null;
	private Jedis j = null;

	public Ctrl(SimpleTask task, rtSampler rtSampler) {
		this.task = task;
		this.rtSampler = rtSampler;

		this.vcores = new ArrayList<Double>();
		this.vrt = new ArrayList<Double>();
		this.vctrlTime = new ArrayList<Double>();
		this.vU = new ArrayList<Double>();

		j = this.task.getJedisPool().getResource();
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

	private void actuateCtrl(double core) {
		Long quota = Double.valueOf(Math.ceil(core * this.period)).longValue();
		//System.out.println(core + " " + quota);

		try {
			this.task.setThreadPoolSize(Math.max(1, Double.valueOf(Math.ceil(core)).intValue()));
			BufferedWriter out;
			try {
				out = new BufferedWriter(new FileWriter("/sys/fs/cgroup/" + this.task.getName() + "/e1/cpu.max", true));
				out.write(quota + " " + this.period + "\n");
				out.flush();
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}

	}

	private void doCtrl() {
		System.out.println("rt=%s, qlen=%s".formatted(new Object[] { this.rtAvg, this.qlen }));
		this.k++;

		String sla = this.j.get(this.task.getName() + "_sla");
		if (sla != null) {
			this.tauro = Double.valueOf(sla);
		}

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
			ros_km1_meas = (this.task.getNcmp().get() - this.ncp_km1) / Ts;
			taur_meas = this.qlen / ros_km1_meas;
			sigma_km1_meas = cores_km1 / ros_km1_meas;
			e_k = this.tauro - taur_meas;
			u_k = u_km1 + e_k - alpha * e_km1;
			cores_k = sigma_km1_meas * l_k / ((1 - alpha) * u_k + alpha * taur_meas);
			
			
			
			System.out.println(cores_max+" "+this.cores_min+" "+cores_k);
			cores_k = Math.min(this.cores_max, Math.max(this.cores_min, cores_k));
			// this.task.setHwCore(Double.valueOf(cores_k).floatValue());
			this.actuateCtrl(cores_k);

			u_k = (alpha * cores_k * taur_meas - l_k * sigma_km1_meas) / ((alpha - 1) * cores_k);

			this.vcores.add(cores_k);
			this.vrt.add(this.rtAvg);
			this.vctrlTime.add(this.t_k);
			this.vU.add(u_k);

			// devo salvare il mat con i dati dell'esperimento
			if (k > 100) {
				System.out.println("saving mat");
				MatFile matFile = Mat5.newMatFile();
				Matrix rtMatrix = Mat5.newMatrix(1, this.vrt.size());
				Matrix coreMatrix = Mat5.newMatrix(1, this.vcores.size());
				Matrix timeMatrix = Mat5.newMatrix(1, this.vctrlTime.size());
				Matrix uMatrix = Mat5.newMatrix(1, this.vU.size());
				for (int i = 0; i < this.vrt.size(); i++) {
					rtMatrix.setDouble(0, i, this.vrt.get(i));
					coreMatrix.setDouble(0, i, this.vcores.get(i));
					timeMatrix.setDouble(0, i, this.vctrlTime.get(i));
					uMatrix.setDouble(0, i, this.vU.get(i));
				}
				matFile.addArray("rt", rtMatrix);
				matFile.addArray("core", coreMatrix);
				matFile.addArray("ctime", timeMatrix);
				matFile.addArray("u", uMatrix);
				try {
					Mat5.writeToFile(matFile, this.task.getName() + "out.mat");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}

		this.t_km1 = t_k;
		this.l_km1 = l_k;
		this.e_km1 = e_k;
		this.u_km1 = u_k;
		this.ncp_km1 = this.task.getNcmp().get();
		this.cores_km1 = cores_k;
		this.t = System.nanoTime();
	}

	public Integer getNr() {
		return nr;
	}

	public void setNr(Integer nr) {
		this.nr = nr;
	}

	public double getAlpha() {
		return alpha;
	}

	public void setAlpha(double alpha) {
		this.alpha = alpha;
	}

	public double getTauro() {
		return tauro;
	}

	public void setTauro(double tauro) {
		this.tauro = tauro;
	}

	public SimpleTask getTask() {
		return task;
	}

	public void setTask(SimpleTask task) {
		this.task = task;
	}

}
