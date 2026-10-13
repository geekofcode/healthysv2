package org.novasos.healthysv2.care;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class PhysicianQueueDtos {
    private PhysicianQueueDtos() {}
    public record Visit(UUID visitId,UUID episodeId,UUID patientId,String patientFirstName,
            String patientLastName,Instant scheduledAt,String status,String presence,long position,long visitVersion) {}
    public record Calendar(UUID professionalId,LocalDate date,String timezone,long version,List<Visit> visits) {}
    public record Presence(@NotNull UUID visitId,boolean present,@NotNull @Min(0) Long expectedVersion) {}
    public record Next(@NotNull UUID professionalId,@NotNull LocalDate date,@NotNull @Min(0) Long expectedVersion) {}
    public record Order(@NotNull UUID professionalId,@NotNull LocalDate date,
            @NotNull List<@NotNull UUID> visitIds,@NotNull @Min(0) Long expectedVersion) {}
}
