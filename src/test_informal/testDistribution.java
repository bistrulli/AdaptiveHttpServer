package test_informal;

import java.util.Iterator;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.stat.StatUtils;

public class testDistribution {
	public static void main(String[] args) {
		ExponentialDistribution dist=new ExponentialDistribution(2.0);
		double[] arr=new double[2000];
		for (int i = 0; i < arr.length; i++) {
			arr[i]=dist.sample();
		}
		System.out.println(StatUtils.mean(arr));
	}
}
