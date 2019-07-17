package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClientConfigImpl;
import org.xbib.netty.http.xmlrpc.client.XmlRpcTransportFactory;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcServer;

/** Abstract base implementation of {@link ClientProvider}.
 */
public abstract class ClientProviderImpl implements ClientProvider {

	protected final XmlRpcHandlerMapping mapping;

	protected XmlRpcClientConfigImpl clientConfig;

	protected abstract XmlRpcTransportFactory getTransportFactory(XmlRpcClient pClient);

	/** Creates a new instance.
	 * @param pMapping The test servers handler mapping.
	 */
	protected ClientProviderImpl(XmlRpcHandlerMapping pMapping) {
		mapping = pMapping;
	}

	protected XmlRpcServer getXmlRpcServer() {
		XmlRpcServer server = new XmlRpcServer();
		server.setHandlerMapping(mapping);
		return server;
	}

	public XmlRpcClientConfigImpl getConfig() throws Exception {
		if (clientConfig == null) {
			clientConfig =  new XmlRpcClientConfigImpl();
		}
		return clientConfig;
	}

	public XmlRpcClient getClient() {
		XmlRpcClient client = new XmlRpcClient();
		client.setTransportFactory(getTransportFactory(client));
		return client;
	}

}