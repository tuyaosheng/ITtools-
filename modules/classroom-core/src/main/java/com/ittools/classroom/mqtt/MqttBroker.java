package com.ittools.classroom.mqtt;

import com.ittools.platform.settings.SystemSettingService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * 内置 MQTT 服务器（MQTT 3.1.1，代替单独运行的 SIoT）。
 * - 端口、账号、密码在“系统设置 → 内置 MQTT 服务器”，默认 1883、siot / dfrobot，Mind+ 里物联网平台照样选 SIoT；
 * - 账号或密码不对拒绝连接（第10课小实验2）；
 * - 发布：保存“项目/设备”两级主题的消息（见 {@link MqttMessageService}），并转发给订阅了该主题的客户端（第13课订阅要用），支持 QoS 0/1/2；
 * - 订阅：支持 + 和 # 通配符，保留消息（retain）在订阅时补发。
 */
@Component
public class MqttBroker {
    private static final Logger log = LoggerFactory.getLogger(MqttBroker.class);

    private final SystemSettingService settings;
    private final MqttMessageService messages;
    /** 保存消息走数据库，不能占用 netty 的 IO 线程 */
    private final ExecutorService saver = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mqtt-save");
        t.setDaemon(true);
        return t;
    });

    private EventLoopGroup boss;
    private EventLoopGroup workers;
    private Channel server;
    private volatile int port;
    private volatile String error;

    /** 已连接客户端：channel -> 订阅的主题过滤器 */
    private final Map<Channel, Set<String>> subscriptions = new ConcurrentHashMap<>();
    private final Map<Channel, String> clientIds = new ConcurrentHashMap<>();
    private final Map<String, Retained> retained = new ConcurrentHashMap<>();

    private static final class Retained {
        final byte[] payload;
        final MqttQoS qos;

        Retained(byte[] payload, MqttQoS qos) {
            this.payload = payload;
            this.qos = qos;
        }
    }

    public MqttBroker(SystemSettingService settings, MqttMessageService messages) {
        this.settings = settings;
        this.messages = messages;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (settings.getBool(SystemSettingService.MQTT_ENABLED)) start();
    }

    /** 系统设置里改了开关或端口：重启（账号密码每次连接时读取，改了马上生效）。 */
    @EventListener
    public void onSettingsChanged(com.ittools.platform.settings.SettingsChangedEvent e) {
        if (e.getKeys().contains(SystemSettingService.MQTT_ENABLED) || e.getKeys().contains(SystemSettingService.MQTT_PORT)) {
            boolean enabled = settings.getBool(SystemSettingService.MQTT_ENABLED);
            int p = settings.getInt(SystemSettingService.MQTT_PORT);
            boolean running = server != null;
            if (enabled != running || (running && p != port)) restart();
        }
    }

    public synchronized void start() {
        if (server != null) return;
        int p = settings.getInt(SystemSettingService.MQTT_PORT);
        boss = new NioEventLoopGroup(1);
        workers = new NioEventLoopGroup(2);
        try {
            ServerBootstrap b = new ServerBootstrap()
                    .group(boss, workers)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 256)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new IdleStateHandler(300, 0, 0, TimeUnit.SECONDS))
                                    .addLast(new MqttDecoder(64 * 1024))
                                    .addLast(MqttEncoder.INSTANCE)
                                    .addLast(new Handler());
                        }
                    });
            server = b.bind(p).sync().channel();
            port = p;
            error = null;
            log.info("内置 MQTT 服务器已启动，端口 {}", p);
        } catch (Exception e) {
            error = "端口 " + p + " 启动失败：" + e.getMessage() + "（是不是 SIoT 还开着、占用了这个端口？）";
            log.warn("内置 MQTT 服务器启动失败：{}", error);
            shutdownGroups();
        }
    }

    @PreDestroy
    public synchronized void stop() {
        if (server != null) {
            server.close().awaitUninterruptibly(2000);
            server = null;
        }
        shutdownGroups();
        subscriptions.clear();
        clientIds.clear();
    }

    /** 改了端口或开关后重启（系统设置保存时调用）。 */
    public synchronized void restart() {
        stop();
        error = null;
        if (settings.getBool(SystemSettingService.MQTT_ENABLED)) start();
    }

    private void shutdownGroups() {
        if (boss != null) boss.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        if (workers != null) workers.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        boss = null;
        workers = null;
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", settings.getBool(SystemSettingService.MQTT_ENABLED));
        m.put("running", server != null && server.isActive());
        m.put("port", server != null ? port : settings.getInt(SystemSettingService.MQTT_PORT));
        m.put("username", settings.get(SystemSettingService.MQTT_USERNAME));
        m.put("password", settings.get(SystemSettingService.MQTT_PASSWORD));
        m.put("clients", clientIds.size());
        m.put("error", error);
        return m;
    }

    /** 从网页发布一条消息（老师测试订阅用），与掌控板发布的同样保存和转发。 */
    public void publishFromWeb(String topic, String payload) {
        byte[] data = payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8);
        deliver(topic, data, false);
        messages.save(topic, payload, "网页");
    }

    // ================= 主题匹配 =================

    static boolean matches(String filter, String topic) {
        String[] f = filter.split("/", -1);
        String[] t = topic.split("/", -1);
        for (int i = 0; i < f.length; i++) {
            if ("#".equals(f[i])) return true;
            if (i >= t.length) return false;
            if (!"+".equals(f[i]) && !f[i].equals(t[i])) return false;
        }
        return f.length == t.length;
    }

    private void deliver(String topic, byte[] data, boolean retain) {
        for (Map.Entry<Channel, Set<String>> e : subscriptions.entrySet()) {
            for (String filter : e.getValue()) {
                if (matches(filter, topic)) {
                    sendPublish(e.getKey(), topic, data, false);
                    break;
                }
            }
        }
    }

    private void sendPublish(Channel ch, String topic, byte[] data, boolean retain) {
        if (!ch.isActive()) return;
        MqttPublishMessage msg = MqttMessageBuilders.publish()
                .topicName(topic)
                .qos(MqttQoS.AT_MOST_ONCE)
                .retained(retain)
                .messageId(0)
                .payload(Unpooled.wrappedBuffer(data))
                .build();
        ch.writeAndFlush(msg);
    }

    // ================= 连接处理 =================

    private final class Handler extends SimpleChannelInboundHandler<MqttMessage> {
        private boolean connected;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
            if (msg.decoderResult().isFailure()) {
                ctx.close();
                return;
            }
            MqttMessageType type = msg.fixedHeader().messageType();
            if (!connected && type != MqttMessageType.CONNECT) {
                ctx.close();
                return;
            }
            switch (type) {
                case CONNECT:
                    onConnect(ctx, (MqttConnectMessage) msg);
                    break;
                case PUBLISH:
                    onPublish(ctx, (MqttPublishMessage) msg);
                    break;
                case PUBREL: {
                    int id = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
                    ctx.writeAndFlush(new MqttMessage(new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            MqttMessageIdVariableHeader.from(id)));
                    break;
                }
                case SUBSCRIBE:
                    onSubscribe(ctx, (MqttSubscribeMessage) msg);
                    break;
                case UNSUBSCRIBE: {
                    MqttUnsubscribeMessage u = (MqttUnsubscribeMessage) msg;
                    Set<String> subs = subscriptions.get(ctx.channel());
                    if (subs != null) subs.removeAll(u.payload().topics());
                    ctx.writeAndFlush(new MqttUnsubAckMessage(
                            new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            MqttMessageIdVariableHeader.from(u.variableHeader().messageId())));
                    break;
                }
                case PINGREQ:
                    ctx.writeAndFlush(new MqttMessage(new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0)));
                    break;
                case DISCONNECT:
                    ctx.close();
                    break;
                default:
                    // PUBACK / PUBREC / PUBCOMP：本服务器只用 QoS 0 下发，忽略
                    break;
            }
        }

        private void onConnect(ChannelHandlerContext ctx, MqttConnectMessage m) {
            String user = m.payload().userName();
            byte[] pw = m.payload().passwordInBytes();
            String pass = pw == null ? null : new String(pw, StandardCharsets.UTF_8);
            boolean ok = settings.get(SystemSettingService.MQTT_USERNAME).equals(user)
                    && settings.get(SystemSettingService.MQTT_PASSWORD).equals(pass);
            MqttConnectReturnCode code = ok ? MqttConnectReturnCode.CONNECTION_ACCEPTED
                    : MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD;
            MqttConnAckMessage ack = MqttMessageBuilders.connAck().returnCode(code).sessionPresent(false).build();
            if (!ok) {
                ctx.writeAndFlush(ack).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            connected = true;
            clientIds.put(ctx.channel(), m.payload().clientIdentifier() == null ? "" : m.payload().clientIdentifier());
            subscriptions.put(ctx.channel(), ConcurrentHashMap.newKeySet());
            ctx.writeAndFlush(ack);
        }

        private void onPublish(ChannelHandlerContext ctx, MqttPublishMessage m) {
            String topic = m.variableHeader().topicName();
            ByteBuf buf = m.payload();
            byte[] data = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), data);
            MqttQoS qos = m.fixedHeader().qosLevel();
            int id = m.variableHeader().packetId();
            if (qos == MqttQoS.AT_LEAST_ONCE) {
                ctx.writeAndFlush(new MqttMessage(new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                        MqttMessageIdVariableHeader.from(id)));
            } else if (qos == MqttQoS.EXACTLY_ONCE) {
                ctx.writeAndFlush(new MqttMessage(new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0),
                        MqttMessageIdVariableHeader.from(id)));
            }
            if (m.fixedHeader().isRetain()) {
                if (data.length == 0) retained.remove(topic);
                else retained.put(topic, new Retained(data, qos));
            }
            deliver(topic, data, false);
            String ip = ctx.channel().remoteAddress() instanceof InetSocketAddress
                    ? ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress().getHostAddress() : null;
            String text = new String(data, StandardCharsets.UTF_8);
            saver.submit(() -> {
                try {
                    messages.save(topic, text, ip);
                } catch (RuntimeException e) {
                    log.warn("保存 MQTT 消息失败：{}", e.getMessage());
                }
            });
        }

        private void onSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage m) {
            Set<String> subs = subscriptions.computeIfAbsent(ctx.channel(), k -> ConcurrentHashMap.newKeySet());
            List<Integer> granted = new ArrayList<>();
            List<String> added = new ArrayList<>();
            for (MqttTopicSubscription s : m.payload().topicSubscriptions()) {
                subs.add(s.topicName());
                added.add(s.topicName());
                granted.add(Math.min(s.qualityOfService().value(), 1));
            }
            ctx.writeAndFlush(new MqttSubAckMessage(
                    new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                    MqttMessageIdVariableHeader.from(m.variableHeader().messageId()),
                    new MqttSubAckPayload(granted)));
            for (Map.Entry<String, Retained> r : retained.entrySet()) {
                for (String f : added) {
                    if (matches(f, r.getKey())) {
                        sendPublish(ctx.channel(), r.getKey(), r.getValue().payload, true);
                        break;
                    }
                }
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            subscriptions.remove(ctx.channel());
            clientIds.remove(ctx.channel());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
