package org.novasos.healthysv2.identity;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.novasos.healthysv2.identity.api.EmployeeAccountProvisioning;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class EmployeeAccountProvisioningService implements EmployeeAccountProvisioning {
  private final JdbcTemplate jdbc;

  EmployeeAccountProvisioningService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public AccountIdentity ensureAccount(String firstName, String lastName, String email) {
    String mail = normalizeEmail(email);
    String given = requiredName(firstName), family = requiredName(lastName);
    // Shared advisory key serializes this workflow without exposing an email in audit/logs.
    jdbc.queryForObject(
        "select pg_advisory_xact_lock(hashtextextended(?, 5721))", Object.class, mail);
    Optional<AccountIdentity> existing = lookupExactEmail(mail);
    if (existing.isPresent()) {
      AccountIdentity person = existing.get();
      if (!"ACTIVE".equals(person.status()))
        throw new ConflictException("ACCOUNT_INACTIVE", "error.account.inactive");
      if (!"ACTIVE".equals(person.accountStatus())) enqueue(person.personId(), mail);
      return lookupExactEmail(mail).orElseThrow();
    }
    Integer unlinked =
        jdbc.queryForObject(
            "select count(*) from identity.person_contact c join identity.person p on"
                + " p.id=c.person_id where c.type='EMAIL' and lower(trim(c.value))=? and"
                + " p.keycloak_user_id is null and not exists(select 1 from"
                + " identity.staff_account_outbox o where o.person_id=p.id)",
            Integer.class,
            mail);
    if (unlinked != null && unlinked > 0)
      throw new ConflictException(
          "ACCOUNT_IDENTITY_RECONCILIATION_REQUIRED", "error.account.identity.reconciliation");
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into identity.person(id,person_number,first_name,last_name,status)"
            + " values(?,?,?,?,'ACTIVE')",
        id,
        "PER-" + id.toString().replace("-", ""),
        given,
        family);
    jdbc.update(
        "insert into identity.person_contact(id,person_id,type,value,is_primary,verified)"
            + " values(?,?,'EMAIL',?,true,false)",
        UUID.randomUUID(),
        id,
        mail);
    enqueue(id, mail);
    return new AccountIdentity(id, given, family, mail, "ACTIVE", "PENDING");
  }

  private void enqueue(UUID id, String mail) {
    jdbc.update(
        "insert into identity.staff_account_outbox(person_id,email) values(?,?) on"
            + " conflict(person_id) do nothing",
        id,
        mail);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AccountIdentity> lookupExactEmail(String email) {
    String mail = normalizeEmail(email);
    List<AccountIdentity> people =
        jdbc.query(
            """
            select distinct p.id,p.first_name,p.last_name,p.status,
              coalesce(o.status,case when p.keycloak_user_id is not null then 'ACTIVE' else 'PENDING' end) account_status
            from identity.person p join identity.person_contact c on c.person_id=p.id
            left join identity.staff_account_outbox o on o.person_id=p.id
            where c.type='EMAIL' and lower(trim(c.value))=? and (p.keycloak_user_id is not null or o.person_id is not null) order by p.id limit 2
            """,
            (row, index) ->
                new AccountIdentity(
                    row.getObject("id", UUID.class),
                    row.getString("first_name"),
                    row.getString("last_name"),
                    mail,
                    row.getString("status"),
                    row.getString("account_status")),
            mail);
    if (people.size() > 1)
      throw new ConflictException("AMBIGUOUS_ACCOUNT_EMAIL", "error.account.email.ambiguous");
    return people.stream().findFirst();
  }

  static String normalizeEmail(String email) {
    if (email == null) throw new IllegalArgumentException("Email is required");
    String value = email.trim().toLowerCase(Locale.ROOT);
    if (value.length() > 255 || !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new IllegalArgumentException("Invalid email");
    return value;
  }

  private static String requiredName(String name) {
    if (name == null || name.isBlank() || name.trim().length() > 120)
      throw new IllegalArgumentException("Invalid name");
    return name.trim();
  }
}
