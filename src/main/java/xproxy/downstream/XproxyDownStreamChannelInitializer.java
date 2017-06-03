package xproxy.downstream;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import xproxy.conf.XproxyConfig;

public class XproxyDownStreamChannelInitializer extends ChannelInitializer<Channel> {

	//private final XproxyConfig config;
	
	private final DownStreamHandler downStreamHandler;

	public XproxyDownStreamChannelInitializer(XproxyConfig config, DownStreamHandler downStreamHandler) {
		//this.config = config;
		this.downStreamHandler = downStreamHandler;
	}

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();
		//pipeline.addLast(new IdleStateHandler(0, 0, config.keepaliveTimeout(), TimeUnit.SECONDS));
		pipeline.addLast(new HttpServerCodec());
		pipeline.addLast(new HttpObjectAggregator(512 * 1024));
		pipeline.addLast(downStreamHandler);
	}

}
