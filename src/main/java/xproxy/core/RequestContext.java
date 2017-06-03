package xproxy.core;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.FastThreadLocal;

public class RequestContext {

	private static final FastThreadLocal<RequestContext> CONTEXT = new FastThreadLocal<RequestContext>() {
		@Override
		protected RequestContext initialValue() throws Exception {
			return new RequestContext();
		}
	};

	private final Map<String, LinkedList<Connection>> keepAlivedConns = new HashMap<>();

	private final FullHttpResponse errorResponse;

	private final FullHttpResponse notfoundResponse;
	
	private RequestContext() {
		errorResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
		errorResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, errorResponse.content().readableBytes());
		errorResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

		notfoundResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
		notfoundResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, notfoundResponse.content().readableBytes());
		notfoundResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
	}

	public LinkedList<Connection> getKeepAlivedConns(String proxypass) {
		LinkedList<Connection> conns = keepAlivedConns.get(proxypass);
		if (null == conns) {
			conns = new LinkedList<>();
			keepAlivedConns.put(proxypass, conns);
		}
		return keepAlivedConns.get(proxypass);
	}

	public FullHttpResponse getErrorResponse() {
		return errorResponse;
	}

	public FullHttpResponse getNotfoundResponse(){
		return notfoundResponse;
	}
	
	public static LinkedList<Connection> keepAlivedConntions(String proxypass) {
		return CONTEXT.get().getKeepAlivedConns(proxypass);
	}

	public static FullHttpResponse errorResponse() {
		return CONTEXT.get().getErrorResponse().retain();
	}

	public static FullHttpResponse notfoundResponse() {
		return CONTEXT.get().getNotfoundResponse().retain();
	}
}
