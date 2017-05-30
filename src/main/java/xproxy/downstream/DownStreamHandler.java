package xproxy.downstream;

import java.util.Iterator;
import java.util.LinkedList;

import io.netty.bootstrap.Bootstrap;
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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import xproxy.conf.XproxyConfig;
import xproxy.conf.XproxyConfig.Server;
import xproxy.core.Connection;
import xproxy.core.RequestContext;
import xproxy.upstream.XproxyUpStreamChannelInitializer;
import xproxy.upstream.lb.RoundRobin;
import xproxy.upstream.lb.RoundRobinFactory;

@ChannelHandler.Sharable
public class DownStreamHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	
	private final XproxyConfig config;

	public DownStreamHandler(XproxyConfig config) {
		this.config = config;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		final Channel downstreamChannel = ctx.channel();
		boolean keepAlived = HttpUtil.isKeepAlive(request);

		String serverName = request.headers().get(HttpHeaderNames.HOST);
		String proxypass = config.proxyPass(serverName, request.uri());
		RoundRobin roundRobin = RoundRobinFactory.INSTANCE.roundRobin(proxypass);
		Server server;
		if (null == roundRobin || null == (server = roundRobin.next())) {
			// return 404
			FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
			if (keepAlived) {
				response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
				response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			}
			ctx.writeAndFlush(response, downstreamChannel.voidPromise());
			return;
		}

		// reset http request(keepalive)
		request.setProtocolVersion(HttpVersion.HTTP_1_1);
		request.headers().remove(HttpHeaderNames.CONNECTION);

		LinkedList<Connection> conns = RequestContext.keepAlivedConntions(proxypass);
		Connection connection = null;

		Connection conn = null;
		for (Iterator<Connection> it = conns.iterator(); it.hasNext();) {
			conn = it.next();
			// find the keepalived connection
			if (server.equals(conn.getServer())) {
				it.remove();
				connection = conn;
				break;
			}
		}

		if (null == connection) {// need create new connection
			Bootstrap b = new Bootstrap();
			b.group(downstreamChannel.eventLoop()).channel(ctx.channel().getClass())
					.handler(new XproxyUpStreamChannelInitializer(config, downstreamChannel, server));

			ChannelFuture connectFuture = b.connect(server.getIp(), server.getPort());
			request.retain();
			connectFuture.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess()) {
						future.channel().attr(AttributeKey.valueOf("downstreamChannel")).set(downstreamChannel);
						future.channel().attr(AttributeKey.valueOf("proxypass")).set(proxypass);
						future.channel().attr(AttributeKey.valueOf("keepalive")).set(keepAlived);
						future.channel().writeAndFlush(request, future.channel().voidPromise());
					} else {
						downstreamChannel.close();
					}
				}
			});
		} else {// get cached connection
			connection.getChannel().attr(AttributeKey.valueOf("keepalive")).set(keepAlived);
			connection.getChannel().attr(AttributeKey.valueOf("proxypass")).set(proxypass);
			connection.getChannel().attr(AttributeKey.valueOf("downstreamChannel")).set(downstreamChannel);
			connection.getChannel().writeAndFlush(request.retain(), connection.getChannel().voidPromise());
		}

	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if(evt instanceof IdleStateEvent){ // close all connecitons that exceed keepalive_timeout
			ctx.channel().close(ctx.channel().voidPromise());
		}else{
			super.userEventTriggered(ctx, evt);
		}
	}
}
