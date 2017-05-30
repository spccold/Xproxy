package xproxy.upstream.lb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import xproxy.conf.XproxyConfig;
import xproxy.conf.XproxyConfig.Server;

public class RoundRobinFactory {

	private final Map<String, RoundRobin> robinMap = new HashMap<>();

	public void init(XproxyConfig config) {
		Map<String, List<Server>> upstreams = config.upstreams();
		if (null == upstreams || upstreams.isEmpty()) {
			return;
		}

		for (Entry<String, List<Server>> upstreamEntry : upstreams.entrySet()) {
			robinMap.put(upstreamEntry.getKey(), new RoundRobin(upstreamEntry.getValue().toArray(new Server[] {})));
		}
	}

	public RoundRobin roundRobin(String proxypass) {
		return robinMap.get(proxypass);
	}
}
