package org.novasos.healthysv2.teleconsultation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "healthys.livekit")
class LiveKitProperties {
    private String url;
    private String apiKey;
    private String apiSecret;
    private long tokenTtlMinutes = 60;

    public String getUrl(){return url;} public void setUrl(String value){url=value;}
    public String getApiKey(){return apiKey;} public void setApiKey(String value){apiKey=value;}
    public String getApiSecret(){return apiSecret;} public void setApiSecret(String value){apiSecret=value;}
    public long getTokenTtlMinutes(){return tokenTtlMinutes;} public void setTokenTtlMinutes(long value){tokenTtlMinutes=value;}

    void validate(){
        if(url==null||url.isBlank()||apiKey==null||apiKey.isBlank()||apiSecret==null||apiSecret.isBlank())
            throw new IllegalStateException("LiveKit is not configured");
        if(tokenTtlMinutes<1||tokenTtlMinutes>360)throw new IllegalStateException("Invalid LiveKit token TTL");
    }
}
