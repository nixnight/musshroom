package musshroom.server;

import musshroom.server.impl.ConnectionHandler;


public interface IServer {

	void register(ConnectionHandler connectionHandler);

	void unregister(ConnectionHandler connectionHandler);
}
