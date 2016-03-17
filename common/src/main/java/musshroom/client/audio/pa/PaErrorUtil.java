package musshroom.client.audio.pa;

import portaudio.PortaudioLibrary.PaErrorCode;

/** convert integer to PA error code. */
public class PaErrorUtil {
	private PaErrorUtil() {
	}

	public static PaErrorCode getPaErrorCode(long err) {
		for (PaErrorCode p : PaErrorCode.values()) {
			if (p.value == err)
				return p;
		}
		return null;
	}
}
