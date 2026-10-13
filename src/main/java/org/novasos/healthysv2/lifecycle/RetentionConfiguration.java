package org.novasos.healthysv2.lifecycle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class RetentionConfiguration implements ApplicationRunner {
  private final JdbcTemplate jdbc;
  private final int years;

  RetentionConfiguration(JdbcTemplate jdbc, @Value("${DATA_RETENTION_YEARS:30}") int years) {
    if (years < 1 || years > 200)
      throw new IllegalArgumentException("DATA_RETENTION_YEARS must be between 1 and 200");
    this.jdbc = jdbc;
    this.years = years;
  }

  public void run(ApplicationArguments args) {
    // Changing the policy never shortens already associated historical deadlines.
    jdbc.update("update lifecycle.retention_policy set years=? where id=true", years);
  }
}
