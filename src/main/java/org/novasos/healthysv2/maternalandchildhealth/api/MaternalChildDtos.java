package org.novasos.healthysv2.maternalandchildhealth.api;
import java.math.*;import java.time.*;import java.util.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;

public final class MaternalChildDtos{private MaternalChildDtos(){}
 public record StartPregnancyRequest(@NotNull UUID motherPatientId,LocalDate estimatedConceptionDate,LocalDate lastMenstrualPeriod,@NotNull LocalDate expectedDeliveryDate,@AssertTrue(message="{validation.maternal.pregnancy-dates}")Boolean datesValid){public StartPregnancyRequest{LocalDate start=estimatedConceptionDate!=null?estimatedConceptionDate:lastMenstrualPeriod;datesValid=start==null||expectedDeliveryDate==null||expectedDeliveryDate.isAfter(start);}}
 public record PrenatalVisitRequest(UUID consultationId,UUID professionalId,Instant visitDate,@Min(0)@Max(45)Integer gestationalAgeWeeks,@DecimalMin("0.1")BigDecimal weight,@Min(40)@Max(300)Integer systolicPressure,@Min(20)@Max(200)Integer diastolicPressure,@Min(50)@Max(250)Integer fetalHeartRate,String notes){}
 public record PregnancyRiskRequest(@NotBlank @Size(max=150)String riskType,@Pattern(regexp="LOW|MODERATE|HIGH|CRITICAL",flags=Pattern.Flag.CASE_INSENSITIVE)String severity,String notes){}
 public record NewbornRequest(@NotNull UUID childPatientId,@Min(1)Integer birthOrder,@DecimalMin("0.1")BigDecimal birthWeight,@DecimalMin("0.1")BigDecimal birthHeight,@DecimalMin("0.1")BigDecimal headCircumference,@Min(0)@Max(10)Short apgar1,@Min(0)@Max(10)Short apgar5){}
 public record DeliveryRequest(UUID organizationId,@NotNull Instant deliveryDate,@NotBlank @Size(max=50)String deliveryType,UUID professionalId,String complications,String notes,@NotEmpty List<@Valid NewbornRequest>newborns){}
 public record PostpartumVisitRequest(UUID professionalId,Instant visitDate,String notes,@Size(max=30)String status){}
 public record VaccinationRequest(@NotNull UUID vaccineCatalogId,@Min(1)Integer doseNumber,Instant administeredAt,UUID administeredBy,UUID organizationId,@Size(max=100)String batchNumber,LocalDate nextDueDate,@Size(max=30)String status){}
 public record GrowthMeasurementRequest(Instant measuredAt,@DecimalMin("0.1")BigDecimal weight,@DecimalMin("0.1")BigDecimal height,@DecimalMin("0.1")BigDecimal headCircumference,UUID measuredBy,@AssertTrue(message="{validation.maternal.growth-values}")Boolean hasValue){public GrowthMeasurementRequest{hasValue=weight!=null||height!=null||headCircumference!=null;}}
 public record PrenatalVisitResponse(UUID id,UUID consultationId,UUID professionalId,Instant visitDate,Integer gestationalAgeWeeks,BigDecimal weight,Integer systolicPressure,Integer diastolicPressure,Integer fetalHeartRate,String notes){}
 public record PregnancyRiskResponse(UUID id,String riskType,String severity,Instant identifiedAt,String notes,String status){}
 public record NewbornResponse(UUID id,UUID childPatientId,Integer birthOrder,BigDecimal birthWeight,BigDecimal birthHeight,BigDecimal headCircumference,Short apgar1,Short apgar5,String status){}
 public record DeliveryResponse(UUID id,UUID organizationId,Instant deliveryDate,String deliveryType,UUID professionalId,String complications,String notes,List<NewbornResponse>newborns){}
 public record PostpartumVisitResponse(UUID id,UUID professionalId,Instant visitDate,String notes,String status){}
 public record PregnancySummary(UUID id,String pregnancyNumber,UUID motherPatientId,LocalDate expectedDeliveryDate,String status,Instant createdAt){}
 public record PregnancyResponse(UUID id,String pregnancyNumber,UUID motherPatientId,LocalDate estimatedConceptionDate,LocalDate lastMenstrualPeriod,LocalDate expectedDeliveryDate,String status,Instant createdAt,List<PrenatalVisitResponse>prenatalVisits,List<PregnancyRiskResponse>risks,DeliveryResponse delivery,List<PostpartumVisitResponse>postpartumVisits){}
 public record VaccinationResponse(UUID id,UUID vaccineCatalogId,String vaccineCode,String vaccineName,Integer doseNumber,Instant administeredAt,UUID administeredBy,UUID organizationId,String batchNumber,LocalDate nextDueDate,String status){}
 public record GrowthMeasurementResponse(UUID id,Instant measuredAt,BigDecimal weight,BigDecimal height,BigDecimal headCircumference,BigDecimal bmi,UUID measuredBy){}
 public record ChildHealthRecordResponse(UUID id,UUID childPatientId,UUID motherPatientId,Instant createdAt,String status,List<VaccinationResponse>vaccinations,List<GrowthMeasurementResponse>growthMeasurements){}
}
