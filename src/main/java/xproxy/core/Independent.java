package xproxy.core;

import java.util.concurrent.ThreadFactory;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import xproxy.util.PlatformUtil;

public class Independent {

	public static EventLoopGroup newEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
		if (PlatformUtil.isLinux()) {
			return new EpollEventLoopGroup(nThreads, threadFactory);
		} else if (PlatformUtil.isMac()) {
			return new KQueueEventLoopGroup(nThreads, threadFactory);
		} else {
			return new NioEventLoopGroup(nThreads, threadFactory);
		}
	}

	public static Class<? extends Channel> channelClass() {
		if (PlatformUtil.isLinux()) {
			return EpollSocketChannel.class;
		} else if (PlatformUtil.isMac()) {
			return KQueueSocketChannel.class;
		} else {
			return NioSocketChannel.class;
		}
	}

	public static Class<? extends ServerChannel> serverChannelClass() {
		if (PlatformUtil.isLinux()) {
			return EpollServerSocketChannel.class;
		} else if (PlatformUtil.isMac()) {
			return KQueueServerSocketChannel.class;
		} else {
			return NioServerSocketChannel.class;
		}
	}
}
