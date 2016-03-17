package musshroom.common.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataInputStream {
	private static final Logger LOG = LoggerFactory.getLogger(DataInputStream.class);
	private InputStream input;
	private byte[] buffer;
	private boolean terminated;

	public DataInputStream(InputStream is) {
		this.input = new BufferedInputStream(is);
		buffer = new byte[16000];
	}

	public boolean isTerminated() {
		return terminated;
	}

	public Buffer next() throws IOException {
		int r = 0;
		int size = 0;
		int rAcc = 0;
		int x = input.read();
		if (x < 0) {
			LOG.debug("End of stream");
			terminated = true;
			return null;
		} else if (x >= DataType.values().length) {
			LOG.debug("Invalid type [{}]", x);
			terminated = true;
			return null;
		}
		// read packet type
		DataType type = DataType.values()[x];
		// read packet size
		size = input.read() << 8;
		size = size | input.read();
		if (size > buffer.length) {
			LOG.error("packet to big for our buffer [" + size + " > " + buffer.length + "]. Abort");
			terminated = true;
			return null;
		} else if (size == -1) {
			LOG.debug("End of stream");
			terminated = true;
			return null;
		} else if (size < 0) {
			LOG.error("Invalid declared payload size [" + size + "]. Abort.");
			terminated = true;
			return null;
		}
		// read timestamp
		long ts = ((long)input.read()) << 54;
		ts = ts | (((long)input.read()) << 48);
		ts = ts | (((long)input.read()) << 40);
		ts = ts | (((long)input.read()) << 32);
		ts = ts | (((long)input.read()) << 24);
		ts = ts | (((long)input.read()) << 16);
		ts = ts | (((long)input.read()) << 8);
		ts = ts | input.read();
		// check latency
		long latency = System.currentTimeMillis()-ts;
		//System.out.println("LATENCY: "+latency);
		// read payload
		rAcc = r = input.read(buffer, 0, size);
		while (rAcc < size && r > 0) {
			// payload not complete yet. re-read from stream
			r = input.read(buffer, rAcc, size - rAcc);
			rAcc += r;
		}
		// check if stream has been closed
		terminated = r < 0;
		// return payload
		return new Buffer(buffer, 0, size, type);
	}

	public static class Buffer {
		private final byte[] data;
		private final int offset;
		private final DataType type;
		private final int size;

		public Buffer(byte[] data, int offset, int size, DataType type) {
			this.data = data;
			this.offset = offset;
			this.size = size;
			this.type = type;
		}

		public byte[] getData() {
			return data;
		}

		public String getDataAsString() {
			return new String(data, offset, size);
		}

		public int getOffset() {
			return offset;
		}

		public DataType getType() {
			return type;
		}

		public int getSize() {
			return size;
		}
	}

	public void close() {
		try {
			input.close();
			LOG.debug("close()");
		} catch (Exception e) {
			LOG.error("Failed to close stream", e);
		}
	}
}
