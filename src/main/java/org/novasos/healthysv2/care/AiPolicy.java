package org.novasos.healthysv2.care;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
record AiPolicy(
    boolean enabled,
    String serviceUrl,
    String serviceToken,
    Duration timeout,
    Duration deadline,
    Duration audioTtl,
    int maxAttempts,
    long maxAudioBytes) {
  AiPolicy(
      @Value("${healthys.ai.enabled:false}") boolean enabled,
      @Value("${healthys.ai.service-url:http://ai:8000}") String serviceUrl,
      @Value("${healthys.ai.service-token:}") String serviceToken,
      @Value("${healthys.ai.timeout:PT60S}") Duration timeout,
      @Value("${healthys.ai.job-deadline:PT15M}") Duration deadline,
      @Value("${healthys.ai.audio-ttl:PT1H}") Duration audioTtl,
      @Value("${healthys.ai.max-attempts:3}") int maxAttempts,
      @Value("${healthys.ai.max-audio-bytes:10485760}") long maxAudioBytes) {
    if (timeout.isNegative()
        || timeout.isZero()
        || deadline.compareTo(timeout) < 0
        || audioTtl.isNegative()
        || audioTtl.isZero()
        || maxAttempts < 1
        || maxAudioBytes < 1
        || maxAudioBytes > 24_000_000)
      throw new IllegalArgumentException("Invalid AI worker limits");
    if (enabled && (serviceToken == null || serviceToken.length() < 32))
      throw new IllegalArgumentException("AI service token must contain at least 32 characters");
    this.enabled = enabled;
    this.serviceUrl = serviceUrl;
    this.serviceToken = serviceToken;
    this.timeout = timeout;
    this.deadline = deadline;
    this.audioTtl = audioTtl;
    this.maxAttempts = maxAttempts;
    this.maxAudioBytes = maxAudioBytes;
  }
}
