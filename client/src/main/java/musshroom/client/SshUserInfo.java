package musshroom.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

public class SshUserInfo implements UserInfo, UIKeyboardInteractive {
	private final static Logger LOG = LoggerFactory.getLogger(SshUserInfo.class);
	private String passphrase;

	public SshUserInfo(String passphrase) {
		this.passphrase = passphrase;
	}

	public String getPassword() {
		LOG.debug("getPassword()");
		return null;
	}

	public boolean promptYesNo(String str) {
		LOG.debug("promptYesNo({}) [return Yes is default]",str);
		return true;
	}

	public String getPassphrase() {
		LOG.debug("getPassphrase()");
		return passphrase;
	}

	public boolean promptPassphrase(String message) {
		LOG.debug("promptPassphrase([{}])", message);
		return true;
	}

	public boolean promptPassword(String message) {
		LOG.debug("promptPassword({})",message);
		return true;
	}

	public void showMessage(String message) {
		LOG.debug("showMessage({})",message);
	}

	public String[] promptKeyboardInteractive(String destination, String name, String instruction, String[] prompt, boolean[] echo) {
		LOG.debug("promptKeyboardInteractive(["+destination+"],["+instruction+"])");
		return null;
	}
}