package org.novasos.healthysv2.identity;

import java.util.List;
import java.util.UUID;
import org.novasos.healthysv2.identity.api.EmployeeAccountProvisioning.AccountIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Account directory excludes clinical profiles, employee assignments and personal demographics. */
@RestController
@RequestMapping("/api/v1/platform/accounts")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
class PlatformAccountsController {
  private final JdbcTemplate jdbc;

  PlatformAccountsController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping
  List<AccountIdentity> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 100)
      throw new IllegalArgumentException("Invalid account page");
    return jdbc.query(
        """
        select p.id,p.first_name,p.last_name,p.status,
          (select c.value from identity.person_contact c where c.person_id=p.id and c.type='EMAIL' order by c.is_primary desc,c.id limit 1) email,
          coalesce(o.status,case when p.keycloak_user_id is not null then 'ACTIVE' else 'PENDING' end) account_status
        from identity.person p left join identity.staff_account_outbox o on o.person_id=p.id
        where p.keycloak_user_id is not null or o.person_id is not null
        order by p.created_at desc,p.id limit ? offset ?
        """,
        (row, index) ->
            new AccountIdentity(
                row.getObject("id", UUID.class),
                row.getString("first_name"),
                row.getString("last_name"),
                row.getString("email"),
                row.getString("status"),
                row.getString("account_status")),
        size,
        page * size);
  }
}
