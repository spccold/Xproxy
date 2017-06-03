package xproxy;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.util.concurrent.DefaultThreadFactory;
import xproxy.conf.XproxyConfig;
import xproxy.conf.XproxyConfig.ConfigException;
import xproxy.core.Independent;
import xproxy.downstream.DownStreamHandler;
import xproxy.downstream.XproxyDownStreamChannelInitializer;
import xproxy.upstream.lb.RoundRobinFactory;

public class Xproxy {

	private static final Logger logger = LoggerFactory.getLogger(Xproxy.class);

	private final RoundRobinFactory robinFactory = new RoundRobinFactory();

	private DownStreamHandler downStreamHandler;

	public static void main(String[] args) {
		Xproxy xproxy = new Xproxy();
		try {
			xproxy.initializeAndRun(args);
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
		} else {
			throw new IllegalArgumentException("Invalid args:" + Arrays.toString(args));
		}
		robinFactory.init(config);
		downStreamHandler = new DownStreamHandler(config, robinFactory);
		runFromConfig(config);
	}

	public void runFromConfig(XproxyConfig config) {

		EventLoopGroup bossGroup = Independent.newEventLoopGroup(1, new DefaultThreadFactory("Xproxy-Boss-Thread"));
		EventLoopGroup workerGroup = Independent.newEventLoopGroup(config.workerThreads(),
				new DefaultThreadFactory("Xproxy-Downstream-Worker-Thread"));

		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup);
			b.channel(Independent.serverChannelClass());

			// connections wait for accept(influenced by maxconn)
			b.option(ChannelOption.SO_BACKLOG, 1024);
			b.option(ChannelOption.SO_REUSEADDR, true);
			b.childOption(ChannelOption.SO_KEEPALIVE, true);
			b.childOption(ChannelOption.TCP_NODELAY, true);
			b.childOption(ChannelOption.SO_SNDBUF, 32 * 1024);
			b.childOption(ChannelOption.SO_RCVBUF, 32 * 1024);
			// temporary settings, need more tests
			b.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024));
			b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
			// default is true, reduce thread context switching
			b.childOption(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, true);

			b.childHandler(new XproxyDownStreamChannelInitializer(config, downStreamHandler));

			Channel ch = b.bind(config.listen()).syncUninterruptibly().channel();

			logger.info(String.format("bind to %d success.", config.listen()));

			ch.closeFuture().syncUninterruptibly();
		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}
}
