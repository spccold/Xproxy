package xproxy.upstream;

import java.util.LinkedList;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.AttributeKey;
import xproxy.conf.XproxyConfig;
import xproxy.conf.XproxyConfig.Server;
import xproxy.core.Connection;
import xproxy.core.RequestContext;

public class UpStreamHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

	private final XproxyConfig config;

	//private final Channel downstreamChannel;
	private final Server server;

	public UpStreamHandler(XproxyConfig config, Channel downstreamChannel, Server server) {
		this.config = config;
		//this.downstreamChannel = downstreamChannel;
		this.server = server;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {

		Channel downstreamChannel = (Channel) ctx.channel().attr(AttributeKey.valueOf("downstreamChannel")).get();
		
		String proxypass = (String)ctx.channel().attr(AttributeKey.valueOf("proxypass")).get();
		
		boolean keepAlive = (boolean) ctx.channel().attr(AttributeKey.valueOf("keepalive")).get();
		
		LinkedList<Connection> conns = RequestContext.keepAlivedConntions(proxypass);

		if (conns.size() == server.getKeepalive()) {
			// the least recently used connections are closed
			conns.pollFirst().getChannel().close();
		}
		conns.addLast(new Connection(server, ctx.channel()));
		
		downstreamChannel.writeAndFlush(response.retain(), downstreamChannel.voidPromise());
		
		if(!keepAlive){ // close the downstream connection
			downstreamChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
		}
	}

}
