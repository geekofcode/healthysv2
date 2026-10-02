package org.novasos.healthysv2.professional.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class ProfessionalDtos {
    private ProfessionalDtos() {}

    public record ProfessionalRequest(@NotNull UUID personId,
            @NotBlank @Size(max=50) String professionalNumber,
            @NotBlank @Size(max=50) String professionalType,
            @Size(max=30) String status) {}
    public record ProfessionalUpdateRequest(@NotBlank @Size(max=50) String professionalType,
            @Size(max=30) String status) {}
    public record LicenseRequest(@NotBlank @Size(max=100) String licenseNumber,
            @Size(max=255) String issuingAuthority, UUID countryId,
            LocalDate issuedAt, LocalDate expiresAt, @Size(max=30) String status) {
        @AssertTrue(message="{validation.professional.license-dates}")
        public boolean isDateRangeValid() { return issuedAt == null || expiresAt == null || !expiresAt.isBefore(issuedAt); }
    }
    public record SpecialityRequest(@NotNull UUID specialityCatalogId, boolean primary) {}
    public record AssignmentRequest(@NotNull UUID organizationId, UUID departmentId,
            UUID serviceId, @Size(max=150) String position,
            @Size(max=100) String employeeNumber, @NotNull LocalDate startDate,
            LocalDate endDate, @Size(max=30) String status) {
        @AssertTrue(message="{validation.professional.assignment-dates}")
        public boolean isDateRangeValid() { return startDate == null || endDate == null || !endDate.isBefore(startDate); }
        @AssertTrue(message="{validation.professional.service-department}")
        public boolean isServiceAttachedToDepartment() { return serviceId == null || departmentId != null; }
    }
    public record ScheduleRequest(@NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotNull LocalTime startTime, @NotNull LocalTime endTime,
            @NotNull @Positive Integer slotDurationMinutes) {
        @AssertTrue(message="{validation.professional.schedule-times}")
        public boolean isTimeRangeValid() { return startTime == null || endTime == null || endTime.isAfter(startTime); }
    }
    public record AvailabilityRequest(@NotNull Instant startAt, @NotNull Instant endAt,
            @NotBlank @Size(max=30) String availabilityType, @Size(max=30) String status) {
        @AssertTrue(message="{validation.professional.availability-times}")
        public boolean isTimeRangeValid() { return startAt == null || endAt == null || endAt.isAfter(startAt); }
    }

    public record ProfessionalSummary(UUID id, UUID personId, String professionalNumber,
            String professionalType, String status) {}
    public record ProfessionalResponse(UUID id, UUID personId, String professionalNumber,
            String professionalType, String status, Instant createdAt, Instant updatedAt,
            List<LicenseResponse> licenses, List<SpecialityResponse> specialities,
            List<AssignmentResponse> assignments) {}
    public record LicenseResponse(UUID id, String licenseNumber, String issuingAuthority,
            UUID countryId, LocalDate issuedAt, LocalDate expiresAt, String status) {}
    public record SpecialityResponse(UUID specialityCatalogId, String code, String name,
            boolean primary) {}
    public record AssignmentResponse(UUID id, UUID organizationId, UUID departmentId,
            UUID serviceId, String position, String employeeNumber, LocalDate startDate,
            LocalDate endDate, String status, List<ScheduleResponse> schedules,
            List<AvailabilityResponse> availabilities) {}
    public record ScheduleResponse(UUID id, int dayOfWeek, LocalTime startTime,
            LocalTime endTime, int slotDurationMinutes) {}
    public record AvailabilityResponse(UUID id, Instant startAt, Instant endAt,
            String availabilityType, String status) {}
    public record SpecialityCatalogResponse(UUID id, String code, String name) {}
}
