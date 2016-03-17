package musshroom.client.audio.pa;

import musshroom.client.audio.IRecorder;
import musshroom.common.AudioConstants;
import musshroom.common.audio.RingBuffer;

import org.bridj.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import portaudio.PaDeviceInfo;
import portaudio.PaStreamCallbackTimeInfo;
import portaudio.PaStreamParameters;
import portaudio.PortaudioLibrary;
import portaudio.PortaudioLibrary.PaStreamCallback;

/**
 * This element start the voice recording. Data are saved in segments (small
 * buffers) within a ring buffer : it allows a 2nd process to pick a segment
 * (which is not currently being written) for processing (audio compression,
 * network, etc).
 */
public class Recorder implements IRecorder {
	private static final Logger LOG = LoggerFactory.getLogger(Recorder.class);
	private static final int CHANNEL_CNT = 1;
	/* use 16 bits signed (paInt16) since opus deal with them quit good */
	private static final long CHANNEL_SAMPLE_FORMAT = PA.PaSampleFormat.paInt16;
	private static final long SEGMENT_SIZE = AudioConstants.FRAME_SIZE * CHANNEL_CNT;
	// worker thread
	private RecorderWorker recorder;
	// device
	private int inDevice;
	private Pointer<PaDeviceInfo> inInfo;
	// state
	private boolean stopped;
	private boolean mute;
	// buffer
	private RingBuffer ring;

	public void setup(RingBuffer ring) {
		// choose device (for now just use default device)
		inDevice = PortaudioLibrary.Pa_GetDefaultInputDevice();		
		inInfo = PortaudioLibrary.Pa_GetDeviceInfo(inDevice);
		this.ring = ring;
	}

	@Override
	public void start() {
		recorder = new RecorderWorker();
		Thread t = new Thread(recorder, "Recorder Thread");
		t.setDaemon(false);
		t.start();
	}

	@Override
	public void stop() {
		stopped = true;
	}

	public boolean isMute() {
		return mute;
	}

	public void setMute(boolean mute) {
		this.mute = mute;
	}

	private class RecorderWorker implements Runnable {
		public void run() {
			// prepare stream parameters
			Pointer<PaStreamParameters> inParamPtr = Pointer.allocate(PaStreamParameters.class);
			PaStreamParameters inputParams = inParamPtr.get();
			inputParams.device(inDevice);
			inputParams.channelCount(CHANNEL_CNT); // microphone is mono
			inputParams.sampleFormat(CHANNEL_SAMPLE_FORMAT);
			inputParams.suggestedLatency(inInfo.get().defaultLowInputLatency());
			inputParams.hostApiSpecificStreamInfo(null);
			/*
			 * PA callback. This callback receive the raw audio data directly
			 * from portaudio. It should not block do slow processing (disk i/o,
			 * even context switching,.. already letting switch back to java is
			 * overkill.). In out case we copy the data in a ring buffer which
			 * is proceeded (encoding, sending over network) in another thread.
			 */
			PaStreamCallback callback = new PaStreamCallback() {
				@Override
				public int apply(Pointer<?> input, //
						Pointer<?> output, //
						long frameCount, Pointer<PaStreamCallbackTimeInfo> timeInfo, long statusFlags, Pointer<?> userData) {
					Pointer<Short> rptr = input.as(Short.class);
					if (!mute) {
						// Save Frames in ring buffer
						Pointer<Short> wptr = ring.getWriteBuffer();
						rptr.copyTo(wptr, SEGMENT_SIZE);
						// done with writing. unlock segment.
						ring.releaseWriteBuffer();
					}
					// do we stop now?
					return stopped ? PA.PaStreamCallback.paAbort : PA.PaStreamCallback.paContinue;
				}
			};
			// open stream
			Pointer<Pointer<?>> stream = Pointer.allocatePointer();
			long err = PortaudioLibrary.Pa_OpenStream(//
					stream, //
					inParamPtr, //
					null, //
					AudioConstants.SAMPLERATE, //
					AudioConstants.FRAME_SIZE, //
					PA.PaStreamFlag.paClipOff, //
					Pointer.pointerTo(callback), //
					ring.getNativeBuffer());
			if (err != PortaudioLibrary.PaErrorCode.paNoError.value()) {
				LOG.error("failed to open stream [" + PaErrorUtil.getPaErrorCode(err) + "]");
				return;
			}
			LOG.debug("RingBuffer recorder stream opened");
			// start stream
			err = PortaudioLibrary.Pa_StartStream(stream.get());
			if (err != PortaudioLibrary.PaErrorCode.paNoError.value()) {
				LOG.error("failed to start stream [" + PaErrorUtil.getPaErrorCode(err) + "]");
				return;
			}
			LOG.debug("RingBuffer recorder stream started");
			// wait until stream is closed (by callback return value)
			while (1 == PortaudioLibrary.Pa_IsStreamActive(stream.get())) {
				// sleep before checking again
				PortaudioLibrary.Pa_Sleep(500);
			}
			LOG.debug("RingBuffer recorder stream stopped");
			err = PortaudioLibrary.Pa_CloseStream(stream.get());
			if (err != PortaudioLibrary.PaErrorCode.paNoError.value()) {
				LOG.error("failed to close stream [" + PaErrorUtil.getPaErrorCode(err) + "]");
				return;
			}
			LOG.debug("RingBuffer recorder stream closed");
		}
	}
}
