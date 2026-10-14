package org.novasos.healthysv2.profilecontract;

import java.io.*;
import java.sql.Timestamp;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.stereotype.Component;

@Component
class ContractPdf {
  byte[] render(Map<String, Object> agreement) {
    try (var document = new PDDocument();
        var fontStream = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")) {
      if (fontStream == null) throw new IllegalStateException("CONTRACT_FONT_UNAVAILABLE");
      var font = PDType0Font.load(document, fontStream);
      var lines = new ArrayList<String>();
      lines.addAll(wrap("HEALTH’YS - Contrat du profil", font));
      lines.add("");
      lines.addAll(wrap("Profil : " + agreement.get("profile_label"), font));
      lines.addAll(
          wrap(
              "Modèle : version "
                  + agreement.get("template_version")
                  + " - "
                  + agreement.get("category"),
              font));
      if (!Objects.toString(agreement.get("organization_name"), "").isBlank())
        lines.addAll(wrap("Établissement : " + agreement.get("organization_name"), font));
      lines.add("");
      lines.addAll(wrap(Objects.toString(agreement.get("body_snapshot"), ""), font));
      var proof = new ArrayList<String>();
      proof.add("Acceptation et signature");
      proof.add("");
      proof.addAll(wrap("Signataire : " + agreement.get("signer_name"), font));
      proof.addAll(wrap("Adresse vérifiée : " + agreement.get("signer_email"), font));
      proof.addAll(wrap("Profil : " + agreement.get("profile_label"), font));
      if (Boolean.TRUE.equals(agreement.get("guardian_acknowledgement"))) {
        proof.add("Signature du représentant légal pour le patient mineur :");
        proof.addAll(wrap(Objects.toString(agreement.get("patient_name"), ""), font));
      }
      proof.addAll(wrap("Date : " + ((Timestamp) agreement.get("signed_at")).toInstant(), font));
      proof.add("Conditions acceptées : version " + agreement.get("terms_version"));
      proof.addAll(wrap(Objects.toString(agreement.get("terms_url"), ""), font));

      appendPages(document, font, lines);
      appendPages(document, font, proof);
      var signaturePage = new PDPage(PDRectangle.A4);
      document.addPage(signaturePage);
      try (var content = new PDPageContentStream(document, signaturePage)) {
        var signatureLabels = new ArrayList<String>();
        signatureLabels.add("Signature du contrat");
        signatureLabels.add("");
        signatureLabels.addAll(wrap("Signataire : " + agreement.get("signer_name"), font));
        signatureLabels.addAll(wrap("Profil : " + agreement.get("profile_label"), font));
        signatureLabels.addAll(
            wrap("Date : " + ((Timestamp) agreement.get("signed_at")).toInstant(), font));
        if (Boolean.TRUE.equals(agreement.get("guardian_acknowledgement")))
          signatureLabels.addAll(
              wrap("Représentant légal de : " + agreement.get("patient_name"), font));
        write(content, font, signatureLabels, 48, 790, 10, 17);
        // The signature has its own fixed region, separate from the contract and proof text.
        content.setLineWidth(0.8f);
        content.addRect(48, 270, 499, 150);
        content.stroke();
        write(content, font, List.of("Signature manuscrite"), 60, 400, 10, 14);
        var image = ImageIO.read(new ByteArrayInputStream((byte[]) agreement.get("signature_png")));
        float scale = Math.min(460f / image.getWidth(), 105f / image.getHeight());
        content.drawImage(
            LosslessFactory.createFromImage(document, image),
            65,
            285,
            image.getWidth() * scale,
            image.getHeight() * scale);
        var hashes = new ArrayList<String>();
        hashes.add("Identifiant du contrat : " + agreement.get("id"));
        hashes.add("Empreinte du contenu :");
        hashes.add(Objects.toString(agreement.get("snapshot_sha256"), ""));
        hashes.add("Empreinte de signature :");
        hashes.add(Objects.toString(agreement.get("signature_sha256"), ""));
        write(content, font, hashes, 48, 235, 8, 15);
        footer(content, font, document.getNumberOfPages());
      }
      var info = document.getDocumentInformation();
      info.setTitle("Contrat HEALTH’YS");
      info.setAuthor("HEALTH’YS");
      var calendar =
          GregorianCalendar.from(
              ((Timestamp) agreement.get("signed_at"))
                  .toInstant()
                  .atZone(java.time.ZoneOffset.UTC));
      info.setCreationDate(calendar);
      info.setModificationDate(calendar);
      var out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("CONTRACT_PDF_FAILED", e);
    }
  }

  private void appendPages(PDDocument document, PDType0Font font, List<String> lines)
      throws IOException {
    int perPage = 43;
    for (int offset = 0; offset < lines.size(); offset += perPage) {
      var page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      try (var content = new PDPageContentStream(document, page)) {
        write(
            content,
            font,
            lines.subList(offset, Math.min(offset + perPage, lines.size())),
            48,
            790,
            10,
            16);
        footer(content, font, document.getNumberOfPages());
      }
    }
  }

  private void footer(PDPageContentStream content, PDType0Font font, int number)
      throws IOException {
    write(content, font, List.of("HEALTH’YS - Contrat privé - Page " + number), 48, 25, 8, 12);
  }

  private void write(
      PDPageContentStream content,
      PDType0Font font,
      List<String> lines,
      float x,
      float y,
      int size,
      int leading)
      throws IOException {
    content.beginText();
    content.setFont(font, size);
    content.setLeading(leading);
    content.newLineAtOffset(x, y);
    for (String line : lines) {
      content.showText(line);
      content.newLine();
    }
    content.endText();
  }

  private List<String> wrap(String input, PDType0Font font) throws IOException {
    var lines = new ArrayList<String>();
    for (String paragraph : input.replace('\r', '\n').replace('\t', ' ').split("\\n", -1)) {
      var line = new StringBuilder();
      for (int offset = 0; offset < paragraph.length(); ) {
        int code = paragraph.codePointAt(offset);
        offset += Character.charCount(code);
        if (Character.isISOControl(code)) continue;
        String character = new String(Character.toChars(code));
        if (font.getStringWidth(line + character) * .01f > 495 && !line.isEmpty()) {
          lines.add(line.toString());
          line.setLength(0);
        }
        line.append(character);
      }
      lines.add(line.toString());
    }
    return lines;
  }
}
