package musshroom.client;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

/**
 * This utility try to connect server with all SSH's key found in $HOME/.ssh/.
 * If passphrase is asked, it assume that this key is valid.
 */
@Deprecated
public class AutoSsh {
	private final static Logger LOG = LoggerFactory.getLogger(AutoSsh.class);

	public AutoSsh() {
	}

	public static void main(String[] args) {
		AutoSsh a = new AutoSsh();
		a.scanold("localhost", 22, "musshroom");
	}

	public File scanold(String host, int port, String username) {
		//
		String home = System.getProperty("user.home");
		File hFile = new File(home);
		File sFile = new File(hFile, ".ssh");
		Executor exec = Executors.newCachedThreadPool();
		int probes = 0;
		HashMap<File, Boolean> failed = new HashMap<>();
		System.out.println(">> "+sFile.getAbsolutePath());
		for (File f : sFile.listFiles()) {
			System.out.println("   >> "+f.getAbsolutePath());
			if (f.getName().endsWith(".pub")) {
				// public key found -> check if private key available
				String abs = f.getAbsolutePath();
				File privkey = new File(abs.substring(0, abs.length() - 4));
				if (privkey.exists()) {
//					System.out.println("found [" + privkey.getAbsolutePath() + "]");
					probes++;
					exec.execute(new CheckKey(privkey, host, port, username, failed));
				}
			}
		}
		while (failed.size() != probes) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		LOG.debug("Ran [" + probes + "] probes.");
		for (File probe : failed.keySet()) {
			if (!failed.get(probe)) {
				// found sucessfull key
				LOG.debug("Found working key [" + probe.getName() + "]");
				return probe;
			}
		}
		return null;
	}

	private class CheckKey implements Runnable {
		private File privkey;
		private int port;
		private String hostname;
		private String username;
		private HashMap<File, Boolean> failed;

		public CheckKey(File privkey, String hostname, int port, String username, HashMap<File, Boolean> failed) {
			this.privkey = privkey;
			this.hostname = hostname;
			this.port = port;
			this.username = username;
			this.failed = failed;
		}

		@Override
		public void run() {
			JSch jsch = new JSch();
			TestInfo t = new TestInfo();
			try {
				jsch.addIdentity(privkey.getAbsolutePath());
				Session session = jsch.getSession(username, hostname, port);
				session.setUserInfo(t);
				session.connect();
				session.disconnect();
			} catch (Exception e) {
				// nothing since we do not provide password or passphrase
			} finally {
				LOG.debug("Auth with [" + privkey.getName() + "] failed? : " + t.failed);
				synchronized (failed) {
					failed.put(privkey, t.failed);
				}
			}
		}
	}

	private class TestInfo implements UserInfo {
		public boolean failed = true;

		public TestInfo() {
		}

		@Override
		public String getPassword() {
			/**
			 * if password is asked, it is because the key was not accepted
			 */
			return null;
		}

		@Override
		public String getPassphrase() {
			/**
			 * if passphrase is asked, it is because key is accepted.
			 */
			failed = false;
			return null;
		}

		@Override
		public boolean promptPassphrase(String arg0) {
			return true;
		}

		@Override
		public boolean promptPassword(String arg0) {
			return true;
		}

		@Override
		public boolean promptYesNo(String arg0) {
			return true;
		}

		@Override
		public void showMessage(String arg0) {
		}
	}
}
