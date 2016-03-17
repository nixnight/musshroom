package musshroom.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import musshroom.client.ClientConnection.IDataCallback;
import musshroom.client.audio.pa.PA;
import musshroom.client.audio.pa.Player;
import musshroom.client.audio.pa.Recorder;
import musshroom.common.JsonMessage;
import musshroom.common.audio.DataDecoder;
import musshroom.common.audio.DataEncoder;
import musshroom.common.audio.RingBuffer;
import musshroom.common.io.DataInputStream.Buffer;
import musshroom.common.io.DataType;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client implements Runnable, IDataCallback {
	private final static Logger LOG = LoggerFactory.getLogger(Client.class);
	private static final long TIMEOUT = 20000; // 20s
	private ClientConnection econ;
	private boolean connected;
	private ObjectMapper mapper = new ObjectMapper();
	// requests
	private Lock reqLock = new ReentrantLock();
	private HashMap<String, MonitorObject> reqWaitingQueue = new HashMap<String, MonitorObject>();
	private DataEncoder encoder;
	private DataDecoder decoder;
	private Player player;
	private Recorder recorder;

	public Client(ClientConnection econ) {
		this.econ = econ;
		recorder = new Recorder();
		player = new Player();
		encoder = new DataEncoder();
		decoder = new DataDecoder();
		/*
		 * player buffer is not blocking and should be large enough to ensure
		 * that PortAudio Stream always get audio data. Failing at this result
		 * in audio glitches (metallic voice). BUT this buffer should be as
		 * small as possible in order to minimize latency.
		 */
		RingBuffer playerBuffer = new RingBuffer(25, 10, false, "playback");
		/*
		 * recorder buffer is blocking. PortAudio fill it at constant rate
		 * (sampling rate) and the encoder remove data from it in order to
		 * process them. Since encoder is faster than PortAudio this buffer is
		 * (should be) almost always empty.
		 */
		RingBuffer recorderBuffer = new RingBuffer(25, 1, true, "record");
		//
		PA.initPortAudio();
		econ.setDataCallbak(this);
		recorder.setup(recorderBuffer);
		encoder.setup(recorderBuffer, econ);
		player.setup(playerBuffer);
		decoder.setup(playerBuffer);
		//
		encoder.start();
		recorder.start();
		player.start();
	}

	public void start() {
		Thread t = new Thread(this, "client");
		t.start();
	}

	@Override
	public void run() {
		try {
			while (true) {
				// check if connected
				if (econ != null && econ.isConnected()) {
					connected = true;
					/**
					 * normal periodic loop run by client
					 */
					keepalive(); // why?
					// TODO pool messages
					Thread.sleep(5000);
				} else {
					connected = false;
					LOG.debug("not connected : wait.");
					Thread.sleep(1000);
					// TODO count #failed and rebuild TCP connection
				}
			}
		} catch (Exception e) {
			LOG.error("Client Failure", e);
			System.exit(2);
		}
	}

	@Override
	public void handle(Buffer dpk, DataType type) {
		switch (type) {
		case JSON:
			handleJson(dpk);
			break;
		case VOICE:
			decoder.handleData(dpk.getData(), dpk.getOffset(), dpk.getSize(), DataType.VOICE);
			break;
		default:
			LOG.error("Unsupported packet type [{}]", type);
			break;
		}
	}

	/**
	 * Callback when a JSON packet is received.
	 */
	public void handleJson(Buffer dpk) {
		try {
			JsonMessage msg = mapper.readValue(dpk.getData(), dpk.getOffset(), dpk.getSize(), JsonMessage.class);
			switch (msg.type) {
			case JsonMessage.REQUEST:
				LOG.error("REQUEST not implemented");
				break;
			case JsonMessage.ERROR:
				LOG.error("ERROR not implemented");
				break;
			case JsonMessage.RESPONSE:
				// check if we still wait response
				MonitorObject monitor = null;
				try {
					reqLock.lock();
					monitor = reqWaitingQueue.remove(msg.uid);
				} finally {
					reqLock.unlock();
				}
				if (monitor == null) {
					// no -> proabably timeout. return error to server.
					econ.sendData(mapper.writeValueAsBytes(JsonMessage.createErrorMsg(msg.uid, "timeout")), DataType.JSON);
				} else {
					// yes -> handle it
					synchronized (monitor) {
						monitor.response = msg;
						monitor.notifyAll();
					}
				}
				break;
			default:
				LOG.warn("unsupported message type [{}]", msg.type);
				break;
			}
		} catch (IOException e) {
			LOG.error("Failed to de-serialize JSON", e);
		}
	}

	private void keepalive() throws ClientException {
		// not implemented
	}

	public class MonitorObject {
		public JsonMessage request;
		public JsonMessage response;
	}

	/**
	 * send a request and wait for a response within a given timeout.
	 * 
	 * @return
	 * @throws IOException
	 * @throws JsonMappingException
	 * @throws JsonGenerationException
	 */
	private JsonMessage request(JsonMessage req) throws IOException {
		MonitorObject monitor = new MonitorObject();
		monitor.request = req;
		try {
			reqLock.lock();
			econ.sendData(mapper.writeValueAsBytes(req), DataType.JSON);
			reqWaitingQueue.put(req.uid, monitor);
		} finally {
			reqLock.unlock();
		}
		synchronized (monitor) {
			try {
				monitor.wait(TIMEOUT);
			} catch (InterruptedException e) {
				LOG.error("Thread fail", e);
			}
		}
		try {
			reqLock.lock();
			// Check if response has been received (or we exit monitor wait by
			// timeout)
			if (monitor.response == null) {
				throw new RuntimeException("Request failed du to timeout.");
			} else {
				return monitor.response;
			}
			// TODO
		} finally {
			reqLock.unlock();
		}
	}
}
