package org.novasos.healthysv2.pharmacy.api;
import java.math.*;import java.time.*;import java.util.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;
public final class PharmacyDtos{private PharmacyDtos(){}
 public record CreatePrescriptionRequest(@NotNull UUID patientId,UUID consultationId,@NotNull UUID prescriberId,UUID organizationId,@Future Instant expiresAt,@NotEmpty List<@Valid PrescriptionItemRequest>items){}
 public record PrescriptionItemRequest(@NotNull UUID medicationCatalogId,@NotBlank@Size(max=100)String dosage,@NotBlank@Size(max=100)String frequency,@NotBlank@Size(max=100)String route,@NotBlank@Size(max=100)String duration,@NotNull@DecimalMin("0.001")BigDecimal quantity,String instructions){}
 public record CreateDispenseRequest(@NotNull UUID pharmacyOrganizationId,@NotNull UUID pharmacistId,@NotEmpty List<@Valid DispenseItemRequest>items){}
 public record DispenseItemRequest(@NotNull UUID prescriptionItemId,@NotNull@DecimalMin("0.001")BigDecimal quantityDispensed,@NotBlank@Size(max=100)String batchNumber){}
 public record ReplenishStockRequest(@NotNull UUID organizationId,@NotNull UUID medicationCatalogId,@NotBlank@Size(max=100)String batchNumber,@NotNull@DecimalMin("0.001")BigDecimal quantity,@FutureOrPresent LocalDate expirationDate){}
 public record PrescriptionSummary(UUID id,String prescriptionNumber,UUID patientId,UUID organizationId,Instant prescribedAt,Instant expiresAt,String status){}
 public record PrescriptionResponse(UUID id,String prescriptionNumber,UUID patientId,UUID consultationId,UUID prescriberId,UUID organizationId,Instant prescribedAt,Instant expiresAt,String status,List<PrescriptionItemResponse>items,List<DispenseResponse>dispenses){}
 public record PrescriptionItemResponse(UUID id,UUID medicationCatalogId,String medicationCode,String medicationName,String dosage,String frequency,String route,String duration,BigDecimal quantity,BigDecimal quantityDispensed,BigDecimal quantityRemaining,String instructions){}
 public record DispenseResponse(UUID id,String dispenseNumber,UUID pharmacyOrganizationId,UUID pharmacistId,Instant dispensedAt,String status,List<DispenseItemResponse>items){}
 public record DispenseItemResponse(UUID id,UUID prescriptionItemId,BigDecimal quantityDispensed,String batchNumber){}
 public record MedicationStockResponse(UUID id,UUID organizationId,UUID medicationCatalogId,String medicationCode,String medicationName,String batchNumber,BigDecimal quantity,LocalDate expirationDate,boolean expired){}
 public record MedicationCatalogResponse(UUID id,String code,String name,String genericName,String form,String strength,String atcCode){}
}
