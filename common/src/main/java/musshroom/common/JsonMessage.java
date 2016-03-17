package musshroom.common;

import java.util.UUID;

public class JsonMessage {
	public static final byte REQUEST = 0x0;
	public static final byte RESPONSE = 0x1;
	public static final byte ERROR = 0x2;
	public String method;
	public String uid = UUID.randomUUID().toString();
	public byte type = REQUEST;
	public byte[] data;
	public String dataType;

	public JsonMessage(String method) {
		this.method = method;
	}

	public JsonMessage() {
	}

	/**
	 * typical response will re-use uid of request.
	 */
	public JsonMessage(String uid, String method) {
		this.uid = uid;
		this.method = method;
		this.type = RESPONSE;
	}

	public static JsonMessage createErrorMsg(String uid, String error) {
		JsonMessage m = new JsonMessage(uid, error);
		m.type = ERROR;
		return m;
	}
}
