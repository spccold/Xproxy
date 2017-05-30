package xproxy.upstream.lb;

import java.util.concurrent.atomic.AtomicInteger;

import xproxy.conf.XproxyConfig.Server;

public class RoundRobin implements ServerChooser {

	private final ServerChooser inner;

	public RoundRobin(Server[] servers) {
		inner = ServerChooserFactory.INSTANCE.newChooser(servers);
	}

	static class ServerChooserFactory {

		public static final ServerChooserFactory INSTANCE = new ServerChooserFactory();

		private ServerChooserFactory() {
		}

		public ServerChooser newChooser(Server[] servers) {
			if (isPowerOfTwo(servers.length)) {
				return new PowerOfTwoEventExecutorChooser(servers);
			} else {
				return new GenericEventExecutorChooser(servers);
			}
		}

		private static boolean isPowerOfTwo(int val) {
			return (val & -val) == val;
		}

		private static final class PowerOfTwoEventExecutorChooser implements ServerChooser {
			private final AtomicInteger idx = new AtomicInteger();
			private final Server[] servers;

			PowerOfTwoEventExecutorChooser(Server[] servers) {
				this.servers = servers;
			}

			public Server next() {
				return servers[idx.getAndIncrement() & servers.length - 1];
			}
		}

		private static final class GenericEventExecutorChooser implements ServerChooser {
			private final AtomicInteger idx = new AtomicInteger();
			private final Server[] servers;

			GenericEventExecutorChooser(Server[] servers) {
				this.servers = servers;
			}

			public Server next() {
				return servers[Math.abs(idx.getAndIncrement() % servers.length)];
			}
		}
	}

	@Override
	public Server next() {
		return inner.next();
	}
}

interface ServerChooser {
	Server next();
}