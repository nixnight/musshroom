package musshroom.common.audio;

import musshroom.client.audio.pa.PaErrorUtil;
import musshroom.common.AudioConstants;
import musshroom.common.IConnection;
import musshroom.common.io.DataType;
import opus.OpusLibrary;
import opus.OpusLibrary.OpusEncoder;

import org.bridj.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import portaudio.PortaudioLibrary.PaErrorCode;

public class DataEncoder {
	private static final Logger LOG = LoggerFactory.getLogger(DataEncoder.class);
	private SenderWorker worker;
	private boolean running;
	private Pointer<OpusEncoder> encoder;
	private IConnection connector;
	private RingBuffer ringBuffer;
	private double loudnessMax = Double.NaN;
	private double loudnessMin = Double.NaN;
	private double loudnessThreshold;
	private boolean skipFrame;
	private int loudnessDownLimitHysteresis = 0;
	private boolean loudnessAutoTune = true;

	public DataEncoder() {
	}

	public boolean frameSkipped() {
		return skipFrame;
	}

	public double getLoudnessThreshold() {
		return loudnessThreshold;
	}

	public void setLoudnessAutotune(boolean b) {
		this.loudnessAutoTune = b;
	}

	public boolean isLoudnessAutotune() {
		return loudnessAutoTune;
	}

	public void setup(RingBuffer ringBuffer, IConnection connector) {
		this.ringBuffer = ringBuffer;
		this.connector = connector;
		// encoder
		Pointer<Integer> error = Pointer.allocateInt();
		encoder = OpusLibrary.opus_encoder_create(AudioConstants.SAMPLERATE, 1, OpusLibrary.OPUS_APPLICATION_VOIP, error);
		PaErrorCode err = PaErrorUtil.getPaErrorCode(error.get());
		if (err != PaErrorCode.paNoError) {
			LOG.error("failed to create encoder : " + err);
		}
	}

	public void start() {
		worker = new SenderWorker();
		Thread t = new Thread(worker, "Sender Worker");
		t.setDaemon(false);
		running = true;
		t.start();
	}

	public void stop() {
		running = false;
	}

	private class SenderWorker implements Runnable {
		/*
		 * AUTO_MUTE_HYSTERESIS has been set empirically. It is related to frame
		 * size and sample rate and define how long we continue to send sound
		 * which does not reach the minimal loudness after someone speech. set
		 * it to 1-3 seconds in order to
		 */
		private static final int AUTO_MUTE_HYSTERESIS = 100;

		public void run() {
			try {
				LOG.debug("Start encoding...");
				Pointer<Byte> encDataBuffer = Pointer.allocateBytes(4000);
				//
				while (running) {
					// get next available segment in ring buffer
					Pointer<Short> segment = ringBuffer.getNextReadBuffer();
					if (segment == null) {
						LOG.error("Should never get a null pointer since buffer *should* be a blocking one. [blocking:" + ringBuffer.isBlocking() + "]");
						System.exit(1);
					}
					/*
					 * compute mean square root in order to find out if someone
					 * speak or not
					 */
					short[] data = segment.getShorts(AudioConstants.FRAME_SIZE);
					double meanSqr = 0;
					for (short s : data) {
						meanSqr += s * s;
					}
					/*
					 * Loudness auto tune (experimental) try to adapt the silent
					 * loudness level dynamically
					 */
					meanSqr = Math.sqrt(meanSqr / data.length);
					if (Double.isNaN(loudnessMax)) {
						loudnessMax = meanSqr;
					}
					if (Double.isNaN(loudnessMin)) {
						loudnessMin = meanSqr;
					}
					// System.out.println(">meanSqr>"+meanSqr);
					// System.out.println(">loudnessMin>"+loudnessMin);
					// System.out.println(">loudnessMax>"+loudnessMax);
					loudnessThreshold = (loudnessMax - loudnessMin) / 3 + loudnessMin;
					// System.out.println(">loudnessThreshold>"+loudnessThreshold);
					/**
					 * the adaptive part is when we adapt the loudness Max and
					 * Min based on the actual loudness. Actually we try to
					 * 'forget' past min and max in order to be able to shape
					 * changes in what is considered as speech or background
					 * noise. we first computed the mean value "(max+actual)/2"
					 * but it was too radical : min and max could be changed
					 * base on one short event. Now we over-emphasis min and
					 * max, so that change must be constant over several frames.
					 */
					if (meanSqr >= loudnessThreshold) {
						loudnessMax = (loudnessMax * 5 + meanSqr) / 6;
					} else {
						loudnessMin = (loudnessMin * 5 + meanSqr) / 6;
					}
					/*
					 * based on computed/adapted loudnessThreshold, we decide if
					 * this frame is speech or silence.
					 */
					boolean silent = meanSqr < loudnessThreshold && loudnessAutoTune;
					// send it
					if (silent && loudnessDownLimitHysteresis <= 0) {
						/*
						 * do not send data to client any more if no speech has
						 * been detected
						 */
						skipFrame = true;
					} else {
						if (silent) {
							// fade out to silent to avoid hearing when we
							// switch to silent mode.
							double f = loudnessDownLimitHysteresis / (double) AUTO_MUTE_HYSTERESIS;
							// System.out.println(">> " + f);
							for (int i = 0; i < data.length; i++) {
								data[i] = (short) (data[i] * f);
							}
							segment.setShorts(data);
							loudnessDownLimitHysteresis--;
						} else {
							// loudness to normal value
							loudnessDownLimitHysteresis = AUTO_MUTE_HYSTERESIS;
						}
						// encode it
						int encDataSize = OpusLibrary.opus_encode(encoder, segment, AudioConstants.FRAME_SIZE, encDataBuffer, 4000);
						byte[] packet = encDataBuffer.getBytes(encDataSize);
						// check if still running before sending it
						if (!running) {
							break;
						}
						if (connector.isConnected()) {
							connector.sendData(packet, 0, packet.length, DataType.VOICE);
							skipFrame = true;
						} else {
							skipFrame = false;
						}
					}
				}
			} catch (Exception e) {
				LOG.error("Failed to encode data", e);
			}
		}
	}
}
