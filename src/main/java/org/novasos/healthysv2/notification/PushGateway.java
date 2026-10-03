package org.novasos.healthysv2.notification;

import java.util.UUID;

interface PushGateway {
    boolean enabled();
    Result send(String token, UUID notificationId, String locale);
    enum Outcome { ACCEPTED, TRANSIENT_FAILURE, UNREGISTERED, PERMANENT_FAILURE }
    record Result(Outcome outcome, String messageId) {}
}
