package musshroom.server.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import musshroom.server.IServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ServerSocket listen for incoming connection and start ConnectionHandler for
 * each one.
 */
public class ConnectionListener implements Runnable {
	private final static Logger LOG = LoggerFactory.getLogger(ConnectionListener.class);
	private int port;
	private ServerSocket srv;
	private IServer server;

	public ConnectionListener(int port, IServer server) {
		this.port = port;
		this.server = server;
	}

	@Override
	public void run() {
		try {
			LOG.debug("Start server on port [{}]", port);
			srv = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
			while (true) {
				Socket socket = srv.accept();
				LOG.debug("Got connection [{}]", socket.getRemoteSocketAddress());
				ConnectionHandler handler = new ConnectionHandler(socket, server);
				handler.start();
			}
		} catch (IOException e) {
			LOG.error("Failed to start server", e);
			System.exit(1);
		}
	}

	public void close() throws IOException {
		srv.close();
	}
}