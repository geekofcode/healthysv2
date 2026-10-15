package org.novasos.healthysv2.care;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class AiSnapshot {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  AiSnapshot(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  Map<String, Object> build(AiAuthorization.Context c) {
    List<Map<String, Object>> sources = new ArrayList<>();
    add(
        sources,
        "visit",
        "Accueil et motif",
        db.queryForList(
            "select v.reason,coalesce(r.version,0) as draft_version from care.care_visit v left"
                + " join care.visit_report r on r.visit_id=v.id where v.id=?",
            c.visitId()));
    add(
        sources,
        "intake",
        "Constantes et accueil infirmier",
        db.queryForList(
            "select observations,vitals::text as vitals,recorded_at from care.visit_intake where"
                + " visit_id=?",
            c.visitId()));
    add(
        sources,
        "allergies",
        "Allergies",
        db.queryForList(
            "select id,allergen,reaction,severity,status from patient.allergy where patient_id=?"
                + " order by id limit 100",
            c.patientId()));
    add(
        sources,
        "chronic",
        "Maladies chroniques",
        db.queryForList(
            "select c.id,c.notes,c.status,c.diagnosed_at,d.code,d.label from"
                + " patient.chronic_disease c left join catalog.diagnosis_catalog d on"
                + " d.id=c.diagnosis_catalog_id where c.patient_id=? order by c.id limit 100",
            c.patientId()));
    add(
        sources,
        "history",
        "Antécédents médicaux",
        db.queryForList(
            "select id,condition,notes,diagnosed_at,resolved_at from patient.medical_history where"
                + " patient_id=? order by id limit 100",
            c.patientId()));
    add(
        sources,
        "surgery",
        "Antécédents chirurgicaux",
        db.queryForList(
            "select id,procedure_name,procedure_date,notes from patient.surgical_history where"
                + " patient_id=? order by id limit 100",
            c.patientId()));
    add(
        sources,
        "reports",
        "Derniers rapports validés",
        db.queryForList(
            """
            select r.id,r.validated_at,r.reason,r.observations,r.diagnosis,r.treatment,r.follow_up,r.exam_requests::text as exam_requests from care.visit_report_revision r
            join care.care_visit v on v.id=r.visit_id join care.care_episode e on e.id=v.episode_id
            where e.patient_id=? and r.revision=(select max(x.revision) from care.visit_report_revision x where x.visit_id=r.visit_id)
            order by r.validated_at desc,r.id limit 20
            """,
            c.patientId()));
    add(
        sources,
        "flags",
        "Alertes du dossier",
        db.queryForList(
            "select id,flag_type,label,severity from patient.patient_flag where patient_id=? and"
                + " active order by id limit 100",
            c.patientId()));
    add(
        sources,
        "family",
        "Antécédents familiaux",
        db.queryForList(
            "select id,relationship,condition,notes from patient.family_history where patient_id=?"
                + " order by id limit 100",
            c.patientId()));
    add(
        sources,
        "prescriptions",
        "Dernières prescriptions validées",
        db.queryForList(
            "select r.id,r.items::text as items,r.validated_at from"
                + " care.visit_prescription_revision r join care.care_visit v on v.id=r.visit_id"
                + " join care.care_episode e on e.id=v.episode_id where e.patient_id=? and"
                + " r.revision=(select max(x.revision) from care.visit_prescription_revision x"
                + " where x.visit_id=r.visit_id) order by r.validated_at desc,r.id limit 20",
            c.patientId()));
    add(
        sources,
        "measured_observations",
        "Observations datées avec provenance (self_reported = déclarées, non validées)",
        db.queryForList(
            """
            select o.id,o.parameter_id,o.code,o.label,o.value_text,o.unit,o.note,o.measured_at,o.recorded_at,
                o.self_reported,o.organization_id,org.name organization_name,o.visit_id,o.actor_person_id,o.supersedes_id,
                case when o.self_reported then 'PATIENT_DECLARED_NOT_VALIDATED' else 'CLINICIAN_MEASURED' end provenance
            from patienthealth.observation o left join organization.organization org on org.id=o.organization_id
            where o.patient_id=? and not exists(select 1 from patienthealth.observation newer where newer.supersedes_id=o.id)
            order by o.measured_at desc,o.recorded_at desc,o.id limit 100
            """,
            c.patientId()));
    add(
        sources,
        "patient_declarations",
        "Déclarations patient non validées — ne pas présenter comme des faits cliniques confirmés",
        db.queryForList(
            """
            select d.id,d.kind,d.label,d.value_text,d.detail,d.occurred_on,d.recorded_at,d.actor_person_id,d.supersedes_id,
                'PATIENT_DECLARED_NOT_VALIDATED' as provenance
            from patienthealth.declaration d where d.patient_id=?
                and not exists(select 1 from patienthealth.declaration newer where newer.supersedes_id=d.id)
            order by d.recorded_at desc,d.id limit 50
            """,
            c.patientId()));
    List<String> digests = new ArrayList<>();
    for (String table :
        List.of(
            "allergy",
            "chronic_disease",
            "medical_history",
            "surgical_history",
            "family_history",
            "patient_flag"))
      digests.add(
          db.queryForObject(
              "select coalesce(md5(string_agg(md5(to_jsonb(t)::text),'' order by id)),'') from"
                  + " patient."
                  + table
                  + " t where patient_id=?",
              String.class,
              c.patientId()));
    for (String table : List.of("observation", "declaration"))
      digests.add(
          db.queryForObject(
              "select coalesce(md5(string_agg(md5(to_jsonb(t)::text),'' order by t.id)),'') from"
                  + " patienthealth."
                  + table
                  + " t where patient_id=?",
              String.class,
              c.patientId()));
    for (String table : List.of("visit_report_revision", "visit_prescription_revision"))
      digests.add(
          db.queryForObject(
              "select coalesce(md5(string_agg(md5(to_jsonb(r)::text),'' order by r.id)),'') from"
                  + " care."
                  + table
                  + " r join care.care_visit v on v.id=r.visit_id join care.care_episode e on"
                  + " e.id=v.episode_id where e.patient_id=?",
              String.class,
              c.patientId()));
    digests.add(
        db.queryForObject(
            "select coalesce(md5(string_agg(md5(to_jsonb(t)::text),'' order by visit_id)),'') from"
                + " care.visit_intake t where visit_id=?",
            String.class,
            c.visitId()));
    List<String> truncated = new ArrayList<>();
    for (var source : sources) {
      var data = (List<?>) source.get("data");
      if (data.size() >= 20 || encode(data).contains("[TRUNCATED]"))
        truncated.add(source.get("id").toString());
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("sources", sources);
    result.put("sourceDigest", hash(digests));
    result.put("truncatedSources", truncated);
    while (encode(result).getBytes(StandardCharsets.UTF_8).length > 150000) {
      var largest =
          sources.stream()
              .filter(x -> !((List<?>) x.get("data")).isEmpty())
              .max(Comparator.comparingInt(x -> encode(x.get("data")).length()))
              .orElseThrow();
      var data = (List<?>) largest.get("data");
      data.removeLast();
      if (!truncated.contains(largest.get("id"))) truncated.add(largest.get("id").toString());
    }
    return result;
  }

  private void add(
      List<Map<String, Object>> sources, String id, String label, List<Map<String, Object>> data) {
    List<Map<String, Object>> clean = new ArrayList<>();
    for (var row : data) {
      Map<String, Object> values = new TreeMap<>();
      row.forEach((k, v) -> values.put(k, v == null ? null : bounded(v.toString())));
      clean.add(values);
    }
    sources.add(Map.of("id", id, "label", label, "data", clean));
  }

  private String bounded(String s) {
    return s.length() > 6000 ? s.substring(0, 5989) + "[TRUNCATED]" : s;
  }

  String encode(Object value) {
    try {
      return json.writer()
          .with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("AI_JSON_INVALID", e);
    }
  }

  Map<String, Object> decode(String value) {
    try {
      return json.readValue(
          value, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("AI_JSON_INVALID", e);
    }
  }

  String hash(Object value) {
    return hashText(encode(value));
  }

  static String hashText(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
