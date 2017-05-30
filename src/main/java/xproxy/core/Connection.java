package xproxy.core;

import io.netty.channel.Channel;
import xproxy.conf.XproxyConfig.Server;

public class Connection {

	private final Server server;
	
	private final Channel channel;

	public Connection(Server server, Channel channel) {
		this.server = server;
		this.channel = channel;
	}

	public Server getServer() {
		return server;
	}

	public Channel getChannel() {
		return channel;
	}
}
