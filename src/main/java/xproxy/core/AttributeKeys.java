package xproxy.core;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public class AttributeKeys {
	
	public static final AttributeKey<Channel> downstream_channel_key = AttributeKey.valueOf("");

	public static final AttributeKey<Channel> xx = AttributeKey.valueOf("");
	
	public static final AttributeKey<Channel> yy = AttributeKey.valueOf("");
}
