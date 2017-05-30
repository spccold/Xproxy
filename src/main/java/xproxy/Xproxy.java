package xproxy;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import xproxy.conf.XproxyConfig;
import xproxy.conf.XproxyConfig.ConfigException;
import xproxy.downstream.DownStreamHandler;
import xproxy.downstream.XproxyDownStreamChannelInitializer;
import xproxy.upstream.lb.RoundRobinFactory;

public class Xproxy {

	private static final Logger logger = LoggerFactory.getLogger(Xproxy.class);

	private DownStreamHandler downStreamHandler;
	
	public static void main(String[] args) {
		Xproxy xproxy = new Xproxy();
		try {
			xproxy.initializeAndRun(new String[]{"/Users/wuwo/github/Xproxy/src/main/resources/xproxy.yml"});
			//xproxy.initializeAndRun(args);
		} catch (ConfigException e) {
			logger.error("Invalid config, exiting abnormally", e);
			System.err.println("Invalid config, exiting abnormally");
			System.exit(2);
		}
	}

	protected void initializeAndRun(String[] args) throws ConfigException {
		XproxyConfig config = new XproxyConfig();
		if (args.length == 1) {
			config.parse(args[0]);
			RoundRobinFactory.INSTANCE.init(config);
		} else {
			throw new IllegalArgumentException("Invalid args:" + Arrays.toString(args));
		}
		
		downStreamHandler = new DownStreamHandler(config);
		runFromConfig(config);
	}

	public void runFromConfig(XproxyConfig config) {

		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup(config.workerThreads());

		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup)
			 .channel(NioServerSocketChannel.class)
		     .childHandler(new XproxyDownStreamChannelInitializer(config, downStreamHandler));

			Channel ch = b.bind(config.listen()).syncUninterruptibly().channel();

			logger.info(String.format("bind to %d success.", config.listen()));

			ch.closeFuture().syncUninterruptibly();
		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}
}
