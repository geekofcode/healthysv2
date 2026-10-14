package org.novasos.healthysv2.hospital.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrganizationDtos {
  private OrganizationDtos() {}

  public record OrganizationRequest(
      @NotBlank @Size(max = 50) String number,
      @NotBlank @Size(max = 255) String name,
      @Size(max = 255) String legalName,
      UUID organizationTypeId,
      @Size(max = 50) String phone,
      @Email @Size(max = 255) String email,
      @Size(max = 255) String website,
      @Size(max = 30) String status,
      @Size(max = 100) String timezone,
      UUID administratorPersonId,
      @jakarta.validation.Valid AdministratorAccount administratorAccount) {}

  public record AdministratorAccount(
      @NotBlank @Size(max = 120) String firstName,
      @NotBlank @Size(max = 120) String lastName,
      @NotBlank @Email @Size(max = 254) String email) {}

  public record OrganizationUpdateRequest(
      @NotBlank @Size(max = 255) String name,
      @Size(max = 255) String legalName,
      UUID organizationTypeId,
      @Size(max = 50) String phone,
      @Email @Size(max = 255) String email,
      @Size(max = 255) String website,
      @Size(max = 30) String status,
      @Size(max = 100) String timezone,
      UUID administratorPersonId) {}

  public record OrganizationStatusRequest(
      @NotBlank @jakarta.validation.constraints.Pattern(regexp = "ACTIVE|INACTIVE")
          String status) {}

  public record DepartmentRequest(
      @NotBlank @Size(max = 50) String code,
      @NotBlank @Size(max = 255) String name,
      String description,
      @Size(max = 30) String status) {}

  public record DepartmentUpdateRequest(
      @NotBlank @Size(max = 255) String name, String description, @Size(max = 30) String status) {}

  public record ServiceRequest(
      @NotBlank @Size(max = 50) String code,
      @NotBlank @Size(max = 255) String name,
      String description,
      @Size(max = 30) String status) {}

  public record ServiceUpdateRequest(
      @NotBlank @Size(max = 255) String name, String description, @Size(max = 30) String status) {}

  public record RoomRequest(
      UUID departmentId,
      @NotBlank @Size(max = 50) String roomNumber,
      @Size(max = 50) String type,
      @Size(max = 30) String status) {}

  public record RoomUpdateRequest(
      UUID departmentId, @Size(max = 50) String type, @Size(max = 30) String status) {}

  public record BedRequest(
      @NotBlank @Size(max = 50) String bedNumber, @Size(max = 30) String status) {}

  public record BedUpdateRequest(@Size(max = 30) String status) {}

  public record OrganizationSummary(
      UUID id, String number, String name, String legalName, String status) {}

  public record OrganizationResponse(
      UUID id,
      String number,
      String name,
      String legalName,
      UUID organizationTypeId,
      String phone,
      String email,
      String website,
      String status,
      Instant createdAt,
      Instant updatedAt,
      List<DepartmentResponse> departments,
      List<RoomResponse> rooms,
      String timezone,
      UUID administratorPersonId) {}

  public record DepartmentResponse(
      UUID id,
      String code,
      String name,
      String description,
      String status,
      List<ServiceResponse> services) {}

  public record ServiceResponse(
      UUID id, String code, String name, String description, String status) {}

  public record RoomResponse(
      UUID id,
      UUID departmentId,
      String roomNumber,
      String type,
      String status,
      List<BedResponse> beds) {}

  public record BedResponse(UUID id, String bedNumber, String status) {}
}
