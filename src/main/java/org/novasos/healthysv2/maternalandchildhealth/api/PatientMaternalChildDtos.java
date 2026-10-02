package org.novasos.healthysv2.maternalandchildhealth.api;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.novasos.healthysv2.maternalandchildhealth.api.MaternalChildDtos.PregnancySummary;
/** Explicit patient projections: private clinical narrative is never serialized. */
public final class PatientMaternalChildDtos {
 private PatientMaternalChildDtos() {}
 public record Prenatal(UUID id,Instant visitDate,Integer gestationalAgeWeeks,BigDecimal weightKg,Integer systolicPressure,Integer diastolicPressure,Integer fetalHeartRate) {}
 public record Newborn(UUID id,UUID childPatientId,Integer birthOrder,BigDecimal birthWeightKg,BigDecimal birthHeightCm,BigDecimal headCircumferenceCm,Short apgar1,Short apgar5,String status) {}
 public record Delivery(UUID id,Instant deliveryDate,String deliveryType,List<Newborn> newborns) {}
 public record PregnancyDetail(PregnancySummary pregnancy,LocalDate estimatedConceptionDate,LocalDate lastMenstrualPeriod,List<Prenatal> prenatalVisits,Delivery delivery) {}
 public record ChildSummary(UUID id,UUID childPatientId,String firstName,String lastName,LocalDate dateOfBirth,String sex,String status,Instant createdAt) {}
 public record Birth(Instant deliveryDate,String deliveryType,Integer birthOrder,BigDecimal birthWeightKg,BigDecimal birthHeightCm,BigDecimal headCircumferenceCm,Short apgar1,Short apgar5,String status) {}
 public record Vaccination(UUID id,UUID vaccineCatalogId,String vaccineCode,String vaccineName,Integer doseNumber,Instant administeredAt,LocalDate nextDueDate,String status) {}
 public record Growth(UUID id,Instant measuredAt,BigDecimal weightKg,BigDecimal heightCm,BigDecimal headCircumferenceCm,BigDecimal bmi) {}
 public record ChildDetail(ChildSummary child,Birth birth,List<Vaccination> vaccinations,List<Growth> growthMeasurements) {}
}
