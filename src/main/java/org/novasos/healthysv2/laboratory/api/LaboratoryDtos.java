package org.novasos.healthysv2.laboratory.api;
import java.math.*;import java.time.*;import java.util.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;
public final class LaboratoryDtos{private LaboratoryDtos(){}
 public record CreateOrderRequest(@NotNull UUID patientId,UUID consultationId,@NotNull UUID orderingProfessionalId,UUID laboratoryOrganizationId,@NotBlank String priority,@NotEmpty List<@Valid OrderItemRequest>items){}
 public record OrderItemRequest(@NotNull UUID labExamCatalogId,String instructions){}
 public record CollectSpecimenRequest(@NotBlank @Size(max=100)String specimenNumber,@NotBlank @Size(max=100)String specimenType,Instant collectedAt,@NotNull UUID collectedBy){}
 public record CreateResultRequest(@NotNull UUID performedBy,Instant performedAt,String notes){}
 public record ResultItemRequest(@NotNull UUID labOrderItemId,UUID parameterCatalogId,@Size(max=255)String parameter,@NotBlank @Size(max=500)String value,@Size(max=80)String unit,BigDecimal referenceMin,BigDecimal referenceMax,String interpretation,@Pattern(regexp="NORMAL|LOW|HIGH|CRITICAL",flags=Pattern.Flag.CASE_INSENSITIVE)String abnormalFlag,@AssertTrue(message="{validation.laboratory.reference-range}")Boolean rangeValid,@AssertTrue(message="{validation.laboratory.parameter}")Boolean parameterDefined){public ResultItemRequest{rangeValid=referenceMin==null||referenceMax==null||referenceMax.compareTo(referenceMin)>=0;parameterDefined=parameterCatalogId!=null||(parameter!=null&&!parameter.isBlank());}}
 public record ValidateResultRequest(@NotNull UUID validatedBy){}
 public record OrderSummary(UUID id,String orderNumber,UUID patientId,UUID laboratoryOrganizationId,String priority,Instant orderedAt,String status){}
 public record LabOrderResponse(UUID id,String orderNumber,UUID patientId,UUID consultationId,UUID orderingProfessionalId,UUID laboratoryOrganizationId,String priority,Instant orderedAt,String status,List<LabOrderItemResponse>items,List<LabResultResponse>results){}
 public record LabOrderItemResponse(UUID id,UUID labExamCatalogId,String examCode,String examName,String instructions,String status,List<SpecimenResponse>specimens){}
 public record SpecimenResponse(UUID id,String specimenNumber,String specimenType,Instant collectedAt,UUID collectedBy,String status){}
 public record LabResultResponse(UUID id,String resultNumber,UUID performedBy,UUID validatedBy,Instant performedAt,Instant validatedAt,String status,String notes,List<LabResultItemResponse>items){}
 public record LabResultItemResponse(UUID id,UUID labOrderItemId,UUID parameterCatalogId,String parameter,String value,String unit,BigDecimal referenceMin,BigDecimal referenceMax,String interpretation,String abnormalFlag){}
}
