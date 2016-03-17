package musshroom.server.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import musshroom.client.audio.pa.PaErrorUtil;
import musshroom.common.AudioConstants;
import musshroom.common.JsonMessage;
import musshroom.common.audio.DataDecoder;
import musshroom.common.audio.RingBuffer;
import musshroom.common.io.DataInputStream;
import musshroom.common.io.DataInputStream.Buffer;
import musshroom.common.io.DataOutputStream;
import musshroom.common.io.DataType;
import musshroom.common.io.StringSerializer;
import musshroom.server.IServer;
import opus.OpusLibrary;
import opus.OpusLibrary.OpusEncoder;

import org.bridj.Pointer;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import portaudio.PortaudioLibrary.PaErrorCode;

public class ConnectionHandler implements Runnable {
	private final static Logger LOG = LoggerFactory.getLogger(ConnectionHandler.class);
	public static final int SERVER_INPUT_RING_SIZE = AudioConstants.FRAME_SIZE / 20;
	public static final int SERVER_INPUT_RING_CONFORT = SERVER_INPUT_RING_SIZE / 2;
	private Socket socket;
	private String userId;
	private DataInputStream dip;
	private DataOutputStream dop;
	private ObjectMapper mapper = new ObjectMapper();
	private IServer server;
	private RingBuffer inputRing;
	private DataDecoder decoder;
	private Pointer<OpusEncoder> encoder;

	public String getUserId() {
		return userId;
	}

	public RingBuffer getRingBuffer() {
		return inputRing;
	}

	public Pointer<OpusEncoder> getEncoder() {
		return encoder;
	}

	public ConnectionHandler(Socket socket, IServer server) {
		this.socket = socket;
		this.server = server;
		/**
		 * opus encoder is used to re-encode audio data once we have mixed audio
		 * from all participants.
		 */
		Pointer<Integer> error = Pointer.allocateInt();
		encoder = OpusLibrary.opus_encoder_create(AudioConstants.SAMPLERATE, 1, OpusLibrary.OPUS_APPLICATION_VOIP, error);
		PaErrorCode err = PaErrorUtil.getPaErrorCode(error.get());
		if (err != PaErrorCode.paNoError) {
			LOG.error("failed to create encoder : " + err);
			System.exit(0);
		}
		/**
		 * input ring is not blocking since the mixer (ServerModel) should be
		 * able to process a mix even if one client do not provide audio data
		 * anymore (due to network congestion, or if client does not have any
		 * data lef)
		 */
		inputRing = new RingBuffer(SERVER_INPUT_RING_SIZE, SERVER_INPUT_RING_CONFORT, false);
		decoder = new DataDecoder();
		decoder.setup(inputRing);
	}

	public void start() {
		Thread t = new Thread(this, "handler");
		t.setDaemon(true);
		t.start();
	}

	public void stop() {
		try {
			LOG.debug("close()");
			socket.close();
		} catch (IOException e) {
			LOG.error("Failed to close socket", e);
		}
	}

	public void run() {
		LOG.debug("Handle socket connection [" + socket.getRemoteSocketAddress() + "]");
		server.register(this);
		try {
			// read userId (from Shell, based on server SSH connection)
			InputStream is = socket.getInputStream();
			String userId = StringSerializer.readString(is);
			if (userId == null || userId.length() == 0) {
				LOG.error("Received bad username");
				// exit thread (close connection)
				return;
			}
			LOG.debug("User [{}] connected", userId);
			//
			LOG.info("wait packet");
			dip = new DataInputStream(is);
			dop = new DataOutputStream(socket.getOutputStream());
			// consume data packets
			Buffer next;
			while ((next = dip.next()) != null) {
				switch (next.getType()) {
				case JSON:
					LOG.info("Got packet " + next.getType());
					JsonMessage req = mapper.readValue(next.getDataAsString(), JsonMessage.class);
					handleJsonMessage(req);
					break;
				case VOICE:
					// decode voice data and put result in ring buffer
					decoder.handleData(next.getData(), next.getOffset(), next.getSize(), next.getType());
					break;
				default:
					LOG.error("Not implemented [Packet {}]", next.getType());
					break;
				}
			}
			LOG.debug("ConnectionHanlder terminated [1no more DataPacket available]");
		} catch (IOException e) {
			LOG.error("Hanlder's socket closed.", e);
		} finally {
			server.unregister(this);
			close();
		}
	}

	private void close() {
		if (dip != null) {
			dip.close();
		}
		if (dop != null) {
			dop.close();
		}
		if (socket != null) {
			try {
				socket.close();
			} catch (Exception e) {
			}
		}
	}

	private void handleJsonMessage(JsonMessage req) throws IOException {
		switch (req.type) {
		case JsonMessage.ERROR:
			LOG.error("Got error from client [" + req.method + "]");
			break;
		case JsonMessage.REQUEST:
			handleJsonRequest(req);
			break;
		default:
			LOG.error("Unsupported message [" + req.type + "]");
			break;
		}
	}

	public void send(Buffer buffer) throws IOException {
		dop.send(buffer.getData(), buffer.getOffset(), buffer.getSize(), buffer.getType());
	}

	private void handleJsonRequest(JsonMessage req) throws IOException {
		switch (req.method) {
		case "xxxx":
			LOG.info("Got request : " + req.method);
			JsonMessage msg = new JsonMessage(req.uid, "response");
			msg.data = mapper.writeValueAsBytes("XXXX response XXXX");
			msg.dataType = String.class.getCanonicalName();
			dop.send(mapper.writeValueAsBytes(msg), DataType.JSON);
			break;
		default:
			LOG.warn("unsupported request [{}]", req.method);
		}
	}
}