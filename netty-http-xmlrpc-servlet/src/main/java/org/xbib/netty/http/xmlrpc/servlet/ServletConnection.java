package org.xbib.netty.http.xmlrpc.servlet;

import java.io.IOException;
import java.net.Socket;

import javax.servlet.http.HttpServlet;

/** {@link ServletWebServer ServletWebServer's} {@link Runnable} for handling a single
 * servlet connection.
 */
public class ServletConnection implements ThreadPool.InterruptableTask {

	private final HttpServlet servlet;

	private final Socket socket;

	private final HttpServletRequestImpl request;

	private final HttpServletResponseImpl response;

	private boolean shuttingDown;

	/** Creates a new instance.
	 * @param pServlet The servlet, which ought to handle the request.
	 * @param pSocket The socket, to which the client is connected.
	 * @throws IOException
	 */
	ServletConnection(HttpServlet pServlet, Socket pSocket) throws IOException {
		servlet = pServlet;
		socket = pSocket;
		request = new HttpServletRequestImpl(pSocket);
		response = new HttpServletResponseImpl(pSocket);
	}

	@Override
	public void run() {
        try {
            request.readHttpHeaders();
            servlet.service(request, response);
        } catch (Throwable t) {
            if (!shuttingDown) {
                throw new RuntimeException(t);
            }
        }
	}

	@Override
    public void shutdown() throws Throwable {
        shuttingDown = true;
        socket.close();
    }
}