package org.novasos.healthysv2.laboratory.api;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public final class PatientLaboratoryDtos {
 private PatientLaboratoryDtos() {}
 public record ResultSummary(UUID id,String resultNumber,UUID labOrderId,String orderNumber,UUID patientId,UUID laboratoryOrganizationId,String laboratoryName,Instant orderedAt,Instant performedAt,Instant validatedAt,String status) {}
 public record ResultDetail(ResultSummary result,List<ResultItem> items) {}
 public record ResultItem(UUID id,UUID labOrderItemId,String examCode,String examName,UUID parameterCatalogId,String parameter,String value,String unit,BigDecimal referenceMin,BigDecimal referenceMax,String interpretation,String abnormalFlag) {}
}
