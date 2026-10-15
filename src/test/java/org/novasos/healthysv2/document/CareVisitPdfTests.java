package org.novasos.healthysv2.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.care.api.VisitReportReader.ValidatedVisitReport;
import org.novasos.healthysv2.prescription.api.CareVisitPrescriptionDtos.*;

class CareVisitPdfTests {
  private ValidatedVisitReport report(String observations) {
    return new ValidatedVisitReport(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Dr Émilie Noël",
        2,
        UUID.randomUUID(),
        Instant.parse("2026-01-01T12:00:00Z"),
        "Toux sèche",
        observations,
        "État stable",
        "Repos et hydratation",
        "Réévaluation si nécessaire");
  }

  @Test
  void reportIsParseableAndPreservesFrenchAndSourceRevision() throws Exception {
    byte[] bytes =
        new CareVisitPdf()
            .report(report("Température : 37 °C"), "François Lévesque", "Hôpital Santé");
    try (var pdf = Loader.loadPDF(bytes)) {
      String text = new PDFTextStripper().getText(pdf);
      assertThat(text)
          .contains(
              "François Lévesque",
              "Émilie Noël",
              "Température : 37 °C",
              "révision 2",
              "Diagnostic");
      assertThat(pdf.getNumberOfPages()).isEqualTo(1);
    }
  }

  @Test
  void reportPrintsExaminationRequestsFromItsValidatedSnapshot() throws Exception {
    var source = report("Examen normal");
    var snapshot =
        new ValidatedVisitReport(
            source.visitId(),
            source.episodeId(),
            source.patientId(),
            source.organizationId(),
            source.professionalId(),
            source.doctorName(),
            source.revision(),
            source.revisionId(),
            source.validatedAt(),
            source.reason(),
            source.observations(),
            source.diagnosis(),
            source.treatment(),
            source.followUp(),
            null,
            true,
            List.of(
                new org.novasos.healthysv2.care.api.VisitReportReader.ExamRequest(
                    "Numération sanguine", "À réaliser demain"),
                new org.novasos.healthysv2.care.api.VisitReportReader.ExamRequest(
                    "Radiographie thoracique", null)));
    byte[] bytes = new CareVisitPdf().report(snapshot, "Patient", "Hôpital");
    try (var pdf = Loader.loadPDF(bytes)) {
      assertThat(new PDFTextStripper().getText(pdf))
          .contains(
              "Examens demandés",
              "Numération sanguine",
              "À réaliser demain",
              "Radiographie thoracique",
              "révision 2");
    }
  }

  @Test
  void longMedicalReportPaginatesWithoutDiscardingEnding() throws Exception {
    byte[] bytes =
        new CareVisitPdf()
            .report(
                report("Observation clinique répétée. ".repeat(700) + "FIN DU RAPPORT"),
                "Patient",
                "Hôpital");
    try (var pdf = Loader.loadPDF(bytes)) {
      assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
      assertThat(new PDFTextStripper().getText(pdf)).contains("FIN DU RAPPORT");
    }
  }

  @Test
  void prescriptionIncludesMedicationAndCompleteDosageInstructions() throws Exception {
    var report = report("Examen normal");
    var prescription =
        new ValidatedPrescription(
            report.visitId(),
            UUID.randomUUID(),
            3,
            report.validatedAt(),
            List.of(
                new Item(
                    "Paracétamol",
                    "500 mg",
                    "orale",
                    "toutes les 8 heures",
                    "3 jours",
                    "Après le repas")));
    byte[] bytes = new CareVisitPdf().prescription(report, prescription, "Patient", "Hôpital");
    try (var pdf = Loader.loadPDF(bytes)) {
      assertThat(new PDFTextStripper().getText(pdf))
          .contains(
              "Paracétamol",
              "500 mg",
              "orale",
              "toutes les 8 heures",
              "3 jours",
              "Après le repas",
              "révision 3");
    }
  }
}
