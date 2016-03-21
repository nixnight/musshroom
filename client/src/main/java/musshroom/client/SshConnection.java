package musshroom.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import musshroom.common.io.DataInputStream;
import musshroom.common.io.DataOutputStream;
import musshroom.common.io.StringSerializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ConfigRepository;
import com.jcraft.jsch.Hacky;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.OpenSSHConfig;
import com.jcraft.jsch.Session;

/**
 * encapsulate basic SSH connection and provide facilities to exchange Data.
 */
public class SshConnection {
	private final static Logger LOG = LoggerFactory.getLogger(SshConnection.class);
	private File idFile;
	private String hostname;
	private char[] passphrase;
	private String username;
	private int sshPort;
	private DataInputStream input;
	private DataOutputStream output;
	private Channel shellChannel;

	public SshConnection(String hostname, int sshPort, String username, File idFile, char[] passphrase) {
		this.hostname = hostname;
		this.sshPort = sshPort;
		this.username = username;
		this.idFile = idFile;
		this.passphrase = passphrase;
	}

	public void connect() throws JSchException, IOException {
		JSch jsch = new JSch();
		// read SSH config
		File sshDir = new File(new File(System.getProperty("user.home")), ".ssh");
		File config = new File(sshDir, "config");
		if (config.exists()) {
			LOG.debug("Load SSH config file [{}]", config.getAbsolutePath());
			ConfigRepository configRepository = OpenSSHConfig.parseFile(config.getAbsolutePath());
			jsch.setConfigRepository(configRepository);
		}
		//
		LOG.debug("Connect [{}@{}:{}]", username, hostname, sshPort);
		Session session = jsch.getSession(username, hostname, sshPort);
		Hacky.hacky(session, username);
		session.setUserInfo(new SshUserInfo(passphrase, idFile));
		// something does not work when ServerAliveInterval is set in ssh
		// config file so we clear it there
		session.setServerAliveInterval(0);
		// add local key found in ~/.ssh
		if (idFile == null) {
			for (File f : sshDir.listFiles()) {
				if (f.getName().endsWith(".pub")) {
					// public key found -> check if private key available
					String abs = f.getAbsolutePath();
					File privkey = new File(abs.substring(0, abs.length() - 4));
					if (privkey.exists()) {
						jsch.addIdentity(privkey.getAbsolutePath());
					}
				}
			}
		} else {
			// only key specified by user
			jsch.addIdentity(idFile.getAbsolutePath());
		}
		// connect
		session.connect();
		//
		shellChannel = session.openChannel("shell");
		shellChannel.connect();
		// open channel
		InputStream is = shellChannel.getInputStream();
		// get userId configured on the server for our public key
		String userId = null;
		try {
			userId = StringSerializer.readString(is);
		} catch (Exception e) {
			throw new RuntimeException("Failed to parse userId received from server", e);
		}
		if (userId == null || userId.length() == 0) {
			LOG.error("Received bad username. close connection.");
			close();
			return;
		}
		LOG.debug("Got username from server [" + userId + "]");
		//
		input = new DataInputStream(is);
		output = new DataOutputStream(shellChannel.getOutputStream());
	}

	public void close() {
		shellChannel.disconnect();
	}

	public DataInputStream getInput() {
		return input;
	}

	public DataOutputStream getOutput() {
		return output;
	}
}
