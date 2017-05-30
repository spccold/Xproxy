package xproxy.upstream;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import xproxy.conf.XproxyConfig;
import xproxy.conf.XproxyConfig.Server;

public class XproxyUpStreamChannelInitializer extends ChannelInitializer<Channel>{
	
	private final XproxyConfig config;
	private final Channel downstreamChannel;
	private final Server server;
	
	public XproxyUpStreamChannelInitializer(XproxyConfig config, Channel downstreamChannel, Server server) {
		this.config = config;
		this.downstreamChannel = downstreamChannel;
		this.server = server;
	}

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();
		pipeline.addLast(new HttpClientCodec());
		pipeline.addLast(new HttpObjectAggregator(512 * 1024));
		pipeline.addLast(new UpStreamHandler(config, downstreamChannel, server));
	}

}
