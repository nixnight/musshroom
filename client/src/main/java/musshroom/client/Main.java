package musshroom.client;

import java.io.Console;
import java.io.File;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This util class is used to start a client.
 */
public class Main {
	private final static Logger LOG = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws Exception {
		// hardcoded key and pwd for text purpose
		File idFile = new File(args[3]);
		// connect localhost:22 with SSH using provided key and env username.
		SshConnection sshcon = new SshConnection(args[0], Integer.parseInt(args[1]), args[2], idFile, args[4]);
		ClientConnection econ = new ClientConnection(sshcon);
		Client client = new Client(econ);
		//
		client.start();
		econ.start();
		//
		//
		Thread.sleep(500000);
		System.out.println("done");
		System.exit(0);
	}
}
