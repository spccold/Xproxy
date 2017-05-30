package xproxy.downstream;

import java.util.Iterator;
import java.util.LinkedList;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.IdleStateEvent;
import xproxy.conf.XproxyConfig;
import xproxy.conf.XproxyConfig.Server;
import xproxy.core.AttributeKeys;
import xproxy.core.Connection;
import xproxy.core.RequestContext;
import xproxy.upstream.XproxyUpStreamChannelInitializer;
import xproxy.upstream.lb.RoundRobin;
import xproxy.upstream.lb.RoundRobinFactory;

@ChannelHandler.Sharable
public class DownStreamHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private final XproxyConfig config;

	private final RoundRobinFactory robinFactory;

	public DownStreamHandler(XproxyConfig config, RoundRobinFactory robinFactory) {
		this.config = config;
		this.robinFactory = robinFactory;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		final Channel downstream = ctx.channel();

		boolean keepAlived = HttpUtil.isKeepAlive(request);

		HttpHeaders requestHeaders = request.headers();

		// get Host header
		String serverName = requestHeaders.get(HttpHeaderNames.HOST);
		// get proxy_pass
		String proxyPass = config.proxyPass(serverName, request.uri());

		// get roundRobin
		RoundRobin roundRobin = null;
		Server server = null;
		if (null == proxyPass || null == (roundRobin = robinFactory.roundRobin(proxyPass))
				|| null == (server = roundRobin.next())) {
			// return 404
			notFound(ctx, downstream, keepAlived);
			return;
		}

		// rewrite http request(keep alive to upstream)
		request.setProtocolVersion(HttpVersion.HTTP_1_1);
		requestHeaders.remove(HttpHeaderNames.CONNECTION);

		// increase refCount
		request.retain();

		// get connection from cache
		Connection connection = getConn(server, proxyPass);
		if (null == connection) {// need create an new connection
			Bootstrap b = new Bootstrap();
			b.group(downstream.eventLoop());
			b.channel(ctx.channel().getClass());
			b.handler(new XproxyUpStreamChannelInitializer(server, proxyPass));

			ChannelFuture connectFuture = b.connect(server.getIp(), server.getPort());
			connectFuture.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess()) {
						attrAndWrite(request, future.channel(), downstream, keepAlived);
					} else {
						request.release();
						downstream.close();
					}
				}
			});
		} else {// use the cached connection
			attrAndWrite(request, connection.getChannel(), downstream, keepAlived);
		}

	}

	public Connection getConn(Server server, String proxyPass) {
		LinkedList<Connection> conns = RequestContext.keepAlivedConntions(proxyPass);
		Connection connection = null;

		Connection tmp = null;
		for (Iterator<Connection> it = conns.iterator(); it.hasNext();) {
			tmp = it.next();
			// find the matched keepalived connection
			if (server.equals(tmp.getServer())) {
				it.remove();
				connection = tmp;
				break;
			}
		}
		return connection;
	}

	public void notFound(ChannelHandlerContext ctx, Channel downstream, boolean keepAlived) {
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
		if (keepAlived) {
			response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			ctx.writeAndFlush(response, downstream.voidPromise());
		} else {
			ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
		}
	}

	public void attrAndWrite(FullHttpRequest request, Channel upstream, Channel downstream, boolean keepalived) {
		upstream.attr(AttributeKeys.DOWNSTREAM_CHANNEL_KEY).set(downstream);
		upstream.attr(AttributeKeys.KEEP_ALIVED_KEY).set(keepalived);
		upstream.writeAndFlush(request, upstream.voidPromise());
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent) { // close all connecitons that
												// exceed keepalive_timeout
			ctx.channel().close(ctx.channel().voidPromise());
		} else {
			super.userEventTriggered(ctx, evt);
		}
	}
}
