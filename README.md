# Xproxy
> Mini Nginx, High Performance ^-^ !

## config

~~~yml
# accept connections on the specified port
listen: 8000

# keepalive timeout for all downstream connetions, second
keepalive_timeout: 65

# netty worker threads(auto = cpu cores)
worker_threads: auto

# max connections per worker
worker_connections: 102400

# all virtual hosts configurations
servers: 
  localhost1: 
    -
      path: /*
      proxy_pass: http://localhost1_pool
  localhost2: 
    -
      path: /*
      proxy_pass: http://localhost2_pool
  
# all upstream configurations
upstreams: 
  localhost1_pool: 
    keepalive: 16 # for all backends in current pool
    servers: 
     - 127.0.0.1:8080
     - 127.0.0.2:8080
  localhost2_pool: 
    keepalive: 32 # for all backends in current pool
    servers: 
     - 127.0.0.1:8088
     - 127.0.0.2:8088
     - 127.0.0.3:8088
     - 127.0.0.4:8088
~~~