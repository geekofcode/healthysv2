package org.novasos.healthysv2.appointment.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class PatientAppointmentDtos {
    private PatientAppointmentDtos() {}
    public record PatientAppointmentResponse(UUID id, String appointmentNumber, UUID patientId,
            UUID professionalId, String professionalName, UUID organizationId, String organizationName,
            String type, Instant scheduledStart, Instant scheduledEnd, String reason, String status,
            long version, boolean canCancel, boolean canReschedule) {}
    public record BookingOptions(List<OrganizationOption> organizations, List<ProfessionalOption> professionals) {}
    public record OrganizationOption(UUID id, String name) {}
    public record ProfessionalOption(UUID id, UUID organizationId, String name, String professionalType) {}
    public record AvailableSlots(List<AvailableSlot> slots) {}
    public record AvailableSlot(Instant scheduledStart, Instant scheduledEnd) {}
    public record PatientBookingRequest(@NotNull UUID organizationId, @NotNull UUID professionalId,
            @NotNull Instant scheduledStart, @NotNull Instant scheduledEnd, @Size(max=2000) String reason) {}
    public record PatientRescheduleRequest(@NotNull Instant scheduledStart, @NotNull Instant scheduledEnd,
            @Size(max=2000) String reason) {}
    public record PatientCancelRequest(@Size(max=2000) String reason) {}
}
