package xproxy.upstream;

import java.util.Iterator;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import xproxy.conf.XproxyConfig.Server;
import xproxy.core.AttributeKeys;
import xproxy.core.Connection;
import xproxy.core.RequestContext;

public class UpStreamHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

	private static final Logger logger = LoggerFactory.getLogger(UpStreamHandler.class);

	private final Server server;

	private final String proxyPass;

	public UpStreamHandler(Server server, String proxyPass) {
		this.server = server;
		this.proxyPass = proxyPass;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
		Channel upstream = ctx.channel();

		// get context and clear
		Channel downstream = upstream.attr(AttributeKeys.DOWNSTREAM_CHANNEL_KEY).getAndSet(null);
		boolean keepAlived = upstream.attr(AttributeKeys.KEEP_ALIVED_KEY).getAndSet(null);

		LinkedList<Connection> conns = RequestContext.keepAlivedConntions(proxyPass);

		if (conns.size() == server.getKeepalive()) {
			// the least recently used connection are closed
			logger.info(String.format(
					"[%s]cached connctions exceed the keepalive[%d], the least recently used connection are closed",
					proxyPass, server.getKeepalive()));
			Channel tmp = conns.pollFirst().getChannel();
			tmp.attr(AttributeKeys.UPSTREAM_ACTIVE_CLOSE_KEY).set(true);
			tmp.close();
		}
		conns.addLast(new Connection(server, upstream));
		if (keepAlived) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			downstream.writeAndFlush(response.retain(), downstream.voidPromise());
		} else {// close the downstream connection
			downstream.writeAndFlush(response.retain()).addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		boolean activeClose = false;
		if(ctx.channel().hasAttr(AttributeKeys.UPSTREAM_ACTIVE_CLOSE_KEY)
				&& ctx.channel().attr(AttributeKeys.UPSTREAM_ACTIVE_CLOSE_KEY).get()){
			activeClose = true;
		}
		
		logger.warn(String.format("upstream channel[%s] inactive, activeClose:%s", ctx.channel(), activeClose));

		Channel downstream = null;
		Boolean keepAlived = null;
		if (null != (downstream = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CHANNEL_KEY).get())
				&& null != (keepAlived = ctx.channel().attr(AttributeKeys.KEEP_ALIVED_KEY).get())) {
			if (keepAlived) {
				downstream.writeAndFlush(RequestContext.errorResponse(), downstream.voidPromise());
			} else {
				downstream.writeAndFlush(RequestContext.errorResponse()).addListener(ChannelFutureListener.CLOSE);
			}
		}else{// remove current inactive channel from cached conns
			LinkedList<Connection> conns = RequestContext.keepAlivedConntions(proxyPass);
			Connection tmp = null;
			
			for (Iterator<Connection> it = conns.iterator(); it.hasNext();) {
				tmp = it.next();
				// find the inactive connection
				if (server == tmp.getServer()) {
					it.remove();
					break;
				}
			}
		}
		super.channelInactive(ctx);
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
		logger.warn(String.format("upstream channel[%s] writability changed, isWritable: %s", ctx.channel(),
				ctx.channel().isWritable()));
		super.channelWritabilityChanged(ctx);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error(String.format("upstream channel[%s] exceptionCaught", ctx.channel()), cause);
	}
}
