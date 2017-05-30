package xproxy.upstream;

import java.util.LinkedList;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import xproxy.conf.XproxyConfig.Server;
import xproxy.core.AttributeKeys;
import xproxy.core.Connection;
import xproxy.core.RequestContext;

public class UpStreamHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

	private final Server server;
	
	private final String proxyPass;
	
	public UpStreamHandler(Server server, String proxyPass) {
		this.server = server;
		this.proxyPass = proxyPass;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
		Channel upstream = ctx.channel();
		
		Channel downstream = upstream.attr(AttributeKeys.DOWNSTREAM_CHANNEL_KEY).get();
		boolean keepAlived = upstream.attr(AttributeKeys.KEEP_ALIVED_KEY).get();

		LinkedList<Connection> conns = RequestContext.keepAlivedConntions(proxyPass);

		if (conns.size() == server.getKeepalive()) {
			// the least recently used connection are closed
			conns.pollFirst().getChannel().close();
		}
		conns.addLast(new Connection(server, upstream));
		if (keepAlived) { 
			downstream.writeAndFlush(response.retain(), downstream.voidPromise());
		}else{// close the downstream connection
			downstream.writeAndFlush(response.retain()).addListener(ChannelFutureListener.CLOSE);
		}
	}

}
