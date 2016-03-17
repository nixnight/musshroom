package musshroom.common.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class used to serialize strings
 */
public final class StringSerializer {
	private final static Logger LOG = LoggerFactory.getLogger(StringSerializer.class);

	private StringSerializer() {
	}

	public static void writeString(String string, OutputStream os) throws IOException {
		byte[] utfUsername = string.getBytes("UTF-8");
		if (utfUsername.length > 100) {
			// not sure if 127 or 255 since byte is signed. but 100
			// should be large enough for a username anyway.
			LOG.error("Username is too long");
			System.exit(0);
		}
		LOG.info("write String [" + new String(utfUsername) + "]");
		// send string length
		os.write(utfUsername.length);
		// send string content
		os.write(utfUsername);
		os.flush();
	}

	public static String readString(InputStream is) throws IOException {
		int userIdLenght = is.read();
		byte[] utf = new byte[userIdLenght];
		is.read(utf);
		return new String(utf, "UTF-8");
	}
}
