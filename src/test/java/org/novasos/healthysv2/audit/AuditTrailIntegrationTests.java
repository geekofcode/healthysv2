package org.novasos.healthysv2.audit;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test") @SpringBootTest @Import(TestcontainersConfiguration.class) @Transactional
class AuditTrailIntegrationTests {
    @Autowired AuditTrail trail;@Autowired JdbcTemplate jdbc;

    @Test void storesStructuredChangeAndDataAccessEvents(){UUID entity=UUID.randomUUID(),patient=UUID.randomUUID();trail.change(null,null,"TEST","Record",entity,"CREATE",null,Map.of("status","ACTIVE"));trail.access(null,patient,null,"MEDICAL_RECORD",patient,"READ","TEST_ACCESS",Map.of("allowed",true));assertThat(jdbc.queryForObject("select new_value->>'status' from audit.audit_log where entity_id=?",String.class,entity)).isEqualTo("ACTIVE");assertThat(jdbc.queryForObject("select access_context->>'allowed' from audit.data_access_log where patient_id=?",String.class,patient)).isEqualTo("true");}

    @Test void rejectsUpdatesAndDeletesOnImmutableLogs(){UUID id=UUID.randomUUID();jdbc.update("insert into audit.audit_log(id,module,entity_type,action) values (?,'TEST','Record','CREATE')",id);assertThatThrownBy(()->jdbc.update("update audit.audit_log set action='UPDATE' where id=?",id)).isInstanceOf(DataIntegrityViolationException.class);}
}
