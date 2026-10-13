package org.novasos.healthysv2.document;

import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.novasos.healthysv2.care.api.VisitReportReader.ValidatedVisitReport;
import org.novasos.healthysv2.prescription.api.CareVisitPrescriptionDtos.ValidatedPrescription;
import org.springframework.stereotype.Component;

/** Paginated printable PDFs from immutable, already validated clinical snapshots. */
@Component
class CareVisitPdf {
    byte[] report(ValidatedVisitReport report,String patientName,String hospitalName) {
        var text=header("Rapport de consultation",report,patientName,hospitalName);
        section(text,"Motif",report.reason());section(text,"Observations",report.observations());
        section(text,"Diagnostic",report.diagnosis());section(text,"Traitement",report.treatment());
        section(text,"Suivi",report.followUp());return render(text);
    }
    byte[] prescription(ValidatedVisitReport report,ValidatedPrescription prescription,String patientName,String hospitalName) {
        var text=header("Ordonnance médicale",report,patientName,hospitalName);
        text.add("Ordonnance validée : révision "+prescription.revision()+" — "+prescription.validatedAt());
        for(int index=0;index<prescription.items().size();index++) {
            var item=prescription.items().get(index);text.add("");
            text.add((index+1)+". "+item.medication());text.add("Posologie : "+item.dosage());
            text.add("Voie : "+item.route()+" — Fréquence : "+item.frequency());
            text.add("Durée : "+item.duration());
            if(item.instructions()!=null&&!item.instructions().isBlank()) text.add("Instructions : "+item.instructions());
        }
        text.add("");text.add("Document issu d'une prescription validée. Impression destinée au patient.");
        return render(text);
    }
    private List<String> header(String title,ValidatedVisitReport report,String patientName,String hospitalName) {
        var lines=new ArrayList<String>();lines.add("HEALTH’YS — "+title);lines.add(hospitalName);lines.add("");
        lines.add("Patient : "+patientName);lines.add("Médecin : "+report.doctorName());
        lines.add("Visite : "+report.visitId());lines.add("Épisode : "+report.episodeId());
        lines.add("Rapport validé : révision "+report.revision()+" — "+report.validatedAt());return lines;
    }
    private void section(List<String> lines,String label,String content) {
        lines.add("");lines.add(label+" :");lines.add(content==null||content.isBlank()?"—":content);
    }
    byte[] render(List<String> paragraphs) {
        try(var document=new PDDocument();var fontStream=getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if(fontStream==null) throw new IllegalStateException("PDF font is unavailable");
            var font=PDType0Font.load(document,fontStream);
            var lines=new ArrayList<String>();for(String paragraph:paragraphs)lines.addAll(wrap(paragraph,font));
            int perPage=46;
            for(int offset=0;offset<lines.size();offset+=perPage) {
                var page=new PDPage(PDRectangle.A4);document.addPage(page);
                try(var content=new PDPageContentStream(document,page)) {
                    content.beginText();content.setFont(font,10);content.setLeading(16);
                    content.newLineAtOffset(45,PDRectangle.A4.getHeight()-50);
                    for(String line:lines.subList(offset,Math.min(offset+perPage,lines.size()))) {
                        content.showText(line);content.newLine();
                    }
                    content.endText();content.beginText();content.setFont(font,8);
                    content.newLineAtOffset(45,25);content.showText("HEALTH’YS • document médical confidentiel • page "+document.getNumberOfPages());content.endText();
                }
            }
            var out=new ByteArrayOutputStream();document.save(out);return out.toByteArray();
        }catch(IOException e){throw new IllegalStateException("Cannot generate medical PDF",e);}
    }
    private List<String> wrap(String input,PDType0Font font) throws IOException {
        var result=new ArrayList<String>();
        for(String paragraph:input.replace('\t',' ').replace('\r','\n').split("\\n",-1)) {
            var line=new StringBuilder();
            for(int index=0;index<paragraph.length();) {
                int code=paragraph.codePointAt(index);index+=Character.charCount(code);
                if(Character.isISOControl(code))continue;
                String character=new String(Character.toChars(code));
                // Reject unsupported characters explicitly; never silently alter clinical text.
                if(font.getStringWidth(line+character)*.01f>505 && !line.isEmpty()) {
                    result.add(line.toString());line.setLength(0);
                }
                line.append(character);
            }
            result.add(line.toString());
        }
        return result;
    }
}
