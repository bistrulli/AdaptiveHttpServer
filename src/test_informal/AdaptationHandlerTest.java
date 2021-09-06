package test_informal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.exception.NumberIsTooLargeException;

public class AdaptationHandlerTest {
	public static void main(String[] args) throws UnknownHostException, IOException {
		try (Socket clientSocket = new Socket("localhost", 2000)) {
			PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			UniformIntegerDistribution ui=new UniformIntegerDistribution(3, 32);
			out.println(String.format("action,%d,%d", 1,ui.sample()));
			String resp = in.readLine();
			System.out.println(resp);
		} catch (NumberIsTooLargeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
