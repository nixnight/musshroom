package musshroom.common.audio;

import musshroom.client.audio.pa.PaErrorUtil;
import musshroom.common.AudioConstants;
import musshroom.common.io.DataType;
import opus.OpusLibrary;
import opus.OpusLibrary.OpusDecoder;

import org.bridj.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import portaudio.PortaudioLibrary.PaErrorCode;

public class DataDecoder implements ICallback {
	private static final Logger LOG = LoggerFactory.getLogger(DataDecoder.class);
	private Pointer<OpusDecoder> decoder;
	private Pointer<Byte> buffer;
	private RingBuffer ringBuffer;
	private int bufferSize;

	public DataDecoder() {
	}

	public void setup(RingBuffer ringBuffer) {
		// encoder
		Pointer<Integer> error = Pointer.allocateInt();
		decoder = OpusLibrary.opus_decoder_create(AudioConstants.SAMPLERATE, 1, error);
		PaErrorCode err = PaErrorUtil.getPaErrorCode(error.get());
		if (err != PaErrorCode.paNoError) {
			LOG.error("failed to create decoder : " + err);
		}
		// setup ring buffer for incoming packets
		this.ringBuffer = ringBuffer;
	}

	@Override
	public void handleData(byte[] data, int offset, int size, DataType type) {
		// only accept voice data
		if (type != DataType.VOICE)
			return;
		// lazy create/extend native buffer
		if (buffer == null || size > bufferSize) {
			bufferSize = size;
			buffer = Pointer.allocateBytes(bufferSize);
			LOG.debug("handleData() buffer allocated [" + bufferSize + "]");
		}
		// copy data in native buffer
		buffer.setBytesAtOffset(0, data, 0, size);
		// decode using Opus. Push decoded PCM data in ring buffer
		Pointer<Short> pcm = ringBuffer.getWriteBuffer();
		OpusLibrary.opus_decode(decoder, buffer, size, pcm, AudioConstants.FRAME_SIZE, 0);
		//
		ringBuffer.releaseWriteBuffer();
	}
}
