package Server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UDPServer extends Thread {
	private DatagramSocket udpSocket;
	private int port;
	private SimpleTask task;

	public UDPServer(int port, SimpleTask task) {
		try {
			this.port = port;
			this.udpSocket = new DatagramSocket(this.port);
			this.task = task;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void run() {
		try {
			String msg;

			while (true) {
				byte[] buf = new byte[1024];
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				this.udpSocket.receive(packet);
				msg = new String(packet.getData()).trim();

				InetAddress clientAddress = packet.getAddress();
				int clientPort = packet.getPort();

				switch (msg) {
				case "getState": {
					HashMap<String, AtomicInteger> state = this.task.getState();
					String stUdp = "";
					for (String key : state.keySet()) {
						stUdp += key + ":" + state.get(key).get() + "$";
					}
					DatagramPacket reply = new DatagramPacket(stUdp.getBytes(), stUdp.getBytes().length, clientAddress,
							clientPort);
					udpSocket.send(reply);
					break;
				}
				default:
					throw new IllegalArgumentException("Unexpected command: " + msg);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}