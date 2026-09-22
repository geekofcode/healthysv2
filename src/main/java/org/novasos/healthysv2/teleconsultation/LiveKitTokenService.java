package org.novasos.healthysv2.teleconsultation;

import static org.novasos.healthysv2.teleconsultation.api.TeleconsultationDtos.*;
import java.time.*;import java.util.*;
import io.livekit.server.*;import org.springframework.stereotype.Service;

@Service
class LiveKitTokenService {
    private final LiveKitProperties properties;LiveKitTokenService(LiveKitProperties properties){this.properties=properties;}
    JoinTokenResponse create(UUID personId,String room,String role){properties.validate();long ttl=Duration.ofMinutes(properties.getTokenTtlMinutes()).toMillis();var token=new AccessToken(properties.getApiKey(),properties.getApiSecret());token.setIdentity(personId.toString());token.setMetadata("{\"role\":\""+role+"\"}");token.setTtl(ttl);token.addGrants(new RoomJoin(true),new RoomName(room));return new JoinTokenResponse(properties.getUrl(),room,token.toJwt(),Instant.now().plusMillis(ttl));}
}
