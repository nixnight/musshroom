package musshroom.common.io;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataOutputStream {
	private static final Logger LOG = LoggerFactory.getLogger(DataOutputStream.class);
	private OutputStream output;

	public DataOutputStream(OutputStream out) {
		this.output = new BufferedOutputStream(out);
	}

	public void send(byte[] data, int offset, int size, DataType type) throws IOException {
		output.write(0xff & type.ordinal());
		// send payload size
		output.write(0xff & (size >> 8));
		output.write(0xff & size);
		// timestamp
		long ts = System.currentTimeMillis();
		output.write((int) (0xff & (ts >> 56)));
		output.write((int) (0xff & (ts >> 48)));
		output.write((int) (0xff & (ts >> 40)));
		output.write((int) (0xff & (ts >> 32)));
		output.write((int) (0xff & (ts >> 24)));
		output.write((int) (0xff & (ts >> 16)));
		output.write((int) (0xff & (ts >> 8)));
		output.write((int) (0xff & ts));
		// send payload
		output.write(data, offset, size);
		// flush
		output.flush();
	}

	public void close() {
		try {
			LOG.debug("close()");
			output.close();
		} catch (Exception e) {
			LOG.error("Faile to close stream", e);
		}
	}

	public void send(byte[] data, DataType type) throws IOException {
		send(data, 0, data.length, type);
	}
}
