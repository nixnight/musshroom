package musshroom.client.audio.pa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import portaudio.PortaudioLibrary;

/** PA Constants */
public class PA {
	private static final Logger LOG = LoggerFactory.getLogger(PA.class);

	private PA() {
	}

	public static final void initPortAudio() {
		int err = PortaudioLibrary.Pa_Initialize();
		if (err != PortaudioLibrary.PaErrorCode.paNoError.value()) {
			throw new RuntimeException("failed to init port audio (" + PaErrorUtil.getPaErrorCode(err) + ")");
		}
	}

	public static final void disposePortAudio() {
		int err = PortaudioLibrary.Pa_Terminate();
		if (err != PortaudioLibrary.PaErrorCode.paNoError.value()) {
			LOG.warn("Failed to terminate PortAudioLibrary gracefully (" + PaErrorUtil.getPaErrorCode(err) + ")");
		}
	}

	public interface PaSampleFormat {
		long paFloat32 = 0x00000001;
		long paInt16 = 0x00000008;
	}

	public interface PaStreamCallback {
		int paContinue = 0;
		int paComplete = 1;
		int paAbort = 2;
	}

	public interface PaStreamFlag {
		long paClipOff = 0x00000001;
	}
}