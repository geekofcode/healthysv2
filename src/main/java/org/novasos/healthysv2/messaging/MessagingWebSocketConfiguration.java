package org.novasos.healthysv2.messaging;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.novasos.healthysv2.notification.NotificationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
class MessagingWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final ObjectProvider<JwtDecoder> decoder;
    private final ObjectProvider<JwtAuthenticationConverter> converter;
    private final ObjectProvider<ConversationService> conversations;
    private final ObjectProvider<NotificationService> notifications;
    private final Map<String,Authentication> sessions=new ConcurrentHashMap<>();
    private final String[] origins;

    MessagingWebSocketConfiguration(ObjectProvider<JwtDecoder> decoder,ObjectProvider<JwtAuthenticationConverter> converter,ObjectProvider<ConversationService> conversations,ObjectProvider<NotificationService> notifications,@Value("${healthys.security.cors.allowed-origins:http://localhost:5173}")String origins){
        this.decoder=decoder;this.converter=converter;this.conversations=conversations;this.notifications=notifications;
        this.origins=Arrays.stream(origins.split(",")).map(String::trim).toArray(String[]::new);
    }
    @Bean ThreadPoolTaskScheduler messagingHeartbeatScheduler(){var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("messaging-heartbeat-");return scheduler;}
    @Override public void registerStompEndpoints(StompEndpointRegistry registry){registry.addEndpoint("/ws").setAllowedOrigins(origins);}
    @Override public void configureMessageBroker(MessageBrokerRegistry registry){registry.setApplicationDestinationPrefixes("/app");registry.enableSimpleBroker("/topic").setTaskScheduler(messagingHeartbeatScheduler()).setHeartbeatValue(new long[]{10000,10000});}
    @Override public void configureClientInboundChannel(ChannelRegistration registration){registration.interceptors(inboundInterceptor());}
    @Override public void configureClientOutboundChannel(ChannelRegistration registration){registration.interceptors(outboundInterceptor());}

    ChannelInterceptor inboundInterceptor(){return new ChannelInterceptor(){@Override public Message<?> preSend(Message<?> message,MessageChannel channel){
        var accessor=MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);
        if(accessor==null)throw new AccessDeniedException("STOMP_REQUIRED");
        var command=accessor.getCommand();
        if(StompCommand.CONNECT.equals(command)){
            String header=accessor.getFirstNativeHeader("Authorization");
            JwtDecoder jwtDecoder=decoder.getIfAvailable();var authenticationConverter=converter.getIfAvailable();
            if(header==null||!header.startsWith("Bearer ")||jwtDecoder==null||authenticationConverter==null)throw new AccessDeniedException("JWT_REQUIRED");
            Authentication authentication=authenticationConverter.convert(jwtDecoder.decode(header.substring(7)));
            requireValid(authentication);conversations.getObject().personId(authentication);
            accessor.setUser(authentication);
            if(accessor.getSessionId()!=null)sessions.put(accessor.getSessionId(),authentication);
        }else if(StompCommand.DISCONNECT.equals(command)){
            if(accessor.getSessionId()!=null)sessions.remove(accessor.getSessionId());
        }else if(StompCommand.SUBSCRIBE.equals(command)||StompCommand.SEND.equals(command)){
            Authentication authentication=authentication(accessor);requireValid(authentication);
            if(StompCommand.SUBSCRIBE.equals(command))authorizeTopic(authentication,accessor.getDestination());
            else authorizeSend(authentication,accessor.getDestination());
        }else if(command!=null&&command!=StompCommand.UNSUBSCRIBE){throw new AccessDeniedException("STOMP_COMMAND_NOT_ALLOWED");}
        return message;
    }};}

    ChannelInterceptor outboundInterceptor(){return new ChannelInterceptor(){@Override public Message<?> preSend(Message<?> message,MessageChannel channel){
        var accessor=StompHeaderAccessor.wrap(message);
        if(accessor.getMessageType()==SimpMessageType.MESSAGE){
            try{var authentication=authentication(accessor);requireValid(authentication);authorizeTopic(authentication,accessor.getDestination());}
            catch(AccessDeniedException exception){return null;}
        }
        return message;
    }};}

    @EventListener void disconnected(SessionDisconnectEvent event){sessions.remove(event.getSessionId());}

    private Authentication authentication(StompHeaderAccessor accessor){
        if(accessor.getUser() instanceof Authentication authentication)return authentication;
        Authentication authentication=accessor.getSessionId()==null?null:sessions.get(accessor.getSessionId());
        if(authentication==null)throw new AccessDeniedException("JWT_REQUIRED");return authentication;
    }
    private void requireValid(Authentication authentication){
        if(!(authentication instanceof JwtAuthenticationToken jwt)||!authentication.isAuthenticated()||jwt.getToken().getExpiresAt()==null||!jwt.getToken().getExpiresAt().isAfter(Instant.now()))throw new AccessDeniedException("SESSION_EXPIRED");
    }
    private void authorizeSend(Authentication authentication,String destination){
        UUID id=parse(destination,"/app/conversations/","/messages");
        if(!conversations.getObject().maySubscribe(authentication,id))throw new AccessDeniedException("CONVERSATION_ACCESS_DENIED");
    }
    private void authorizeTopic(Authentication authentication,String destination){
        if(destination!=null&&destination.startsWith("/topic/messaging/users/")){
            UUID id=parse(destination,"/topic/messaging/users/","");
            if(!id.equals(conversations.getObject().personId(authentication)))throw new AccessDeniedException("MESSAGING_USER_ACCESS_DENIED");
        }else if(destination!=null&&destination.startsWith("/topic/conversations/")){
            String tail=destination.substring("/topic/conversations/".length());
            UUID id=parse(destination,"/topic/conversations/",tail.endsWith("/receipts")?"/receipts":"");
            if(!conversations.getObject().maySubscribe(authentication,id))throw new AccessDeniedException("CONVERSATION_ACCESS_DENIED");
        }else if(destination!=null&&destination.startsWith("/topic/notifications/")){
            UUID id=parse(destination,"/topic/notifications/","");
            if(!notifications.getObject().maySubscribe(authentication,id))throw new AccessDeniedException("NOTIFICATION_ACCESS_DENIED");
        }else throw new AccessDeniedException("DESTINATION_NOT_ALLOWED");
    }
    private UUID parse(String destination,String prefix,String suffix){
        if(destination==null||!destination.startsWith(prefix)||!destination.endsWith(suffix))throw new AccessDeniedException("DESTINATION_NOT_ALLOWED");
        String value=destination.substring(prefix.length(),destination.length()-suffix.length());
        try{UUID id=UUID.fromString(value);if(!id.toString().equalsIgnoreCase(value))throw new IllegalArgumentException();return id;}
        catch(IllegalArgumentException exception){throw new AccessDeniedException("DESTINATION_NOT_ALLOWED");}
    }
}
