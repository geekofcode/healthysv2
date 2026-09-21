package org.novasos.healthysv2.patient;
import static org.assertj.core.api.Assertions.*; import java.time.Instant; import java.util.UUID; import org.junit.jupiter.api.Test;
class CareRelationshipTests {
 @Test void createsAndEndsRelationship(){Instant start=Instant.parse("2026-09-21T10:00:00Z");var relationship=CareRelationship.create(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"attending",start,null);assertThat(relationship.isActiveAt(start.plusSeconds(1))).isTrue();relationship.end(start.plusSeconds(60));assertThat(relationship.getStatus()).isEqualTo("ENDED");assertThat(relationship.isActiveAt(start.plusSeconds(61))).isFalse();}
 @Test void rejectsEndBeforeStart(){Instant start=Instant.now();assertThatThrownBy(()->CareRelationship.create(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"ATTENDING",start,start.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);}
}
