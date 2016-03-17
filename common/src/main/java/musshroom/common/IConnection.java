package musshroom.common;

import java.io.IOException;

import musshroom.common.io.DataType;

public interface IConnection {
	public abstract void sendData(byte[] data, DataType type) throws IOException;

	public abstract void sendData(byte[] data, int offset, int length, DataType type) throws IOException;

	public abstract boolean isConnected();
}