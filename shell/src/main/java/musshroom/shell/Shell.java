package musshroom.shell;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import musshroom.common.io.StringSerializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Shell is started from .ssh/config command option. It allows identity (based
 * on SSH public key used to log onto server) to be passed to server
 * process.
 * 
 * It will formward all byte received by SSH to musshroom server. It will also 
 * pipe the username right at the stream start.
 * 
 */
public class Shell {
	private final static Logger LOG = LoggerFactory.getLogger(Shell.class);
	private String username;
	private ByteForwarder sshToServer;
	private ByteForwarder serverToSsh;
	private Socket socket;

	public Shell(String username) {
		this.username = username;
		if (username.length() > 50) {
			// we check username in order to be able to pack its size into a
			// single byte. moreover username should be kept short.
			LOG.error("Username is too long");
			System.exit(0);
		}
	}

	private void start() {
		/**
		 * Shell connects the server and send the user name first. then Shell
		 * will forward all data from client to server.
		 */
		try {
			// server reader / writer
			socket = new Socket("localhost", 10000);
			// ssh stream reader / writer
			sshToServer = new ByteForwarder(username, System.in, socket.getOutputStream());
			Thread r = new Thread(sshToServer, "stdin_server");
			r.setDaemon(true);
			r.start();
			serverToSsh = new ByteForwarder(username, socket.getInputStream(), System.out);
			Thread w = new Thread(serverToSsh, "server_stdout");
			w.setDaemon(false);
			w.start();
			//
			LOG.info("Shell started for user [" + username + "]");
		} catch (Exception e) {
			LOG.error("Shell error", e);
		}
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("Usage : Shell <username>");
			System.exit(1);
		}
		Shell shell = new Shell(args[0]);
		shell.start();
	}

	private static class ByteForwarder implements Runnable {
		private byte[] buffer = new byte[1024];
		private BufferedInputStream bis;
		private BufferedOutputStream bos;
		private String username;

		public ByteForwarder(String username, InputStream in, OutputStream out) {
			this.username = username;
			bis = new BufferedInputStream(in);
			bos = new BufferedOutputStream(out);
		}

		public void run() {
			try {
				// initially send username
				byte[] utfUsername = username.getBytes("UTF-8");
				if (utfUsername.length > 100) {
					// not sure if 127 or 255 since byte is signed. but 100
					// should be large enough for a username anyway.
					LOG.error("Username is too long");
					System.exit(0);
				}
				StringSerializer.writeString(username, bos);
				// just forward I/O in incoming stream to destination stream
				int c = 0;
				while ((c = bis.read(buffer)) >= 0) {
					bos.write(buffer, 0, c);
					//LOG.info("copied [" + c + "] bytes");
					bos.flush();
				}
				LOG.info("exit forwarder [" + c + "]");
			} catch (Exception e) {
				LOG.debug("Input stream closed [" + username + "]", e);
				System.exit(0);
			}
		}
	}
}
