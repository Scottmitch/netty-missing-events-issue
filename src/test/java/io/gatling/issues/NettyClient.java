package io.gatling.issues;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedStream;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NettyClient implements Closeable {

    private final CountDownLatch latch;
    private final EventLoopGroup eventLoop;
    private final Bootstrap bootstrap;

    private volatile boolean success;

    public NettyClient() {

        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());

        this.latch = new CountDownLatch(1);;
        eventLoop = new NioEventLoopGroup();
        bootstrap = new Bootstrap().channel(NioSocketChannel.class).group(eventLoop);

        bootstrap.handler(new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel ch) throws Exception {

                ch.config().setOption(ChannelOption.AUTO_READ, false);

                ch.pipeline() /**/
                .addLast("logging", new LoggingHandler()) /**/
                .addLast("http", new HttpClientCodec()) /**/
                .addLast("chunking", new ChunkedWriteHandler()) /**/
                .addLast("handler", new MyHandler());
            }
        });
    }

    public boolean sendRequest(int port, byte[] bytes) throws InterruptedException {
        bootstrap.connect(new InetSocketAddress("127.0.0.1", port)).addListener(new ChannelFutureListener() {

            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, bytes == null ? HttpMethod.GET : HttpMethod.POST, "/");
                    request.headers()//
                            .set(HttpHeaders.Names.ACCEPT, "*/*")//
                            .set(HttpHeaders.Names.USER_AGENT, "NING/1.0");
                    HttpHeaders.setHost(request, "127.0.0.1:" + port);
                    HttpHeaders.setKeepAlive(request, true);
                    if (bytes != null) {
                        HttpHeaders.setTransferEncodingChunked(request);
                    }

                    Channel channel = future.channel();
                    channel.writeAndFlush(request);
                    if (bytes != null) {
                        channel.write(new ChunkedStream(new ByteArrayInputStream(bytes)));
                        channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                    }

                } else {
                    future.cause().printStackTrace();
                    latch.countDown();
                }
            }
        });
        
        latch.await(10, TimeUnit.SECONDS);
        return success;
    }

    public void close() throws java.io.IOException {
        eventLoop.shutdownGracefully();
    };

    private class MyHandler extends ChannelInboundHandlerAdapter {

        private boolean read = false;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.read();
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.read();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (read)
                success = true;
            else
                System.err.println("Channel became inactive before reading anything!!!");

            latch.countDown();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            read = true;
            ctx.close();
        }
    }
}
