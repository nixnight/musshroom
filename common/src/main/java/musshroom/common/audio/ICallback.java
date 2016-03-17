package musshroom.common.audio;

import musshroom.common.io.DataType;


public interface ICallback {
	void handleData(byte[] data, int offset, int size, DataType type);
}