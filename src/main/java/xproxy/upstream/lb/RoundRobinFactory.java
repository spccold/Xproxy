package xproxy.upstream.lb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import xproxy.conf.XproxyConfig;
import xproxy.conf.XproxyConfig.Server;

public class RoundRobinFactory {

	public static final RoundRobinFactory INSTANCE = new RoundRobinFactory();

	private XproxyConfig xproxyConfig;

	/** {proxy_pass : [RoundRobin...]} */
	private final Map<String, RoundRobin> robinMap = new HashMap<>();

	private RoundRobinFactory() {
	}

	public void init(XproxyConfig config) {
		this.xproxyConfig = config;
		Map<String, List<Server>> upstreams = config.upstreams();
		if (null == upstreams || upstreams.isEmpty()) {
			return;
		}

		for (Entry<String, List<Server>> upstreamEntry : upstreams.entrySet()) {
			robinMap.put(upstreamEntry.getKey(),
					new RoundRobin(upstreamEntry.getValue().toArray(new Server[] {})));
		}
	}
	
	public RoundRobin roundRobin(String proxypass) {
		return robinMap.get(proxypass);
	}
	
	public RoundRobin roundRobin(String serverName, String uri) {
		String proxypass = xproxyConfig.proxyPass(serverName, uri);
		if (StringUtils.isBlank(proxypass)) {
			return null;
		}
		return robinMap.get(proxypass);
	}
}
