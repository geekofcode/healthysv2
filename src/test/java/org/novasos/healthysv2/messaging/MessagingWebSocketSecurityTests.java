package org.novasos.healthysv2.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.notification.NotificationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;

class MessagingWebSocketSecurityTests {
    private static final UUID CONVERSATION=UUID.randomUUID();
    private ConversationService conversations;
    private MessagingWebSocketConfiguration configuration;
    private JwtAuthenticationToken current;

    @BeforeEach void setup(){
        conversations=mock(ConversationService.class);
        when(conversations.maySubscribe(any(),eq(CONVERSATION))).thenReturn(true);
        current=authentication(Instant.now().plusSeconds(300));
        configuration=new MessagingWebSocketConfiguration(provider(mock(JwtDecoder.class)),provider(new JwtAuthenticationConverter()),provider(conversations),provider(mock(NotificationService.class)),"http://localhost:5173");
    }
    @Test void acceptsOnlyExactMemberTopicsAndApplicationSend(){
        assertThat(configuration.inboundInterceptor().preSend(frame(StompCommand.SUBSCRIBE,"/topic/conversations/"+CONVERSATION,current),null)).isNotNull();
        assertThat(configuration.inboundInterceptor().preSend(frame(StompCommand.SUBSCRIBE,"/topic/conversations/"+CONVERSATION+"/receipts",current),null)).isNotNull();
        assertThat(configuration.inboundInterceptor().preSend(frame(StompCommand.SEND,"/app/conversations/"+CONVERSATION+"/messages",current),null)).isNotNull();
        for(String destination:List.of("/topic/**","/topic/conversations/*","/topic/conversations/"+CONVERSATION+"/**","/topic/conversations/"+CONVERSATION+"/unexpected","/unknown")){
            assertThatThrownBy(()->configuration.inboundInterceptor().preSend(frame(StompCommand.SUBSCRIBE,destination,current),null)).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(()->configuration.inboundInterceptor().preSend(frame(StompCommand.SEND,"/topic/conversations/"+CONVERSATION,current),null)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void deniesExpiredInboundAndRevokedMembership(){
        var expired=authentication(Instant.now().minusSeconds(1));
        for(var command:List.of(StompCommand.SEND,StompCommand.SUBSCRIBE)){
            assertThatThrownBy(()->configuration.inboundInterceptor().preSend(frame(command,"/topic/conversations/"+CONVERSATION,expired),null)).isInstanceOf(AccessDeniedException.class);
        }
        when(conversations.maySubscribe(any(),any())).thenReturn(false);
        assertThatThrownBy(()->configuration.inboundInterceptor().preSend(frame(StompCommand.SUBSCRIBE,"/topic/conversations/"+CONVERSATION,current),null)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void filtersActualBrokerMessagesAfterExpiryOrMembershipRevocation(){
        assertThat(configuration.outboundInterceptor().preSend(brokerMessage(current),null)).isNotNull();
        assertThat(configuration.outboundInterceptor().preSend(brokerMessage(authentication(Instant.now().minusSeconds(1))),null)).isNull();
        when(conversations.maySubscribe(any(),any())).thenReturn(false);
        assertThat(configuration.outboundInterceptor().preSend(brokerMessage(current),null)).isNull();
    }
    @Test void rejectsLinkedParticipantsWithoutMessagingRole(){
        var noRole=new JwtAuthenticationToken(current.getToken(),List.of());
        for(var command:List.of(StompCommand.SEND,StompCommand.SUBSCRIBE)){
            assertThatThrownBy(()->configuration.inboundInterceptor().preSend(frame(command,"/topic/conversations/"+CONVERSATION,noRole),null)).isInstanceOf(AccessDeniedException.class).hasMessage("MESSAGING_ROLE_REQUIRED");
        }
        assertThat(configuration.outboundInterceptor().preSend(brokerMessage(noRole),null)).isNull();
        JwtDecoder jwtDecoder=mock(JwtDecoder.class);when(jwtDecoder.decode("unprivileged")).thenReturn(noRole.getToken());
        var deniedConfiguration=new MessagingWebSocketConfiguration(provider(jwtDecoder),provider(new JwtAuthenticationConverter()),provider(conversations),provider(mock(NotificationService.class)),"http://localhost:5173");
        var headers=StompHeaderAccessor.create(StompCommand.CONNECT);headers.addNativeHeader("Authorization","Bearer unprivileged");headers.setSessionId("denied");headers.setLeaveMutable(true);
        assertThatThrownBy(()->deniedConfiguration.inboundInterceptor().preSend(MessageBuilder.createMessage(new byte[0],headers.getMessageHeaders()),null)).isInstanceOf(AccessDeniedException.class).hasMessage("MESSAGING_ROLE_REQUIRED");
    }
    private Message<byte[]> brokerMessage(JwtAuthenticationToken authentication){
        var headers=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);headers.setDestination("/topic/conversations/"+CONVERSATION);headers.setUser(authentication);headers.setSessionId("session");
        return MessageBuilder.createMessage(new byte[0],headers.getMessageHeaders());
    }
    private Message<byte[]> frame(StompCommand command,String destination,JwtAuthenticationToken authentication){
        var headers=StompHeaderAccessor.create(command);headers.setDestination(destination);headers.setUser(authentication);headers.setSessionId("session");headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0],headers.getMessageHeaders());
    }
    private JwtAuthenticationToken authentication(Instant expiry){
        var jwt=Jwt.withTokenValue("test").header("alg","RS256").subject(UUID.randomUUID().toString()).issuedAt(Instant.now().minusSeconds(300)).expiresAt(expiry).build();return new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
    }
    @SuppressWarnings("unchecked") private <T>ObjectProvider<T> provider(T value){ObjectProvider<T> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(value);when(provider.getIfAvailable()).thenReturn(value);return provider;}
}
