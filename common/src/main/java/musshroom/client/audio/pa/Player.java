package musshroom.client.audio.pa;

import musshroom.client.audio.IPlayer;
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
 * buffers) within a ring buffer : it allows a 2nd thread to pick a segment
 * (which is not currently being written) for processing (audio compression,
 * network, etc).
 */
public class Player implements IPlayer {
	private static final Logger LOG = LoggerFactory.getLogger(Player.class);
	private static final long DEFAULT_FRAMESIZE = AudioConstants.FRAME_SIZE;
	private static final int CHANNEL_CNT = 1;
	/* use 16 bits signed (paInt16) since opus deal with them quit good */
	private static final long CHANNEL_SAMPLE_FORMAT = PA.PaSampleFormat.paInt16;
	private static final long SEGMENT_SIZE = DEFAULT_FRAMESIZE * CHANNEL_CNT;
	// worker thread
	private PlayerWorker recorder;
	// device
	private int outDevice;
	private Pointer<PaDeviceInfo> outInfo;
	// state
	private boolean stopped;
	private RingBuffer ringBuffer;
	private Pointer<Short> silent;
	private boolean mute;

	/*
	 * (non-Javadoc)
	 * 
	 * @see Audio.pa.IPlayer#setup(Audio.pa.RingBuffer)
	 */
	@Override
	public void setup(RingBuffer ringBuffer) {
		this.ringBuffer = ringBuffer;
		// choose device (for now just use default device)
		outDevice = PortaudioLibrary.Pa_GetDefaultOutputDevice();
		outInfo = PortaudioLibrary.Pa_GetDeviceInfo(outDevice);
		silent = Pointer.allocateShorts(DEFAULT_FRAMESIZE);
		silent.clearBytes(DEFAULT_FRAMESIZE * 2);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see Audio.pa.IPlayer#start()
	 */
	@Override
	public void start() {
		recorder = new PlayerWorker();
		Thread t = new Thread(recorder, "Player Thread");
		t.setDaemon(false);
		t.start();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see Audio.pa.IPlayer#stop()
	 */
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

	private class PlayerWorker implements Runnable {
		public void run() {
			// prepare stream parameters
			Pointer<PaStreamParameters> paramPtr = Pointer.allocate(PaStreamParameters.class);
			PaStreamParameters params = paramPtr.get();
			params.device(outDevice);
			params.channelCount(CHANNEL_CNT);
			params.sampleFormat(CHANNEL_SAMPLE_FORMAT);
			params.suggestedLatency(outInfo.get().defaultLowInputLatency());
			params.hostApiSpecificStreamInfo(null);
			PaStreamCallback callback = new PaStreamCallback() {
				@Override
				public int apply(Pointer<?> input, //
						Pointer<?> output, //
						long frameCount, //
						Pointer<PaStreamCallbackTimeInfo> timeInfo, //
						long statusFlags, //
						Pointer<?> userData) {
					Pointer<Short> wptr = output.as(Short.class);
					// copy data in wptr
					Pointer<Short> src = ringBuffer.getNextReadBuffer();
					if (src == null || mute) {
						src = silent;
					}
					src.copyTo(wptr, SEGMENT_SIZE);
					// do we stop now?
					return stopped ? PA.PaStreamCallback.paAbort : PA.PaStreamCallback.paContinue;
				}
			};
			// open stream
			Pointer<Pointer<?>> stream = Pointer.allocatePointer();
			long err = PortaudioLibrary.Pa_OpenStream(//
					stream, //
					null, //
					paramPtr, //
					AudioConstants.SAMPLERATE, //
					DEFAULT_FRAMESIZE, //
					PA.PaStreamFlag.paClipOff, //
					Pointer.pointerTo(callback), //
					ringBuffer.getNativeBuffer());
			if (err != PortaudioLibrary.PaErrorCode.paNoError.value()) {
				LOG.error("failed to open player stream [" + PaErrorUtil.getPaErrorCode(err) + "]");
				return;
			}
			LOG.debug("RingBuffer player stream opened");
			// start stream
			err = PortaudioLibrary.Pa_StartStream(stream.get());
			if (err != PortaudioLibrary.PaErrorCode.paNoError.value()) {
				LOG.error("failed to start player stream [" + PaErrorUtil.getPaErrorCode(err) + "]");
				return;
			}
			LOG.debug("RingBuffer player stream started");
			// wait until stream is closed (by callback return value)
			while (1 == PortaudioLibrary.Pa_IsStreamActive(stream.get())) {
				// sleep before checking again
				PortaudioLibrary.Pa_Sleep(500);
			}
			LOG.debug("RingBuffer player stream stopped");
			err = PortaudioLibrary.Pa_CloseStream(stream.get());
			if (err != PortaudioLibrary.PaErrorCode.paNoError.value()) {
				LOG.error("failed to close player stream [" + PaErrorUtil.getPaErrorCode(err) + "]");
				return;
			}
			LOG.debug("RingBuffer player stream closed");
		}
	}
}
