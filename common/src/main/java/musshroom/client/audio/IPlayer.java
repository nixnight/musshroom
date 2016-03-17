package musshroom.client.audio;

import musshroom.common.audio.RingBuffer;

public interface IPlayer {
	public abstract void setup(RingBuffer ringBuffer);

	public abstract void start();

	public abstract void stop();
}