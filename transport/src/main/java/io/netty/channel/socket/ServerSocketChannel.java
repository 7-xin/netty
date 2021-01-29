/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket;

import io.netty.channel.ServerChannel;

import java.net.InetSocketAddress;

/**
 * A TCP/IP {@link ServerChannel} which accepts incoming TCP/IP connections.
 *
 * todo 接受 tcp/ip 服务端 channel
 */
public interface ServerSocketChannel extends ServerChannel {

    // todo 配置
    @Override
    ServerSocketChannelConfig config();

    // todo 本地地址
    @Override
    InetSocketAddress localAddress();

    // todo 远程地址
    @Override
    InetSocketAddress remoteAddress();
}
