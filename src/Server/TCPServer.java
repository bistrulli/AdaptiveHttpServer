package Server;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
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
		PrintWriter writer = null;
		try (ServerSocket serverSocket = new ServerSocket(this.port)) {

			Socket socket = serverSocket.accept();

			OutputStream output = socket.getOutputStream();
			writer = new PrintWriter(output, true);
			DataInputStream reader = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

			writer.println("connected");

			while (true) {
				String msg = null;
				while ((msg = reader.readLine()) == null) {
				}
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

		} catch (Exception ex) {
			System.out.println("Server exception: " + ex.getMessage());
			ex.printStackTrace();
		} finally {
			writer.close();
		}
	}
}