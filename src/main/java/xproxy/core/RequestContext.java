package xproxy.core;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import io.netty.util.concurrent.FastThreadLocal;

public class RequestContext {

	private final Map<String, LinkedList<Connection>> keepAlivedConns = new HashMap<>();

	private static final FastThreadLocal<RequestContext> CONTEXT = new FastThreadLocal<RequestContext>() {
		@Override
		protected RequestContext initialValue() throws Exception {
			return new RequestContext();
		}
	};

	private RequestContext() {}

	public LinkedList<Connection> getKeepAlivedConns(String proxypass) {
		LinkedList<Connection> conns = keepAlivedConns.get(proxypass);
		if (null == conns) {
			conns = new LinkedList<>();
			keepAlivedConns.put(proxypass, conns);
		}
		return keepAlivedConns.get(proxypass);
	}

	public static LinkedList<Connection> keepAlivedConntions(String proxypass) {
		return CONTEXT.get().getKeepAlivedConns(proxypass);
	}
}
