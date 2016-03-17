package test.musshroom.integration;

import musshroom.client.Main;
import musshroom.server.impl.Server;

public class DemoServer {
	public DemoServer() {
	}

	private void start() throws Exception {
		Server server = new Server();
		server.setup(Server.DEFAULT_PORT);
		server.start();
		System.out.println("Server start.");
		Thread.sleep(1500);
		System.out.println("Client start.");
		Main.main(new String[] { "localhost", "22", "musshroom", "src/etc/dev001", "010101" });
	}

	public static void main(String[] args) throws Exception {
		DemoServer d = new DemoServer();
		d.start();
	}
}
