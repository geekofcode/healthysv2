package org.novasos.healthysv2.profilecontract;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.regex.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/** Bounded HTML fragments reconstructed from an allowlist; no CSS, media or network loading. */
public final class ContractHtml {
  private static final Set<String> TAGS =
      Set.of(
          "p",
          "h1",
          "h2",
          "h3",
          "strong",
          "b",
          "em",
          "i",
          "u",
          "ul",
          "ol",
          "li",
          "table",
          "thead",
          "tbody",
          "tr",
          "th",
          "td",
          "br",
          "blockquote",
          "a");
  private static final Pattern ACTIVE =
      Pattern.compile(
          "(?is)<\\s*/?\\s*(script|style|iframe|object|embed|svg|math|form|input|button|select|textarea|link|meta|img|video|audio|source|base|frameset|frame|noscript|template)\\b|<!\\s*(doctype|entity)|<\\?");
  private static final Pattern VARIABLE =
      Pattern.compile(
          "\\{\\{(signerName|profileLabel|organizationName|patientName|signedAt|signature)\\}\\}");

  private ContractHtml() {}

  public static String sanitize(String input) {
    if (input == null || input.length() > 60000)
      throw new IllegalArgumentException("INVALID_CONTRACT_HTML");
    if (ACTIVE.matcher(input).find()) throw new IllegalArgumentException("UNSAFE_CONTRACT_HTML");
    var builder = new SafeFragment();
    parse(input, builder);
    builder.finish();
    if (builder.html.length() > 60000)
      throw new IllegalArgumentException("CONTRACT_HTML_TOO_LARGE");
    return builder.html.toString();
  }

  public static String render(String input, Map<String, String> values) {
    String safe = sanitize(input);
    var matcher = VARIABLE.matcher(safe);
    var out = new StringBuffer();
    while (matcher.find()) {
      String key = matcher.group(1);
      String value = values.get(key);
      matcher.appendReplacement(
          out, Matcher.quoteReplacement(value == null ? matcher.group() : escape(value)));
    }
    matcher.appendTail(out);
    if (out.length() > 60000) throw new IllegalArgumentException("CONTRACT_HTML_TOO_LARGE");
    return out.toString();
  }

  public static String plainText(String html) {
    var text = new StringBuilder();
    parse(
        html,
        new HTMLEditorKit.ParserCallback() {
          @Override
          public void handleText(char[] data, int pos) {
            text.append(data).append(' ');
          }
        });
    return text.toString().replace('\u00a0', ' ').strip();
  }

  public static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private static void parse(String input, HTMLEditorKit.ParserCallback callback) {
    try {
      new ParserDelegator().parse(new StringReader(input), callback, true);
    } catch (IOException e) {
      throw new IllegalArgumentException("INVALID_CONTRACT_HTML");
    }
  }

  private static String safeHref(Object attribute) {
    if (attribute == null) return null;
    String value = attribute.toString();
    if (value.length() > 2048 || value.codePoints().anyMatch(Character::isISOControl)) return null;
    try {
      URI uri = URI.create(value);
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null) return null;
      return value;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static final class SafeFragment extends HTMLEditorKit.ParserCallback {
    private final StringBuilder html = new StringBuilder();
    private final Deque<String> open = new ArrayDeque<>();

    @Override
    public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int pos) {
      start(tag.toString().toLowerCase(Locale.ROOT), attrs);
    }

    @Override
    public void handleEndTag(HTML.Tag tag, int pos) {
      end(tag.toString().toLowerCase(Locale.ROOT));
    }

    @Override
    public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attrs, int pos) {
      String name = tag.toString().toLowerCase(Locale.ROOT);
      Object ending = attrs.getAttribute(HTML.Attribute.ENDTAG);
      if (Boolean.TRUE.equals(ending) || "true".equalsIgnoreCase(Objects.toString(ending, "")))
        end(name);
      else start(name, attrs);
    }

    @Override
    public void handleText(char[] data, int pos) {
      html.append(escape(new String(data)));
    }

    private void start(String name, MutableAttributeSet attrs) {
      if (!TAGS.contains(name)) return;
      if ("br".equals(name)) {
        html.append("<br>");
        return;
      }
      if (open.size() >= 64) throw new IllegalArgumentException("CONTRACT_HTML_TOO_DEEP");
      html.append('<').append(name);
      if ("a".equals(name)) {
        String href = safeHref(attrs.getAttribute(HTML.Attribute.HREF));
        if (href != null) html.append(" href=\"").append(escape(href)).append('"');
      }
      html.append('>');
      open.push(name);
    }

    private void end(String name) {
      if (!open.contains(name)) return;
      while (!open.isEmpty()) {
        String current = open.pop();
        html.append("</").append(current).append('>');
        if (current.equals(name)) return;
      }
    }

    private void finish() {
      while (!open.isEmpty()) html.append("</").append(open.pop()).append('>');
    }
  }
}
