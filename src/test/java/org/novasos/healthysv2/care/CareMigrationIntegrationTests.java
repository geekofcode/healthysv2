package org.novasos.healthysv2.care;

import static org.assertj.core.api.Assertions.*;
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
class CareMigrationIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Test void addsCareTablesWithoutRemovingLegacyTablesAndEnforcesInitialUniqueness() {
        assertThat(jdbc.queryForObject("SELECT to_regclass('care.care_episode') IS NOT NULL AND to_regclass('care.care_visit') IS NOT NULL AND to_regclass('consultation.consultation') IS NOT NULL AND to_regclass('appointment.appointment') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname='care' AND indexname='uq_care_visit_initial'", String.class))
                .contains("UNIQUE").contains("follow_up").contains("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints WHERE table_schema='care' AND constraint_type='FOREIGN KEY'", Integer.class)).isEqualTo(4);
    }
}
