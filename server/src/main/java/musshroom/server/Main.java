package musshroom.server;

import java.util.logging.Level;

import org.bridj.BridJ;

import musshroom.server.impl.Server;


public class Main {
	public static void main(String[] args) {
		BridJ.setMinLogLevel(Level.FINEST);
		Server server = new Server();
		server.setup(Server.DEFAULT_PORT);
		server.start();
	}
}
