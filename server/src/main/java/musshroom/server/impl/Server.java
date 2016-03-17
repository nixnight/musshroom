package musshroom.server.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import musshroom.common.AudioConstants;
import musshroom.common.io.DataInputStream.Buffer;
import musshroom.common.io.DataType;
import musshroom.server.IServer;
import opus.OpusLibrary;

import org.bridj.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server implements IServer {
	private final static Logger LOG = LoggerFactory.getLogger(Server.class);
	public static final int DEFAULT_PORT = 10000;
	private int port;
	private ConnectionListener listener;
	private ArrayList<ConnectionHandler> connections = new ArrayList<>();
	private List<ConnectionHandler> connectionsCopy = new ArrayList<>();
	private Mixer mixer;

	public Server() {
		mixer = new Mixer();
	}

	public void setup(int port) {
		this.port = port;
	}

	public void setup() {
		this.port = DEFAULT_PORT;
	}

	public void start() {
		LOG.debug("Server starting..");
		listener = new ConnectionListener(port, this);
		Thread t = new Thread(listener, "Server");
		t.setDaemon(false);
		t.start();
		// start mixer
		mixer.start();
	}

	public void stop() throws IOException {
		listener.close();
	}

	private class Mixer extends Thread {
		private ArrayList<short[]> frames = new ArrayList<short[]>();

		public Mixer() {
			super("Mixer");
			setDaemon(false);
		}

		@Override
		public void run() {
			try {
				// initialize initial empty buffer
				short[] empty = new short[AudioConstants.FRAME_SIZE];
				for (int x = 0; x < empty.length; x++) {
					empty[x] = 0;
				}
				//
				Pointer<Short> frameNative = Pointer.allocateShorts(AudioConstants.FRAME_SIZE);
				Pointer<Byte> encDataBuffer = Pointer.allocateBytes(4000);
				ByteBuffer byteBuffer = ByteBuffer.allocate(4000);
				short[] mix = new short[AudioConstants.FRAME_SIZE];
				int sum = 0;
				// regulation metrics
				// frame = 480 samples at 48000 Hz
				// frame = (1/48000)*480 = 1/100 = 10ms
				long frameLengthMs = 10;
				long ref = System.currentTimeMillis();
				long nxtRef = ref;
				// endless server loop
				while (true) {
					List<ConnectionHandler> chs = connectionsCopy;
					/*
					 * We control audio packet rate with the following loop.
					 */
					while (System.currentTimeMillis() < nxtRef) {
						Thread.sleep(0, 50);
					}
					nxtRef = nxtRef + frameLengthMs;
					/* mix : fetch one frame from each connection and mix them together. */
					short[] frame;
					for (int i = 0; i < chs.size(); i++) {
						ConnectionHandler ch = chs.get(i);
						/*
						 * prepare one buffer per client to hold data we want to
						 * send it. this buffer will hold the mix of all other
						 * participants speech. the array 'frames' holds these
						 * buffers.
						 */
						if (frames.size() == i) {
							// lazy prepare additional frames when #connection
							// grows (one frame per connection)
							frame = new short[AudioConstants.FRAME_SIZE];
							frames.add(frame);
						} else {
							frame = frames.get(i);
						}
						// copy client data in its frame (it will be negate and
						// mixed to other client data later)
						Pointer<Short> srcFrame = ch.getRingBuffer().getNextReadBuffer();
						// System.out.println("[" + i + "] " + srcFrame + "");
						if (srcFrame == null) {
							// clear frame array since it may be used later
							System.arraycopy(empty, 0, frame, 0, empty.length);
							if (i == 0) {
								// first frame to be added to mix is null. just
								// clear mix array.
								for (int j = 0; j < mix.length; j++) {
									mix[j] = 0;
								}
							}
							// else : do nothing since srcFrame is empty.
						} else {
							// copy from native to java array of this channel
							srcFrame.getShorts(frame);
							// create a mixed canal with ALL client into array
							// 'mix'. Add all samples. bonded to short "max" and
							// "min" values
							if (i == 0) {
								// first canal, just copy it
								System.arraycopy(frame, 0, mix, 0, frame.length);
							} else {
								// other canals, add them to existing mix
								for (int j = 0; j < mix.length; j++) {
									sum = mix[j] + frame[j];
									if (sum > Short.MAX_VALUE) {
										mix[j] = Short.MAX_VALUE;
									} else if (sum < Short.MIN_VALUE) {
										mix[j] = Short.MIN_VALUE;
									} else {
										mix[j] = (short) sum;
									}
								}
							}
						}
					}
					/**
					 * Now we have N array (frames) with each client own data.
					 * We do not want actually to send back to client its own
					 * sound : we are going to subtract it from 'mix' content
					 * that holds the sum of ALL clients sounds.
					 */
					for (int i = 0; i < chs.size(); i++) {
						ConnectionHandler clientHandler = chs.get(i);
						frame = frames.get(i);
						boolean emptyFrame = true;
						for (int j = 0; j < frame.length; j++) {
							frame[j] = (short) (0xffff & (mix[j] - frame[j]));
							// debug : do not remove own audio (loopback)
							//frame[j] = (short) (mix[j]);
							// keep trace if frame is empty
							if (frame[j] != 0) {
								emptyFrame = false;
							}
						}
						/* only send frame if not empty */
						if (!emptyFrame) {
							/*
							 * now 'frame' contains the mix of all clients
							 * except the sender. encode it and send it back.
							 */
							frameNative.setShorts(frame);
							int encDataSize = OpusLibrary.opus_encode(clientHandler.getEncoder(), frameNative, AudioConstants.FRAME_SIZE, encDataBuffer, 4000);
							encDataBuffer.getBytes(byteBuffer);
							//
							try {
								Buffer buffer = new Buffer(byteBuffer.array(), 0, encDataSize, DataType.VOICE);
								clientHandler.send(buffer);
							} catch (IOException e) {
								LOG.error("Failed to send data to client");
							}
						}
					}
				}
			} catch (Exception e) {
				LOG.error("Failed to mix", e);
			}
		}
	}

	@Override
	public void register(ConnectionHandler connectionHandler) {
		synchronized (connections) {
			connections.add(connectionHandler);
		}
		connectionsCopy = Collections.unmodifiableList(new ArrayList<ConnectionHandler>(connections));
	}

	@Override
	public void unregister(ConnectionHandler connectionHandler) {
		synchronized (connections) {
			connections.remove(connectionHandler);
		}
		connectionsCopy = Collections.unmodifiableList(new ArrayList<ConnectionHandler>(connections));
	}
}
