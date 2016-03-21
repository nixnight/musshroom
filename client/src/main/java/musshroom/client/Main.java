package musshroom.client;

import java.io.File;
import java.util.logging.Level;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.bridj.BridJ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This util class is used to start a client.
 */
public class Main {
	private final static Logger LOG = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws Exception {
		BridJ.setMinLogLevel(Level.WARNING);
		// TODO parameter parsing
		Options options = new Options();
		options.addOption(Option.builder("h").required().desc("hostname or IP of musshroom server").hasArg().longOpt("hostname").build());
		options.addOption(Option.builder("p").required(false).desc("tcp port of musshroom server (default: 22)").hasArg().longOpt("port").build());
		options.addOption(Option.builder("i").required(false).desc("SSH identity file (default: scan ~/.ssh)").hasArg().longOpt("identity").build());
		options.addOption(Option.builder("u").required(false).desc("SSH username (default: musshroom)").hasArg().longOpt("username").build());
//		options.addOption(Option.builder().required(false).desc("disable auto probing all SSH key found in home (default: false)").hasArg(false).longOpt("no-autossh").build());
		options.addOption(Option.builder().required(false).desc("SSH identity file's password (default: interactive)").hasArg().longOpt("password").build());
		CommandLineParser parser = new DefaultParser();
		String hostname = null;
		int port = 22;
		File ifile = null;
		char[] pwd = null;
		String username = "musshroom";
//		boolean autoSSH = true;
		try {
			CommandLine cmd = parser.parse(options, args);
			hostname = cmd.getOptionValue('h');
			if (cmd.hasOption('p')) {
				port = Integer.parseInt(cmd.getOptionValue('p'));
			}
			if (cmd.hasOption('u')) {
				username = cmd.getOptionValue('u');
			}
			if (cmd.hasOption('i')) {
				String filePath = cmd.getOptionValue('i');
				ifile = new File(filePath);
				if (!ifile.exists() || !ifile.isFile() || !ifile.canRead()) {
					throw new RuntimeException("File [" + filePath + "] is not readable");
				}
			}
			if (cmd.hasOption("password")) {
				pwd = cmd.getOptionValue("password").toCharArray();
			}
//			autoSSH = !cmd.hasOption("no-autossh"); 
		} catch (Exception e) {
			System.err.println(e.getMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("musshroom-client", options);
			System.exit(1);
		}
//		// if no SSH identity file is done, we will try with AutoSSH (test all
//		// identity files found in ~/.ssh)
//		if (ifile == null && autoSSH) {
//			// TODO cache result in config file to avoid re-probing all keys
//			// each time.
//			AutoSsh as = new AutoSsh();
//			ifile = as.scan(hostname, port, username);
//		}
//		if (ifile == null) {
//			System.err.println("No valid SSH identity file was given as arg or has been found in ~/.ssh. Identity file is required. exit.");
//			System.exit(2);
//		}
		// TODO ask passphrase if necessary
		// connect localhost:22 with SSH using provided key and env username.
		SshConnection sshcon = new SshConnection(hostname, port, username, ifile, pwd);
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
