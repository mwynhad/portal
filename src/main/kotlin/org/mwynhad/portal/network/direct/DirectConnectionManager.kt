package org.mwynhad.portal.network.direct

import org.mwynhad.portal.Portal
import org.mwynhad.portal.network.NetworkManager
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * Direct TCP connection manager for lowest latency node-to-node communication
 * Uses Netty for high-performance async networking
 */
class DirectConnectionManager(
    private val plugin: Portal,
    private val networkManager: NetworkManager
) {
    
    private val config get() = plugin.portalConfig.direct
    
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var serverChannel: Channel? = null
    
    // Connected peer channels, keyed by node ID
    private val peerChannels = ConcurrentHashMap<String, Channel>()
    
    // Pending connections (before handshake completes)
    private val pendingChannels = ConcurrentHashMap<Channel, Long>()
    
    companion object {
        // Protocol constants
        const val MAX_FRAME_SIZE = 16 * 1024 * 1024 // 16MB max message
        const val LENGTH_FIELD_LENGTH = 4
        
        // Handshake message prefix
        val HANDSHAKE_PREFIX = "PORTAL-HELLO:".toByteArray()
    }
    
    /**
     * Start the direct connection server
     */
    suspend fun startServer() = withContext(Dispatchers.IO) {
        val threads = plugin.portalConfig.performance.networkThreads
        
        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup(threads)
        
        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 128)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.TCP_NODELAY, true) // Disable Nagle's for low latency
            .childOption(ChannelOption.SO_SNDBUF, 1024 * 1024) // 1MB send buffer
            .childOption(ChannelOption.SO_RCVBUF, 1024 * 1024) // 1MB receive buffer
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().apply {
                        // Frame decoder/encoder
                        addLast("frameDecoder", LengthFieldBasedFrameDecoder(
                            MAX_FRAME_SIZE, 0, LENGTH_FIELD_LENGTH, 0, LENGTH_FIELD_LENGTH
                        ))
                        addLast("frameEncoder", LengthFieldPrepender(LENGTH_FIELD_LENGTH))
                        
                        // Idle handler for keepalive
                        addLast("idleHandler", IdleStateHandler(30, 15, 0, TimeUnit.SECONDS))
                        
                        // Message handler
                        addLast("messageHandler", DirectMessageHandler(this@DirectConnectionManager, true))
                    }
                }
            })
        
        try {
            val bindAddress = InetSocketAddress(config.bindAddress, config.bindPort)
            serverChannel = bootstrap.bind(bindAddress).sync().channel()
            plugin.logger.info("Direct connection server started on ${config.bindAddress}:${config.bindPort}")
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to start direct connection server", e)
            throw e
        }
    }
    
    /**
     * Connect to a peer node
     */
    suspend fun connectToPeer(host: String, port: Int) = withContext(Dispatchers.IO) {
        val bootstrap = Bootstrap()
            .group(workerGroup)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .option(ChannelOption.SO_SNDBUF, 1024 * 1024)
            .option(ChannelOption.SO_RCVBUF, 1024 * 1024)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().apply {
                        addLast("frameDecoder", LengthFieldBasedFrameDecoder(
                            MAX_FRAME_SIZE, 0, LENGTH_FIELD_LENGTH, 0, LENGTH_FIELD_LENGTH
                        ))
                        addLast("frameEncoder", LengthFieldPrepender(LENGTH_FIELD_LENGTH))
                        addLast("idleHandler", IdleStateHandler(30, 15, 0, TimeUnit.SECONDS))
                        addLast("messageHandler", DirectMessageHandler(this@DirectConnectionManager, false))
                    }
                }
            })
        
        try {
            val channel = bootstrap.connect(host, port).sync().channel()
            pendingChannels[channel] = System.currentTimeMillis()
            
            // Send handshake
            sendHandshake(channel)
            
            plugin.logger.info("Connected to peer at $host:$port")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to connect to peer at $host:$port", e)
        }
    }
    
    /**
     * Send handshake message
     */
    private fun sendHandshake(channel: Channel) {
        val nodeId = Portal.nodeId
        val handshakeData = HANDSHAKE_PREFIX + nodeId.toByteArray()
        val buf = Unpooled.wrappedBuffer(handshakeData)
        channel.writeAndFlush(buf)
    }
    
    /**
     * Handle received handshake
     */
    fun handleHandshake(channel: Channel, nodeId: String) {
        pendingChannels.remove(channel)
        
        // Check if we already have a connection to this node
        val existing = peerChannels.put(nodeId, channel)
        existing?.close()
        
        plugin.logger.info("Handshake complete with node: $nodeId")
        
        // Store channel attribute for later lookup
        channel.attr(NODE_ID_KEY).set(nodeId)
    }
    
    /**
     * Handle received message
     */
    fun handleMessage(data: ByteArray) {
        networkManager.onMessageReceived(data)
    }
    
    /**
     * Handle channel disconnect
     */
    fun handleDisconnect(channel: Channel) {
        val nodeId = channel.attr(NODE_ID_KEY).get()
        if (nodeId != null) {
            peerChannels.remove(nodeId, channel)
            plugin.logger.info("Peer disconnected: $nodeId")
        } else {
            pendingChannels.remove(channel)
        }
    }
    
    /**
     * Broadcast to all connected peers
     * Returns true if at least one peer received the message
     */
    fun broadcast(data: ByteArray): Boolean {
        if (peerChannels.isEmpty()) return false
        
        val buf = Unpooled.wrappedBuffer(data)
        
        peerChannels.values.forEach { channel ->
            if (channel.isActive) {
                channel.writeAndFlush(buf.retainedDuplicate())
            }
        }
        
        buf.release()
        return true
    }
    
    /**
     * Send to a specific node
     * Returns true if the message was sent
     */
    fun sendToNode(nodeId: String, data: ByteArray): Boolean {
        val channel = peerChannels[nodeId] ?: return false
        
        if (!channel.isActive) {
            peerChannels.remove(nodeId)
            return false
        }
        
        val buf = Unpooled.wrappedBuffer(data)
        channel.writeAndFlush(buf)
        return true
    }
    
    /**
     * Get count of connected nodes
     */
    fun getConnectedNodeCount(): Int = peerChannels.size
    
    /**
     * Get list of connected node IDs
     */
    fun getConnectedNodes(): Set<String> = peerChannels.keys.toSet()
    
    /**
     * Shutdown the connection manager
     */
    suspend fun shutdown() = withContext(Dispatchers.IO) {
        try {
            // Close all peer connections
            peerChannels.values.forEach { it.close() }
            peerChannels.clear()
            
            // Close server
            serverChannel?.close()?.sync()
            
            // Shutdown event loops
            workerGroup?.shutdownGracefully()?.sync()
            bossGroup?.shutdownGracefully()?.sync()
            
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Error during direct connection shutdown", e)
        }
    }
    
    companion object {
        val NODE_ID_KEY: io.netty.util.AttributeKey<String> = 
            io.netty.util.AttributeKey.valueOf("nodeId")
    }
}

/**
 * Netty handler for direct connection messages
 */
class DirectMessageHandler(
    private val manager: DirectConnectionManager,
    private val isServer: Boolean
) : ChannelInboundHandlerAdapter() {
    
    private var handshakeComplete = false
    
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val buf = msg as ByteBuf
        
        try {
            val data = ByteArray(buf.readableBytes())
            buf.readBytes(data)
            
            if (!handshakeComplete) {
                // Check for handshake
                if (data.size > DirectConnectionManager.HANDSHAKE_PREFIX.size &&
                    data.sliceArray(0 until DirectConnectionManager.HANDSHAKE_PREFIX.size)
                        .contentEquals(DirectConnectionManager.HANDSHAKE_PREFIX)) {
                    
                    val nodeId = String(data.sliceArray(
                        DirectConnectionManager.HANDSHAKE_PREFIX.size until data.size
                    ))
                    
                    manager.handleHandshake(ctx.channel(), nodeId)
                    handshakeComplete = true
                    
                    // If we're the server, send our handshake back
                    if (isServer) {
                        val response = DirectConnectionManager.HANDSHAKE_PREFIX + 
                            Portal.nodeId.toByteArray()
                        ctx.writeAndFlush(Unpooled.wrappedBuffer(response))
                    }
                    
                    return
                }
            }
            
            // Regular message
            manager.handleMessage(data)
            
        } finally {
            buf.release()
        }
    }
    
    override fun channelInactive(ctx: ChannelHandlerContext) {
        manager.handleDisconnect(ctx.channel())
        super.channelInactive(ctx)
    }
    
    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is IdleStateEvent) {
            // Send keepalive or close connection
            if (handshakeComplete) {
                // Could send a ping here
            } else {
                // Handshake timeout
                ctx.close()
            }
        }
        super.userEventTriggered(ctx, evt)
    }
    
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Portal.instance.logger.log(Level.WARNING, "Direct connection error", cause)
        ctx.close()
    }
}
