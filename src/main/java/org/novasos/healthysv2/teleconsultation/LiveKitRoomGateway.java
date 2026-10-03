package org.novasos.healthysv2.teleconsultation;

import java.io.IOException;
import java.time.Duration;
import io.livekit.server.RoomServiceClient;
import org.springframework.stereotype.Service;

/** Server credentials never leave the backend. Production LiveKit must disable room.auto_create. */
@Service
class LiveKitRoomGateway {
    private final LiveKitProperties properties;
    LiveKitRoomGateway(LiveKitProperties properties){this.properties=properties;}
    void create(String room){
        try {if(!client().createRoom(room).execute().isSuccessful())throw new IllegalStateException("Video provider unavailable");}
        catch(IOException exception){throw new IllegalStateException("Video provider unavailable");}
    }
    boolean delete(String room){
        try {var response=client().deleteRoom(room).execute();return response.isSuccessful()||response.code()==404;}
        catch(IOException|RuntimeException exception){return false;}
    }
    private RoomServiceClient client(){
        properties.validate();
        return RoomServiceClient.createClient(properties.getUrl(),properties.getApiKey(),properties.getApiSecret(),
            ()->new okhttp3.OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(5)).callTimeout(Duration.ofSeconds(8)).build(),false);
    }
}
