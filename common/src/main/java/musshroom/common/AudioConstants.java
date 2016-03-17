package musshroom.common;

public interface AudioConstants {
	// 120 does not work on all hardware (lot of : ALSA lib
	// pcm.c:7843:(snd_pcm_recover) underrun occurred)
	int SAMPLERATE = 48000; // was 48000
	int FRAME_SIZE = SAMPLERATE / 100;
	int FRAME_BYTE_SIZE = FRAME_SIZE * 2; // 2 bytes/sample
}
