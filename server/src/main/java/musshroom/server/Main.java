package musshroom.server;

import musshroom.server.impl.Server;


public class Main {
	public static void main(String[] args) {
		Server server = new Server();
		server.setup(Server.DEFAULT_PORT);
		server.start();
	}
}
