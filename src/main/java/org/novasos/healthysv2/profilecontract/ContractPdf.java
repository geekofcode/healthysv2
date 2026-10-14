package org.novasos.healthysv2.profilecontract;

import java.io.*;
import java.sql.Timestamp;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;
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
      boolean html = "HTML".equals(agreement.get("body_format"));
      if (!html) lines.addAll(wrap(Objects.toString(agreement.get("body_snapshot"), ""), font));
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

      if (html)
        appendHtmlPages(
            document, font, lines, Objects.toString(agreement.get("body_snapshot"), ""));
      else appendPages(document, font, lines);
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

  private void appendHtmlPages(
      PDDocument document, PDType0Font font, List<String> header, String html) throws IOException {
    var parser = new RichParser();
    new ParserDelegator().parse(new StringReader(ContractHtml.sanitize(html)), parser, true);
    parser.flush();
    try (var pages = new RichPages(document, font)) {
      for (String line : header)
        pages.paragraph(new Paragraph(List.of(new Span(line, Style.PLAIN)), 10, 0));
      for (Block block : parser.blocks) {
        if (block instanceof Paragraph paragraph) pages.paragraph(paragraph);
        else if (block instanceof Table table) pages.table(table);
      }
    }
  }

  private interface Block {}

  private record Style(boolean bold, boolean italic, boolean underline) {
    static final Style PLAIN = new Style(false, false, false);
  }

  private record Span(String text, Style style) {}

  private record Paragraph(List<Span> spans, float size, float indent) implements Block {}

  private record Table(List<List<List<Span>>> rows) implements Block {}

  private static class ListContext {
    final boolean ordered;
    int item;

    ListContext(boolean ordered) {
      this.ordered = ordered;
    }
  }

  /** Parses only the supported document structure; no URL, CSS or image is ever resolved. */
  private static class RichParser extends HTMLEditorKit.ParserCallback {
    final List<Block> blocks = new ArrayList<>();
    final Deque<ListContext> lists = new ArrayDeque<>();
    List<Span> flow = new ArrayList<>(), cell;
    List<List<List<Span>>> rows;
    List<List<Span>> row;
    int bold, italic, underline;
    float size = 10, indent;
    boolean suppressed;
    int suppressedDepth;

    Style style() {
      return new Style(bold > 0, italic > 0, underline > 0);
    }

    void text(String value) {
      if (!suppressed && !value.isEmpty())
        (cell == null ? flow : cell).add(new Span(value, style()));
    }

    public void flush() {
      if (!flow.isEmpty()) {
        blocks.add(new Paragraph(List.copyOf(flow), size, indent));
        flow = new ArrayList<>();
      }
    }

    boolean heading(String tag) {
      return tag.matches("h[1-6]");
    }

    @Override
    public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int position) {
      String name = tag.toString();
      if (Set.of("script", "style", "iframe", "object").contains(name)) {
        suppressedDepth++;
        suppressed = true;
        return;
      }
      if (suppressed) return;
      if (Set.of("strong", "b").contains(name)) bold++;
      else if (Set.of("em", "i").contains(name)) italic++;
      else if (Set.of("u", "a").contains(name)) underline++;
      else if (name.equals("table")) {
        flush();
        rows = new ArrayList<>();
      } else if (name.equals("tr")) {
        row = new ArrayList<>();
      } else if (Set.of("td", "th").contains(name)) {
        cell = new ArrayList<>();
        if (name.equals("th")) bold++;
      } else if (cell != null && (name.equals("p") || heading(name))) {
        if (!cell.isEmpty()) text("\n");
      } else if (heading(name)) {
        flush();
        size =
            switch (name) {
              case "h1" -> 18;
              case "h2" -> 16;
              case "h3" -> 14;
              default -> 12;
            };
        bold++;
      } else if (name.equals("ul") || name.equals("ol")) {
        flush();
        lists.push(new ListContext(name.equals("ol")));
        indent = 16 * lists.size();
      } else if (name.equals("li")) {
        flush();
        indent = 16 * lists.size();
        var list = lists.peek();
        text(list != null && list.ordered ? (++list.item) + ". " : "• ");
      } else if (name.equals("blockquote")) {
        flush();
        indent += 18;
      } else if (name.equals("p") || name.equals("div")) {
        flush();
      }
    }

    @Override
    public void handleEndTag(HTML.Tag tag, int position) {
      String name = tag.toString();
      if (Set.of("script", "style", "iframe", "object").contains(name)) {
        suppressedDepth = Math.max(0, suppressedDepth - 1);
        suppressed = suppressedDepth > 0;
        return;
      }
      if (suppressed) return;
      if (Set.of("strong", "b").contains(name)) bold = Math.max(0, bold - 1);
      else if (Set.of("em", "i").contains(name)) italic = Math.max(0, italic - 1);
      else if (Set.of("u", "a").contains(name)) underline = Math.max(0, underline - 1);
      else if (Set.of("td", "th").contains(name)) {
        if (row != null && cell != null) row.add(List.copyOf(cell));
        cell = null;
        if (name.equals("th")) bold = Math.max(0, bold - 1);
      } else if (name.equals("tr")) {
        if (rows != null && row != null && !row.isEmpty()) rows.add(List.copyOf(row));
        row = null;
      } else if (name.equals("table")) {
        if (rows != null && !rows.isEmpty()) blocks.add(new Table(List.copyOf(rows)));
        rows = null;
      } else if (cell != null && (name.equals("p") || heading(name))) {
        text("\n");
      } else if (heading(name)) {
        flush();
        size = 10;
        bold = Math.max(0, bold - 1);
      } else if (name.equals("ul") || name.equals("ol")) {
        flush();
        if (!lists.isEmpty()) lists.pop();
        indent = 16 * lists.size();
      } else if (name.equals("blockquote")) {
        flush();
        indent = Math.max(0, indent - 18);
      } else if (name.equals("p") || name.equals("div") || name.equals("li")) {
        flush();
      }
    }

    @Override
    public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attrs, int position) {
      if (tag == HTML.Tag.BR) text("\n");
    }

    @Override
    public void handleText(char[] chars, int position) {
      text(new String(chars).replaceAll("[\\t\\r\\n ]+", " "));
    }
  }

  /**
   * A body paginator owns only its pages; proof and handwritten signature keep their existing
   * pages.
   */
  private class RichPages implements AutoCloseable {
    final PDDocument document;
    final PDType0Font font;
    PDPageContentStream content;
    float y;

    RichPages(PDDocument document, PDType0Font font) throws IOException {
      this.document = document;
      this.font = font;
      nextPage();
    }

    void nextPage() throws IOException {
      finishPage();
      var page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      content = new PDPageContentStream(document, page);
      y = 790;
    }

    void finishPage() throws IOException {
      if (content != null) {
        content.beginText();
        content.setRenderingMode(RenderingMode.FILL);
        content.endText();
        footer(content, font, document.getNumberOfPages());
        content.close();
        content = null;
      }
    }

    void ensure(float height) throws IOException {
      if (y - height < 64) nextPage();
    }

    void paragraph(Paragraph paragraph) throws IOException {
      float indent = Math.min(140, paragraph.indent()), leading = paragraph.size() + 6;
      for (var line : wrapped(paragraph.spans(), paragraph.size(), 495 - indent)) {
        ensure(leading);
        draw(line, 48 + indent, y, paragraph.size());
        y -= leading;
      }
      y -= paragraph.size() > 10 ? 7 : 4;
    }

    void table(Table table) throws IOException {
      int columns = table.rows().stream().mapToInt(List::size).max().orElse(1);
      float columnWidth = 495f / columns, padding = 5, size = 9, leading = 13;
      for (var row : table.rows()) {
        var cells = new ArrayList<List<List<Span>>>();
        int totalLines = 1;
        for (int column = 0; column < columns; column++) {
          var lines =
              wrapped(
                  column < row.size() ? row.get(column) : List.of(),
                  size,
                  Math.max(1, columnWidth - 2 * padding));
          cells.add(lines);
          totalLines = Math.max(totalLines, lines.size());
        }
        for (int offset = 0; offset < totalLines; ) {
          ensure(leading + 2 * padding);
          int capacity = Math.max(1, (int) ((y - 64 - 2 * padding) / leading));
          int count = Math.min(capacity, totalLines - offset);
          float height = count * leading + 2 * padding;
          for (int column = 0; column < columns; column++) {
            float x = 48 + column * columnWidth;
            content.setLineWidth(.5f);
            content.addRect(x, y - height, columnWidth, height);
            content.stroke();
            var lines = cells.get(column);
            for (int index = 0; index < count && offset + index < lines.size(); index++)
              draw(
                  lines.get(offset + index),
                  x + padding,
                  y - padding - size - index * leading,
                  size);
          }
          y -= height;
          offset += count;
          if (offset < totalLines) nextPage();
        }
      }
      y -= 10;
    }

    void draw(List<Span> spans, float x, float baseline, float size) throws IOException {
      for (Span span : spans) {
        if (span.text().isEmpty()) continue;
        content.beginText();
        content.setFont(font, size);
        content.setLineWidth(.22f);
        content.setRenderingMode(
            span.style().bold() ? RenderingMode.FILL_STROKE : RenderingMode.FILL);
        content.setTextMatrix(new Matrix(1, 0, span.style().italic() ? .22f : 0, 1, x, baseline));
        content.showText(span.text());
        content.endText();
        float width = width(span.text(), size);
        if (span.style().underline()) {
          content.setLineWidth(.5f);
          content.moveTo(x, baseline - 2);
          content.lineTo(x + width, baseline - 2);
          content.stroke();
        }
        x += width;
      }
    }

    float width(String value, float size) throws IOException {
      return font.getStringWidth(value) * size / 1000f;
    }

    List<List<Span>> wrapped(List<Span> spans, float size, float available) throws IOException {
      var lines = new ArrayList<List<Span>>();
      var line = new ArrayList<Span>();
      float used = 0;
      for (Span span : spans) {
        String value = span.text().replace('\r', '\n').replace('\t', ' ');
        for (String token : value.split("(?<=\\s)|(?=\\s)")) {
          if (token.isEmpty()) continue;
          if (token.equals("\n")) {
            lines.add(List.copyOf(line));
            line.clear();
            used = 0;
            continue;
          }
          String word = token.chars().allMatch(Character::isWhitespace) ? " " : token;
          float tokenWidth = width(word, size);
          if (tokenWidth <= available) {
            if (used + tokenWidth > available && !line.isEmpty()) {
              lines.add(List.copyOf(line));
              line.clear();
              used = 0;
            }
            if (line.isEmpty() && word.equals(" ")) continue;
            line.add(new Span(word, span.style()));
            used += tokenWidth;
          } else {
            for (int at = 0; at < word.length(); ) {
              int cp = word.codePointAt(at);
              at += Character.charCount(cp);
              if (Character.isISOControl(cp)) continue;
              String ch = new String(Character.toChars(cp));
              float w = width(ch, size);
              if (used + w > available && !line.isEmpty()) {
                lines.add(List.copyOf(line));
                line.clear();
                used = 0;
              }
              line.add(new Span(ch, span.style()));
              used += w;
            }
          }
        }
      }
      if (!line.isEmpty() || lines.isEmpty()) lines.add(List.copyOf(line));
      return lines;
    }

    @Override
    public void close() throws IOException {
      finishPage();
    }
  }
}
