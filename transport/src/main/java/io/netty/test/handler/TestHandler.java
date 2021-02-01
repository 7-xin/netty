package io.netty.test.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class TestHandler extends ChannelInboundHandlerAdapter {

    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println(ctx.channel().id());
        System.out.println("111111111111111111111111111111111111111111111111");
        ctx.fireChannelRegistered();
    }

}
