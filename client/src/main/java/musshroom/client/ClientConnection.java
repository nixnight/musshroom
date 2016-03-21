package musshroom.client;

import java.io.IOException;

import musshroom.common.IConnection;
import musshroom.common.io.DataInputStream;
import musshroom.common.io.DataInputStream.Buffer;
import musshroom.common.io.DataType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.JSchException;

/**
 * Encapsulate SSH connection and provide callback hook for incoming packets,
 * etc. (?)
 */
public class ClientConnection implements Runnable, IConnection {
	private final static Logger LOG = LoggerFactory.getLogger(ClientConnection.class);
	private SshConnection sshcon;
	private boolean connected;
	private IDataCallback datacb;
	private boolean debug;

	public ClientConnection(SshConnection sshcon) {
		this.sshcon = sshcon;
	}

	public void start() {
		Thread t = new Thread(this, "ClientConnection");
		t.setDaemon(true);
		t.start();
	}

	public boolean isConnected() {
		return connected;
	}

	/* (non-Javadoc)
	 * @see musshroom.client.IConnection#sendData(byte[], musshroom.common.io.DataType)
	 */
	@Override
	public void sendData(byte[] data, DataType type) throws IOException {
		sshcon.getOutput().send(data, 0, data.length, type);
	}

	/* (non-Javadoc)
	 * @see musshroom.client.IConnection#sendData(byte[], int, int, musshroom.common.io.DataType)
	 */
	@Override
	public void sendData(byte[] data, int offset, int length, DataType type) throws IOException {
		sshcon.getOutput().send(data, offset, length, type);
	}

	@Override
	public void run() {
		try {
			LOG.debug("connect..");
			sshcon.connect();
			LOG.info("connected.");
			connected = true;
			// start listening
			LOG.debug("listen packets...");
			DataInputStream din = sshcon.getInput();
			Buffer dpk = null;
			while ((dpk = din.next()) != null) {
				switch (dpk.getType()) {
				case JSON:
				case VOICE:
					if (datacb != null) {
						datacb.handle(dpk, dpk.getType());
					}
					break;
				default:
					LOG.debug("packet not supported [" + dpk.getType() + "]");
					break;
				}
			}
			LOG.debug("Connection exited : null packet received.");
		} catch (JSchException e) {
			LOG.error("SSH connection failed", e);
		} catch (IOException e) {
			LOG.error("Client IOError", e);
		}
	}

	public interface IDataCallback {
		void handle(Buffer dpk, DataType type);
	}

	public void setDataCallbak(IDataCallback datacb) {
		this.datacb = datacb;
	}

}
