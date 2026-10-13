package org.novasos.healthysv2.licensing;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class LicensingMigrationIntegrationTests {
  @Autowired JdbcTemplate db;

  @Test
  void validatesQuotaAndPeriodSchema() {
    assertThat(
            db.queryForObject(
                "select to_regclass('licensing.subscription') is not null", Boolean.class))
        .isTrue();
    assertThatThrownBy(
            () ->
                db.update(
                    "insert into"
                        + " licensing.plan(name,amount_minor,currency,billing_interval,doctor_quota,professional_quota,cashier_quota,accountant_quota,stripe_price_id)"
                        + " values('bad',100,'cad','MONTH',-1,1,1,1,'price_bad')"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void eventsDeduplicated() {
    assertThat(
            db.update(
                "insert into licensing.stripe_event(id,type) values('evt_test','invoice.paid') on"
                    + " conflict do nothing"))
        .isEqualTo(1);
    assertThat(
            db.update(
                "insert into licensing.stripe_event(id,type) values('evt_test','invoice.paid') on"
                    + " conflict do nothing"))
        .isZero();
  }
}
