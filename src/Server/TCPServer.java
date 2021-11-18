package Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TCPServer extends Thread {

	private int port;
	private SimpleTask task;

	public TCPServer(int port, SimpleTask task) {
		this.task = task;
		this.port = port;
	}

	public void run() {
		try (ServerSocket serverSocket = new ServerSocket(this.port)) {

			System.out.println("Server is listening on port " + port);

			while (true) {
				Socket socket = serverSocket.accept();
				
				System.out.println("Client received");

				InputStream input = socket.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(input));
				String msg = reader.readLine();

				OutputStream output = socket.getOutputStream();
				PrintWriter writer = new PrintWriter(output, true);

				switch (msg) {
				case "getState": {
					HashMap<String, AtomicInteger> state = this.task.getState();
					String stTcp = "";
					for (String key : state.keySet()) {
						stTcp += key + ":" + state.get(key).get() + "$";
					}
					writer.println(stTcp);
					break;
				}
				default:
					throw new IllegalArgumentException("Unexpected command: " + msg);
				}

			}

		} catch (IOException ex) {
			System.out.println("Server exception: " + ex.getMessage());
			ex.printStackTrace();
		}
	}
}