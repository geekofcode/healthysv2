package org.novasos.healthysv2.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Metadata-only lock-screen notification. Clinical content stays behind recipient-authorized REST. */
@Component
class FcmPushGateway implements PushGateway {
    private final boolean configured;
    private final String project;
    private final String credentialsFile;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private GoogleCredentials credentials;
    FcmPushGateway(@Value("${healthys.push.enabled:false}") boolean enabled,
        @Value("${healthys.push.fcm-project-id:}") String project,
        @Value("${healthys.push.service-account-file:}") String credentialsFile,ObjectMapper json) {
        this.configured=enabled&&!project.isBlank()&&!credentialsFile.isBlank();this.project=project;this.credentialsFile=credentialsFile;this.json=json;
        if(enabled&&!configured) throw new IllegalStateException("FCM project and service account file are required when push is enabled");
        if(configured) {
            if(!project.matches("[a-z][a-z0-9-]{4,61}[a-z0-9]")) throw new IllegalStateException("Invalid Firebase project ID");
            try(InputStream input=Files.newInputStream(Path.of(credentialsFile))) {credentials=com.google.auth.oauth2.ServiceAccountCredentials.fromStream(input).createScoped("https://www.googleapis.com/auth/firebase.messaging");}
            catch(Exception exception) {throw new IllegalStateException("Cannot load FCM service account credentials");}
        }
    }
    public boolean enabled() {return configured;}
    public Result send(String token,UUID notification,String locale) {
        if(!enabled()) return new Result(Outcome.PERMANENT_FAILURE,null);
        try {
            String body=json.writeValueAsString(payload(token,notification,locale));
            HttpRequest request=HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/"+project+"/messages:send"))
                .timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+accessToken()).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            return classify(response.statusCode(),response.body(),json);
        } catch(InterruptedException exception) {Thread.currentThread().interrupt();return new Result(Outcome.TRANSIENT_FAILURE,null);}
        catch(Exception exception) {return new Result(Outcome.TRANSIENT_FAILURE,null);}
    }
    static Result classify(int status,String body,ObjectMapper json) {
        if(status==429||status>=500||status==401) return new Result(Outcome.TRANSIENT_FAILURE,null);
        try {
            var parsed=json.readTree(body);
            if(status>=200&&status<300) {
                String id=parsed.path("name").asText(null);
                return id==null||id.isBlank()?new Result(Outcome.TRANSIENT_FAILURE,null):new Result(Outcome.ACCEPTED,id);
            }
            for(var detail:parsed.path("error").path("details")) {
                if("UNREGISTERED".equals(detail.path("errorCode").asText())) return new Result(Outcome.UNREGISTERED,null);
            }
            return new Result(Outcome.PERMANENT_FAILURE,null);
        } catch(Exception exception) {return new Result(status>=200&&status<300?Outcome.TRANSIENT_FAILURE:Outcome.PERMANENT_FAILURE,null);}
    }
    private synchronized String accessToken() throws java.io.IOException {
        if(credentials==null) {
            try(InputStream input=Files.newInputStream(Path.of(credentialsFile))) {credentials=com.google.auth.oauth2.ServiceAccountCredentials.fromStream(input).createScoped("https://www.googleapis.com/auth/firebase.messaging");}
        }
        credentials.refreshIfExpired();return credentials.getAccessToken().getTokenValue();
    }
    static Map<String,Object> payload(String token,UUID id,String locale) {
        String body="fr".equals(locale)?"Vous avez une nouvelle notification.":"You have a new notification.";
        return Map.of("message",Map.of("token",token,"notification",Map.of("title","HEALTH'YS","body",body),
            "data",Map.of("notificationId",id.toString()),
            "android",Map.of("priority","NORMAL","notification",Map.of("channel_id","healthys_notifications","visibility","PRIVATE")),
            "apns",Map.of("headers",Map.of("apns-push-type","alert","apns-priority","10"),"payload",Map.of("aps",Map.of("sound","default")))));
    }
}
