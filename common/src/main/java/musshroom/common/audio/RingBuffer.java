package musshroom.common.audio;

import musshroom.client.audio.Stats;
import musshroom.common.AudioConstants;

import org.bridj.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RingBuffer purpose is to hold PCM (raw, uncompressed) voice frames. This is
 * necessary since portaudio callback MUST not block. During recording, frames
 * arrives fixed interval (SampleRate/frame size) through the callback that need
 * to return without waiting that encoding and network stuff. During playback,
 * frames should be ready (buffering) so that callback never waits for new data.
 */
public class RingBuffer {
	// constants
	private static final int SEGSIZE = AudioConstants.FRAME_SIZE;
	private static final Logger LOG = LoggerFactory.getLogger(RingBuffer.class);
	// buffer : native & OO
	private Pointer<Short> segNative;
	private Segment[] segObjects;
	// firstIdx -> next segment to read
	private int firstIdx = 0;
	// nextIdx -> nest segment to write
	private int nextIdx = 0;
	/* buffering flag is used when .. buffering */
	private boolean buffering;
	private final int ringSize;
	private int confortZone;
	//
	private final boolean blocking;
	private int bufferSize;
	//
	private Stats inStats;
	private Stats outStats;
	private String logId;

	public RingBuffer(int ringSize, int confortZone, boolean blocking) {
		this(ringSize, confortZone, blocking, "");
	}

	/**
	 * 
	 * @param ringSize
	 * @param confortZone
	 * @param blocking
	 *            if blocking is set to true, getNextReadBuffer() will block
	 *            until data is available. if set to false, an empty frame will
	 *            be returned.
	 */
	public RingBuffer(int ringSize, int confortZone, boolean blocking, String logId) {
		this.ringSize = ringSize;
		this.confortZone = confortZone;
		this.blocking = blocking;
		this.logId = logId;
		// init buffer
		segNative = Pointer.allocateShorts(SEGSIZE * ringSize);
		segObjects = new Segment[ringSize];
		for (int i = 0; i < segObjects.length; i++) {
			segObjects[i] = new Segment(i);
		}
		// stats
		LOG.debug("RingBuffer created [" + logId + "] [blocking:" + blocking + "] [confort:" + confortZone + "] [size:" + ringSize + "]");
		inStats = new Stats(logId + "-in");
		outStats = new Stats(logId + "-out");
	}

	public Pointer<Short> getNativeBuffer() {
		return segNative;
	}

	/* compute effective buffer size */
	private final void updateBufferSizeVar() {
		// 0 1 2 3 4 5 6 7 8 9
		// . . f * * * n . . . = 6-2 = 4
		// . . fn. . . . . . . = 2-2 = 0
		// * n . . . . f * * * = 10-(6-1) = 5
		bufferSize = firstIdx <= nextIdx ? nextIdx - firstIdx : ringSize - (firstIdx - nextIdx);
	}

	public boolean isEmpty() {
		return nextIdx == firstIdx;
	}

	public synchronized Pointer<Short> getWriteBuffer() {
		return segObjects[nextIdx].ptr;
	}

	/**
	 * Is used to detect if enough buffer to mix channel on the server
	 */
	public int getBufferSize() {
		return bufferSize;
	}

	public synchronized Pointer<Short> getNextReadBuffer() {
		if (buffering) {
			if (bufferSize >= confortZone) {
				// enough buffering :-)
				buffering = false;
			} else {
				// keep buffering :-(
				return null;
			}
		}
		//
		if (nextIdx == firstIdx) {
			// buffer is empty
			if (blocking) {
				try {
					/*
					 * in blocking mode we should wait to have data before
					 * returning.
					 */
					while (nextIdx == firstIdx) {
						/*
						 * will not wait so long since notify is called as soon
						 * as data is available.
						 */
						this.wait(1);
					}
				} catch (InterruptedException e) {
					LOG.error("Failed", e);
				}
			} else {
				/*
				 * in not-blocking mode, we switch to buffering mode and we will
				 * return empty frame as long as buffer is not full again.
				 */
				buffering = true;
				return null;
			}
		}
		// increment 'firstIdx'
		int f = firstIdx;
		firstIdx = (firstIdx + 1) % ringSize;
		// compute new buffer size
		updateBufferSizeVar();
		// statistics
		outStats.trigger();
		// return
		return segObjects[f].ptr;
	}

	public synchronized void releaseWriteBuffer() {
		// increment pointer "next"
		nextIdx = (nextIdx + 1) % ringSize;
		if (nextIdx == firstIdx) {
			// no more place. drop 'oldest' data
			// by incrementing firstIdx
			firstIdx = (firstIdx + 1) % ringSize;
			/* TODO: optimize buffer tracking and dynamic resizing */
		}
		updateBufferSizeVar();
		// unlock
		if (blocking) {
			this.notifyAll();
		}
		inStats.trigger();
	}

	/**
	 * Point on a segment in the native buffer.
	 */
	private class Segment {
		Pointer<Short> ptr;

		public Segment(int idx) {
			ptr = segNative.next(SEGSIZE * idx);
		}
	}

	public int getMaxSize() {
		return ringSize;
	}

	public boolean isBlocking() {
		return blocking;
	}
}
