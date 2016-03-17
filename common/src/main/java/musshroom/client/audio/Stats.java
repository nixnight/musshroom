package musshroom.client.audio;

import musshroom.common.AudioConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Stats {
	private static final Logger LOG = LoggerFactory.getLogger(Stats.class);
	private long t0 = -1;
	private long t1 = 0;
	private long hits;
	private String id;

	public Stats(String id) {
		this.id = id;
	}

	public long getHits() {
		return hits;
	}

	public void trigger() {
		if (t0 == -1) {
			t0 = System.currentTimeMillis();
			return;
		}
		hits++;
		//
		final long now = System.currentTimeMillis();
		long dt = now - t1;
		if (dt > 60000) {
			// display stats
			t1 = now;
			long sdt = now - t0;
			if (sdt > 0) {
				long x = 1000 * hits * AudioConstants.FRAME_SIZE / sdt;
				LOG.debug("Statistics [" + id + "] [" + x + "]");
			}
		}
	}
}
