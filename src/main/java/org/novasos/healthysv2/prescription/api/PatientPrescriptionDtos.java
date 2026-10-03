package org.novasos.healthysv2.prescription.api;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public final class PatientPrescriptionDtos {
 private PatientPrescriptionDtos() {}
 public record Summary(UUID id,String prescriptionNumber,UUID patientId,UUID consultationId,UUID prescriberId,String prescriberName,UUID organizationId,String organizationName,Instant prescribedAt,Instant expiresAt,String status,boolean expired) {}
 public record Detail(Summary prescription,List<Item> items,List<Dispensation> dispensations) {}
 public record Item(UUID id,UUID medicationCatalogId,String medicationCode,String medicationName,String genericName,String form,String strength,String dosage,String frequency,String route,String duration,BigDecimal quantity,BigDecimal quantityDispensed,BigDecimal quantityRemaining,String instructions) {}
 public record Dispensation(UUID id,String dispenseNumber,UUID pharmacyOrganizationId,String pharmacyName,Instant dispensedAt,String status,List<DispensationItem> items) {}
 public record DispensationItem(UUID id,UUID prescriptionItemId,String medicationName,BigDecimal quantityDispensed) {}
}
