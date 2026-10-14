package org.novasos.healthysv2.profilecontract;

import java.net.URI;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.novasos.healthysv2.profilecontract.api.*;
import org.novasos.healthysv2.profilecontract.api.ContractDtos.*;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfileContractService implements ContractAccess {
  private final JdbcTemplate db;
  private final AccessProfileResolver profiles;
  private final ContractObjectStorage storage;
  private final String clientId;

  ProfileContractService(
      JdbcTemplate db,
      AccessProfileResolver profiles,
      ContractObjectStorage storage,
      @Value("${healthys.security.api-client-id:healthys-backend}") String clientId) {
    this.db = db;
    this.profiles = profiles;
    this.storage = storage;
    this.clientId = clientId;
  }

  private JwtAuthenticationToken auth() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jwt))
      throw new AccessDeniedException("ACCOUNT_REQUIRED");
    return jwt;
  }

  private UUID subject() {
    try {
      return UUID.fromString(auth().getToken().getSubject());
    } catch (IllegalArgumentException e) {
      throw new AccessDeniedException("ACCOUNT_REQUIRED");
    }
  }

  private AccessProfileResolver.AccessProfile owned(UUID profileId) {
    return profiles.resolve(subject(), Set.of(), profileId, null);
  }

  static String category(AccessProfileResolver.AccessProfile profile) {
    return switch (profile.type()) {
      case "PLATFORM_ADMIN" -> "ADMIN";
      case "HOSPITAL_ADMIN" -> "HOSPITAL";
      case "PATIENT" -> "PATIENT";
      default -> "STAFF";
    };
  }

  @Transactional(readOnly = true)
  public Template currentTemplate(UUID profileId) {
    var profile = owned(profileId);
    var template = published(category(profile));
    String signer =
        db.queryForObject(
            "select concat_ws(' ',first_name,last_name) from identity.person where id=? and"
                + " status='ACTIVE'",
            String.class,
            profile.personId());
    var values = renderValues(profile, signer, null);
    return new Template(
        template.id(),
        template.category(),
        template.version(),
        template.termsUrl(),
        template.termsVersion(),
        template.bodyText(),
        template.publishedAt(),
        renderBody(template, values),
        template.bodyFormat(),
        template.headerHtml(),
        template.footerHtml(),
        ContractHtml.render(template.headerHtml(), values),
        ContractHtml.render(template.footerHtml(), values));
  }

  private Map<String, String> renderValues(
      AccessProfileResolver.AccessProfile profile, String signer, Instant signedAt) {
    String patient = "";
    if ("PATIENT".equals(profile.type()))
      patient =
          db.queryForObject(
              "select concat_ws(' ',p.first_name,p.last_name) from patient.patient pt join"
                  + " identity.person p on p.id=pt.person_id where pt.id=?",
              String.class,
              profile.sourceId());
    return Map.of(
        "signerName",
        Objects.toString(signer, ""),
        "profileLabel",
        Objects.toString(profile.label(), profile.type()),
        "organizationName",
        Objects.toString(profile.organizationName(), ""),
        "patientName",
        Objects.toString(patient, ""),
        "signedAt",
        signedAt == null ? "[date de signature]" : signedAt.toString());
  }

  static String renderBody(Template template, Map<String, String> values) {
    if ("HTML".equals(template.bodyFormat()))
      return ContractHtml.render(template.bodyText(), values);
    return template
        .bodyText()
        .replace("{{signerName}}", values.get("signerName"))
        .replace("{{profileLabel}}", values.get("profileLabel"))
        .replace("{{organizationName}}", values.get("organizationName"))
        .replace("{{patientName}}", values.get("patientName"))
        .replace("{{signedAt}}", values.get("signedAt"))
        .replace("{{signature}}", "[Signature manuscrite en fin de document]");
  }

  private Template published(String category) {
    var rows =
        db.query(
            "select * from profilecontract.template where category=? and published_at is not null"
                + " order by published_at desc,id desc limit 1",
            (rs, n) ->
                new Template(
                    (UUID) rs.getObject("id"),
                    rs.getString("category"),
                    rs.getInt("version"),
                    rs.getString("terms_url"),
                    rs.getString("terms_version"),
                    rs.getString("body_text"),
                    rs.getTimestamp("published_at") == null
                        ? null
                        : rs.getTimestamp("published_at").toInstant(),
                    null,
                    rs.getString("body_format"),
                    rs.getString("header_html"),
                    rs.getString("footer_html"),
                    "",
                    ""),
            category);
    if (rows.isEmpty())
      throw new BusinessRuleException(
          "CONTRACT_TEMPLATE_NOT_CONFIGURED",
          "An administrator must publish the contract and terms for this profile");
    return rows.getFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public void requireSigned(UUID personId, UUID profileId) {
    if (personId == null || profileId == null)
      throw new AccessDeniedException("PROFILE_CONTRACT_REQUIRED");
    var profile = profiles.forPerson(personId, profileId);
    if (profile == null || !profile.active())
      throw new AccessDeniedException("PROFILE_CONTRACT_REQUIRED");
    Integer signed =
        db.queryForObject(
            """
            select count(*) from profilecontract.agreement a join identity.person p on p.id=a.actor_person_id and p.status='ACTIVE'
            where a.actor_person_id=? and a.profile_id=? and a.profile_type=? and a.source_type=?
              and a.source_id is not distinct from ? and a.organization_id is not distinct from ? and a.category=? and a.accepted_terms
            """,
            Integer.class,
            personId,
            profileId,
            profile.type(),
            profile.sourceType(),
            profile.sourceId(),
            profile.organizationId(),
            category(profile));
    if (signed == null || signed == 0) throw new AccessDeniedException("PROFILE_CONTRACT_REQUIRED");
  }

  @Transactional(readOnly = true)
  public State state(UUID profileId) {
    var profile = owned(profileId);
    return stateFor(profile);
  }

  private State stateFor(AccessProfileResolver.AccessProfile profile) {
    var templates =
        db.queryForList(
            "select id,version from profilecontract.template where category=? and published_at is"
                + " not null order by published_at desc,id desc limit 1",
            category(profile));
    if (templates.isEmpty())
      return new State(
          profile.id(), false, null, "UNSIGNED", null, null, null, false, "NOT_QUEUED");
    UUID templateId = (UUID) templates.getFirst().get("id");
    var rows =
        db.queryForList(
            "select a.*,j.phase,j.email_sent_at,j.error_code from profilecontract.agreement a left"
                + " join profilecontract.delivery_job j on j.agreement_id=a.id where"
                + " a.actor_person_id=? and a.profile_id=? and a.profile_type=? and a.source_type=?"
                + " and a.source_id is not distinct from ? and a.organization_id is not distinct"
                + " from ? and a.category=? order by a.signed_at desc,a.id desc limit 1",
            profile.personId(),
            profile.id(),
            profile.type(),
            profile.sourceType(),
            profile.sourceId(),
            profile.organizationId(),
            category(profile));
    if (rows.isEmpty())
      return new State(
          profile.id(),
          false,
          null,
          "UNSIGNED",
          templateId,
          ((Number) templates.getFirst().get("version")).intValue(),
          null,
          false,
          "NOT_QUEUED");
    var r = rows.getFirst();
    String email =
        r.get("email_sent_at") != null ? "SENT" : r.get("error_code") != null ? "FAILED" : "QUEUED";
    return new State(
        profile.id(),
        true,
        (UUID) r.get("id"),
        (String) r.get("status"),
        (UUID) r.get("template_id"),
        ((Number) r.get("template_version")).intValue(),
        ((Timestamp) r.get("signed_at")).toInstant(),
        r.get("pdf_key") != null,
        email);
  }

  public State sign(UUID profileId, SignRequest request) {
    var profile = owned(profileId);
    if (!profile.active()) throw new AccessDeniedException("ACCESS_PROFILE_INACTIVE");
    if (!request.acceptTerms()) throw new IllegalArgumentException("TERMS_ACCEPTANCE_REQUIRED");
    boolean guardian = "FAMILY_PATIENT".equals(profile.sourceType());
    if (guardian && !request.guardianAcknowledgement())
      throw new IllegalArgumentException("GUARDIAN_ACKNOWLEDGEMENT_REQUIRED");
    db.queryForObject(
        "select id from identity.person where id=? and status='ACTIVE' for update",
        UUID.class,
        profile.personId());
    profile = owned(profileId);
    db.execute("select pg_advisory_xact_lock(19490001)");
    var template = published(category(profile));
    if (!template.id().equals(request.templateId()))
      throw new ConflictException(
          "CONTRACT_TEMPLATE_CHANGED", "Reload the current contract before signing");
    db.queryForObject(
        "select id from profilecontract.template where id=? for share", UUID.class, template.id());
    Integer sameAgreement =
        db.queryForObject(
            "select count(*) from profilecontract.agreement where actor_person_id=? and"
                + " profile_id=? and template_id=? and profile_type=?",
            Integer.class,
            profile.personId(),
            profile.id(),
            template.id(),
            profile.type());
    if (sameAgreement != null && sameAgreement > 0) return stateFor(profile);
    String email = auth().getToken().getClaimAsString("email");
    if (!Boolean.TRUE.equals(auth().getToken().getClaimAsBoolean("email_verified"))
        || !emailAddress(email)) throw new AccessDeniedException("VERIFIED_EMAIL_REQUIRED");
    String signer =
        db.queryForObject(
            "select concat_ws(' ',first_name,last_name) from identity.person where id=? and"
                + " status='ACTIVE'",
            String.class,
            profile.personId());
    if (signer == null || signer.isBlank())
      throw new IllegalArgumentException("SIGNER_NAME_REQUIRED");
    String patientName = "";
    if ("PATIENT".equals(profile.type()))
      patientName =
          db.queryForObject(
              "select concat_ws(' ',p.first_name,p.last_name) from patient.patient pt join"
                  + " identity.person p on p.id=pt.person_id where pt.id=?",
              String.class,
              profile.sourceId());
    String organization = profile.organizationName() == null ? "" : profile.organizationName();
    String label = profile.label() == null ? profile.type() : profile.label();
    byte[] signature = SignatureImage.normalize(request.signature());
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    var values =
        Map.of(
            "signerName",
            signer,
            "profileLabel",
            label,
            "organizationName",
            organization,
            "patientName",
            patientName,
            "signedAt",
            now.toString());
    String body = renderBody(template, values);
    String header = ContractHtml.render(template.headerHtml(), values),
        footer = ContractHtml.render(template.footerHtml(), values);
    ContractPdf.validateZones(header, footer);
    String snapshotHash =
        hashSnapshot(
            List.of(
                id.toString(),
                profile.personId().toString(),
                profile.id().toString(),
                profile.type(),
                profile.sourceType(),
                Objects.toString(profile.sourceId(), ""),
                Objects.toString(profile.organizationId(), ""),
                template.id().toString(),
                Integer.toString(template.version()),
                template.category(),
                template.termsUrl(),
                template.termsVersion(),
                body,
                signer,
                email,
                label,
                organization,
                patientName,
                now.toString(),
                "true",
                Boolean.toString(guardian),
                hash(signature),
                template.bodyFormat(),
                header,
                footer));
    db.update(
        """
        insert into profilecontract.agreement(id,actor_person_id,profile_id,template_id,template_version,category,profile_type,source_type,source_id,organization_id,profile_label,organization_name,patient_name,signer_name,signer_email,terms_url,terms_version,body_snapshot,accepted_terms,guardian_acknowledgement,signature_png,signature_sha256,snapshot_sha256,signed_at,body_format,header_snapshot,footer_snapshot)
        values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,true,?,?,?,?,?,?,?,?)
        """,
        id,
        profile.personId(),
        profile.id(),
        template.id(),
        template.version(),
        template.category(),
        profile.type(),
        profile.sourceType(),
        profile.sourceId(),
        profile.organizationId(),
        label,
        organization,
        patientName,
        signer,
        email,
        template.termsUrl(),
        template.termsVersion(),
        body,
        guardian,
        signature,
        hash(signature),
        snapshotHash,
        Timestamp.from(now),
        template.bodyFormat(),
        header,
        footer);
    db.update("insert into profilecontract.delivery_job(agreement_id) values(?)", id);
    db.update(
        "insert into audit.audit_log(actor_person_id,module,entity_type,entity_id,action,new_value)"
            + " values (?,'PROFILE_CONTRACT','Agreement',?,'SIGNED',?::jsonb)",
        profile.personId(),
        id,
        "{\"profileId\":\""
            + profile.id()
            + "\",\"templateId\":\""
            + template.id()
            + "\",\"snapshotSha256\":\""
            + snapshotHash
            + "\"}");
    return stateFor(profile);
  }

  @Transactional(readOnly = true)
  public byte[] pdf(UUID profileId) {
    var profile = owned(profileId);
    var rows =
        db.queryForList(
            "select pdf_key from profilecontract.agreement where actor_person_id=? and profile_id=?"
                + " and pdf_key is not null order by signed_at desc limit 1",
            profile.personId(),
            profile.id());
    if (rows.isEmpty()) throw new ResourceNotFoundException("ContractPdf", profileId);
    return storage.get((String) rows.getFirst().get("pdf_key"));
  }

  private UUID administrator() {
    var auth = auth();
    if (auth.getAuthorities().stream()
        .noneMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN")))
      throw new AccessDeniedException("SELECTED_PLATFORM_ADMIN_REQUIRED");
    var rows =
        db.queryForList(
            "select id from identity.person where keycloak_user_id=? and status='ACTIVE'",
            UUID.class,
            subject());
    if (rows.size() != 1) throw new AccessDeniedException("ACTIVE_ACCOUNT_REQUIRED");
    return rows.getFirst();
  }

  @Transactional(readOnly = true)
  public List<Template> templates() {
    administrator();
    return db.query(
        "select * from profilecontract.template order by category,version desc",
        (rs, n) ->
            new Template(
                (UUID) rs.getObject("id"),
                rs.getString("category"),
                rs.getInt("version"),
                rs.getString("terms_url"),
                rs.getString("terms_version"),
                rs.getString("body_text"),
                rs.getTimestamp("published_at") == null
                    ? null
                    : rs.getTimestamp("published_at").toInstant(),
                null,
                rs.getString("body_format"),
                rs.getString("header_html"),
                rs.getString("footer_html"),
                "",
                ""));
  }

  static void validateTemplate(TemplateRequest request) {
    URI url;
    try {
      url = URI.create(request.termsUrl());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("INVALID_TERMS_URL");
    }
    if (!"https".equalsIgnoreCase(url.getScheme())
        || url.getHost() == null
        || url.getUserInfo() != null)
      throw new IllegalArgumentException("HTTPS_TERMS_URL_REQUIRED");
    if (!Set.of("TEXT", "HTML").contains(request.bodyFormat()))
      throw new IllegalArgumentException("INVALID_CONTRACT_FORMAT");
    normalizedBody(request);
    ContractPdf.validateZones(
        normalizedZone(request.headerHtml()), normalizedZone(request.footerHtml()));
    if (request.bodyText().isBlank() || request.termsVersion().isBlank())
      throw new IllegalArgumentException("CONTRACT_TEXT_REQUIRED");
  }

  private static String normalizedBody(TemplateRequest request) {
    if (!"HTML".equals(request.bodyFormat())) return request.bodyText();
    String safe = ContractHtml.sanitize(request.bodyText());
    if (ContractHtml.plainText(safe).isBlank())
      throw new IllegalArgumentException("CONTRACT_TEXT_REQUIRED");
    return safe;
  }

  private static String normalizedZone(String input) {
    if (input == null || input.isBlank()) return "";
    if (input.length() > 5000) throw new IllegalArgumentException("CONTRACT_ZONE_TOO_LARGE");
    String safe = ContractHtml.sanitize(input);
    if (safe.length() > 5000) throw new IllegalArgumentException("CONTRACT_ZONE_TOO_LARGE");
    return safe;
  }

  public Template createTemplate(TemplateRequest request) {
    UUID actor = administrator();
    validateTemplate(request);
    db.execute("select pg_advisory_xact_lock(19490001)");
    Integer version =
        db.queryForObject(
            "select coalesce(max(version),0)+1 from profilecontract.template where category=?",
            Integer.class,
            request.category());
    UUID id = UUID.randomUUID();
    db.update(
        "insert into"
            + " profilecontract.template(id,category,version,terms_url,terms_version,body_text,created_by,body_format,header_html,footer_html)"
            + " values(?,?,?,?,?,?,?,?,?,?)",
        id,
        request.category(),
        version,
        request.termsUrl(),
        request.termsVersion(),
        normalizedBody(request),
        actor,
        request.bodyFormat(),
        normalizedZone(request.headerHtml()),
        normalizedZone(request.footerHtml()));
    return new Template(
        id,
        request.category(),
        version,
        request.termsUrl(),
        request.termsVersion(),
        normalizedBody(request),
        null,
        null,
        request.bodyFormat(),
        normalizedZone(request.headerHtml()),
        normalizedZone(request.footerHtml()),
        "",
        "");
  }

  public Template updateTemplate(UUID id, TemplateRequest request) {
    administrator();
    validateTemplate(request);
    int changed =
        db.update(
            "update profilecontract.template set"
                + " terms_url=?,terms_version=?,body_text=?,body_format=?,header_html=?,footer_html=?"
                + " where id=? and category=? and published_at is null",
            request.termsUrl(),
            request.termsVersion(),
            normalizedBody(request),
            request.bodyFormat(),
            normalizedZone(request.headerHtml()),
            normalizedZone(request.footerHtml()),
            id,
            request.category());
    if (changed != 1)
      throw new ConflictException(
          "CONTRACT_TEMPLATE_IMMUTABLE", "Published templates require a new version");
    return templates().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
  }

  public Template publishTemplate(UUID id) {
    administrator();
    db.execute("select pg_advisory_xact_lock(19490001)");
    var template =
        templates().stream()
            .filter(t -> t.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("ContractTemplate", id));
    validateTemplate(
        new TemplateRequest(
            template.category(),
            template.termsUrl(),
            template.termsVersion(),
            template.bodyText(),
            template.bodyFormat(),
            template.headerHtml(),
            template.footerHtml()));
    if (template.publishedAt() != null) return template;
    db.update(
        "update profilecontract.template set published_at=clock_timestamp() where id=? and"
            + " published_at is null",
        id);
    return templates().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
  }

  static boolean emailAddress(String value) {
    return value != null
        && value.length() <= 254
        && value.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");
  }

  private static String hashSnapshot(List<String> fields) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (String field : fields) {
        byte[] bytes = field.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA256_UNAVAILABLE");
    }
  }

  static String hash(String text) {
    return hash(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA256_UNAVAILABLE");
    }
  }
}
