package org.novasos.healthysv2.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.SimpleMessageConverter;
import java.nio.charset.StandardCharsets;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys","healthys.security.api-client-id=healthys-backend-apps","healthys.security.cors.allowed-origins=http://localhost:5173"})
@Import({TestcontainersConfiguration.class,MobileMessagingApiIntegrationTests.Fixtures.class})
class MessagingRealtimeIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired SimpUserRegistry users;
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();

    @Test void authenticatesAndReceivesCommittedMessageAndReadReceiptOverRealStomp()throws Exception{
        UUID actor=person("realtime-actor"),other=person("realtime-other"),conversation=UUID.randomUUID();
        jdbc.update("insert into communication.conversation(id,conversation_number,type,created_by) values (?,?,'DIRECT',?)",conversation,"CONV-"+conversation,actor);
        jdbc.update("insert into communication.conversation_participant(conversation_id,person_id) values (?,?),(?,?)",conversation,actor,conversation,other);
        var client=new WebSocketStompClient(new StandardWebSocketClient());client.setMessageConverter(new SimpleMessageConverter());
        StompSession session=null;
        try{
            var headers=new StompHeaders();headers.add("Authorization","Bearer realtime-other");
            session=client.connectAsync("ws://localhost:"+port+"/ws",new WebSocketHttpHeaders(),headers,new StompSessionHandlerAdapter(){}).get(10,TimeUnit.SECONDS);
            var messages=new LinkedBlockingQueue<String>();var receipts=new LinkedBlockingQueue<String>();
            String topic="/topic/conversations/"+conversation;
            session.subscribe(topic,handler(messages));session.subscribe(topic+"/receipts",handler(receipts));
            await().atMost(Duration.ofSeconds(5)).until(()->users.getUsers().stream().flatMap(user->user.getSessions().stream()).flatMap(s->s.getSubscriptions().stream()).filter(subscription->subscription.getDestination().startsWith(topic)).count()==2);
            var http=RestClient.create("http://localhost:"+port);
            String sent=http.post().uri("/api/v1/conversations/"+conversation+"/messages").header(HttpHeaders.AUTHORIZATION,"Bearer realtime-actor").contentType(MediaType.APPLICATION_JSON).body("{\"type\":\"TEXT\",\"content\":\"Realtime delivery\"}").retrieve().body(String.class);
            UUID message=UUID.fromString(json.readTree(sent).path("id").asText());
            String delivered=messages.poll(10,TimeUnit.SECONDS);assertThat(delivered).isNotNull();assertThat(json.readTree(delivered).path("id").asText()).isEqualTo(message.toString());
            assertThat(jdbc.queryForObject("select count(*) from communication.message where id=?",Integer.class,message)).isEqualTo(1);
            http.post().uri("/api/v1/conversations/messages/"+message+"/read").header(HttpHeaders.AUTHORIZATION,"Bearer realtime-other").retrieve().toBodilessEntity();
            String receipt=receipts.poll(10,TimeUnit.SECONDS);assertThat(receipt).isNotNull();assertThat(json.readTree(receipt).path("personId").asText()).isEqualTo(other.toString());
            assertThat(messages).isEmpty();
        }finally{if(session!=null&&session.isConnected())session.disconnect();client.stop();}
    }
    private StompFrameHandler handler(BlockingQueue<String> queue){return new StompFrameHandler(){@Override public Type getPayloadType(StompHeaders headers){return byte[].class;}@Override public void handleFrame(StompHeaders headers,Object payload){queue.add(new String((byte[])payload,StandardCharsets.UTF_8));}};}
    private UUID person(String token){UUID id=UUID.randomUUID(),subject=UUID.randomUUID();jdbc.update("insert into identity.person(id,keycloak_user_id,person_number,first_name,last_name) values (?,?,?,?,?)",id,subject,"PER-"+id,token,"User");MobileMessagingApiIntegrationTests.Fixtures.SUBJECTS.put(token,subject);return id;}
}
