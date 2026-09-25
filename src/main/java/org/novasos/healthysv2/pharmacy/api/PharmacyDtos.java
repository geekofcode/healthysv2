package org.novasos.healthysv2.pharmacy.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class PharmacyDtos {
    private PharmacyDtos() {}

    public record CreateDispenseRequest(@NotNull UUID pharmacyOrganizationId, @NotNull UUID pharmacistId,
                                        @NotEmpty List<@Valid DispenseItemRequest> items) {}
    public record DispenseItemRequest(@NotNull UUID prescriptionItemId,
                                      @NotNull @DecimalMin("0.001") BigDecimal quantityDispensed,
                                      @NotBlank @Size(max = 100) String batchNumber) {}
    public record ReplenishStockRequest(@NotNull UUID organizationId, @NotNull UUID medicationCatalogId,
                                        @NotBlank @Size(max = 100) String batchNumber,
                                        @NotNull @DecimalMin("0.001") BigDecimal quantity,
                                        @FutureOrPresent LocalDate expirationDate) {}
    public record DispenseResponse(UUID id, String dispenseNumber, UUID pharmacyOrganizationId,
                                   UUID pharmacistId, Instant dispensedAt, String status,
                                   List<DispenseItemResponse> items) {}
    public record DispenseItemResponse(UUID id, UUID prescriptionItemId,
                                       BigDecimal quantityDispensed, String batchNumber) {}
    public record MedicationStockResponse(UUID id, UUID organizationId, UUID medicationCatalogId,
                                          String medicationCode, String medicationName, String batchNumber,
                                          BigDecimal quantity, LocalDate expirationDate, boolean expired) {}
    public record MedicationCatalogResponse(UUID id, String code, String name, String genericName,
                                            String form, String strength, String atcCode) {}
}
