package com.ittools.interact.service;

import com.ittools.classroom.service.LessonService;
import com.ittools.platform.security.AppUserDetails;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课堂实时通道（STOMP over WebSocket，端点 /ws，仅同源）。
 * - /topic/lesson.{id}          全班（广播、抢答开始/结束、求助处理结果）
 * - /topic/lesson.{id}.teacher  仅本堂课教师（新求助、抢答提交、讨论发言、在线状态）
 * 订阅时校验：会话用户必须是该课堂的教师或所在班级的学生；.teacher 频道仅限教师。
 * 客户端不向服务器发送业务消息，所有操作走带 CSRF 的 REST 接口。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final Pattern DEST = Pattern.compile("^/topic/lesson\\.(\\d+)(\\.teacher)?$");
    private final LessonService lessons;

    public WebSocketConfig(LessonService lessons) {
        this.lessons = lessons;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (acc == null) return message;
                if (StompCommand.SEND.equals(acc.getCommand())) {
                    throw new IllegalArgumentException("不接受客户端消息");
                }
                if (StompCommand.SUBSCRIBE.equals(acc.getCommand())) {
                    AppUserDetails u = user(acc.getUser());
                    if (u == null) throw new IllegalArgumentException("未登录");
                    Matcher m = DEST.matcher(acc.getDestination() == null ? "" : acc.getDestination());
                    if (!m.matches()) throw new IllegalArgumentException("无效频道");
                    Long lessonId = Long.parseLong(m.group(1));
                    boolean teacherOnly = m.group(2) != null;
                    switch (u.role()) {
                        case TEACHER:
                            lessons.requireTeacher(u.userId(), lessonId);
                            break;
                        case STUDENT:
                            if (teacherOnly) throw new IllegalArgumentException("无权订阅");
                            lessons.requireStudent(u.userId(), lessonId);
                            break;
                        default:
                            throw new IllegalArgumentException("无权订阅");
                    }
                }
                return message;
            }
        });
    }

    private static AppUserDetails user(Principal p) {
        if (p instanceof Authentication && ((Authentication) p).getPrincipal() instanceof AppUserDetails) {
            return (AppUserDetails) ((Authentication) p).getPrincipal();
        }
        return null;
    }
}
