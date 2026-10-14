package org.novasos.healthysv2.profilecontract;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SMTPS delivery is disabled by default; the sole recipient comes from the verified signed
 * snapshot.
 */
@Component
class ContractMailer {
  private final boolean enabled, ssl;
  private final String host, username, password, from;
  private final int port;

  ContractMailer(
      @Value("${EMAIL_ENABLED:false}") boolean enabled,
      @Value("${EMAIL_SMTP_HOST:}") String host,
      @Value("${EMAIL_SMTP_PORT:465}") int port,
      @Value("${EMAIL_SMTP_SSL:true}") boolean ssl,
      @Value("${EMAIL_SMTP_USERNAME:}") String username,
      @Value("${EMAIL_SMTP_PASSWORD:}") String password,
      @Value("${EMAIL_FROM:}") String from) {
    this.enabled = enabled;
    this.host = host;
    this.port = port;
    this.ssl = ssl;
    this.username = username;
    this.password = password;
    this.from = from;
  }

  boolean enabled() {
    return enabled;
  }

  void send(UUID agreement, String recipient, byte[] pdf) throws IOException {
    if (!enabled
        || !ProfileContractService.emailAddress(from)
        || !ProfileContractService.emailAddress(recipient)
        || host.isBlank()) throw new IOException("EMAIL_CONFIGURATION_INVALID");
    if (!ssl && !Set.of("localhost", "127.0.0.1", "::1").contains(host))
      throw new IOException("SMTP_TLS_REQUIRED");
    try (Socket socket = ssl ? SSLSocketFactory.getDefault().createSocket() : new Socket()) {
      if (socket instanceof javax.net.ssl.SSLSocket secure) {
        var parameters = secure.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        secure.setSSLParameters(parameters);
      }
      socket.connect(new InetSocketAddress(host, port), 10000);
      socket.setSoTimeout(10000);
      if (socket instanceof javax.net.ssl.SSLSocket secure) secure.startHandshake();
      var reader =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
      var writer =
          new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
      reply(reader, 220);
      command(writer, reader, "EHLO healthys", 250);
      if (!username.isBlank()) {
        command(writer, reader, "AUTH LOGIN", 334);
        command(
            writer,
            reader,
            Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)),
            334);
        command(
            writer,
            reader,
            Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)),
            235);
      }
      command(writer, reader, "MAIL FROM:<" + from + ">", 250);
      command(writer, reader, "RCPT TO:<" + recipient + ">", 250);
      command(writer, reader, "DATA", 354);
      String boundary = "healthys-contract-" + agreement;
      String message =
          "From: "
              + from
              + "\r\nTo: "
              + recipient
              + "\r\nSubject: HEALTHYS - Votre contrat signe\r\nMessage-ID: <contract-"
              + agreement
              + "@healthys>\r\nMIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=\""
              + boundary
              + "\"\r\n\r\n--"
              + boundary
              + "\r\n"
              + "Content-Type: text/plain; charset=UTF-8\r\n"
              + "Content-Transfer-Encoding: base64\r\n\r\n"
              + Base64.getEncoder()
                  .encodeToString(
                      "Vous trouverez votre contrat signe en piece jointe. Une copie reste disponible dans votre profil HEALTH’YS."
                          .getBytes(StandardCharsets.UTF_8))
              + "\r\n--"
              + boundary
              + "\r\n"
              + "Content-Type: application/pdf\r\n"
              + "Content-Disposition: attachment; filename=\"contrat-healthys.pdf\"\r\n"
              + "Content-Transfer-Encoding: base64\r\n\r\n"
              + Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                  .encodeToString(pdf)
              + "\r\n--"
              + boundary
              + "--\r\n";
      writer.write(message);
      writer.write(".\r\n");
      writer.flush();
      reply(reader, 250);
      command(writer, reader, "QUIT", 221);
    }
  }

  private void command(BufferedWriter writer, BufferedReader reader, String command, int code)
      throws IOException {
    writer.write(command + "\r\n");
    writer.flush();
    reply(reader, code);
  }

  private void reply(BufferedReader reader, int expected) throws IOException {
    for (int i = 0; i < 100; i++) {
      String line = reader.readLine();
      if (line == null || line.length() < 3) throw new IOException("SMTP_FAILED");
      int code;
      try {
        code = Integer.parseInt(line.substring(0, 3));
      } catch (NumberFormatException e) {
        throw new IOException("SMTP_FAILED");
      }
      if (code != expected) throw new IOException("SMTP_REJECTED");
      if (line.length() < 4 || line.charAt(3) != '-') return;
    }
    throw new IOException("SMTP_REPLY_TOO_LONG");
  }
}
