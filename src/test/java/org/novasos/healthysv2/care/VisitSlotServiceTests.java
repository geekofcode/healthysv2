package org.novasos.healthysv2.care;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;

class VisitSlotServiceTests {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final VisitSlotService service=new VisitSlotService(jdbc,new VisitSchedulingPolicy(30));
    final UUID physician=UUID.randomUUID(),org=UUID.randomUUID(),speciality=UUID.randomUUID(),visit=UUID.randomUUID();
    final Instant start=Instant.parse("2026-10-13T01:00:00Z");
    @BeforeEach void setup() {
        when(jdbc.queryForObject(anyString(),eq(String.class),any(Object[].class))).thenReturn("America/Toronto");
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(contains("join identity.person"),eq(Integer.class),any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(contains("pa.status='AVAILABLE'"),eq(Integer.class),any(Object[].class))).thenReturn(1);
    }
    @Test void assignmentDatesUseTheEstablishmentsLocalDay() {
        assertThat(service.requireSlot(physician,org,speciality,start,visit)).isEqualTo(30);
        verify(jdbc).queryForObject(contains("join identity.person"),eq(Integer.class),eq(physician),eq(org),eq(org),
                eq(LocalDate.of(2026,10,12)),eq(LocalDate.of(2026,10,12)),eq(speciality));
    }
    @Test void aBlockedIntervalOverridesPositiveAvailability() {
        when(jdbc.queryForObject(contains("pa.status='UNAVAILABLE'"),eq(Integer.class),any(Object[].class))).thenReturn(1);
        assertThatThrownBy(()->service.requireSlot(physician,org,speciality,start,visit)).isInstanceOf(BusinessRuleException.class);
        verify(jdbc,never()).queryForObject(contains("from appointment.appointment"),eq(Integer.class),any(Object[].class));
    }
    @Test void overlapsAreRejectedAcrossEstablishments() {
        when(jdbc.queryForObject(contains("from care.care_visit"),eq(Integer.class),any(Object[].class))).thenReturn(1);
        assertThatThrownBy(()->service.requireSlot(physician,org,speciality,start,visit)).isInstanceOf(ConflictException.class);
        verify(jdbc).queryForObject(contains("from care.care_visit"),eq(Integer.class),eq(physician),eq(visit),
                eq(java.sql.Timestamp.from(start.plusSeconds(1800))),eq(java.sql.Timestamp.from(start)));
    }
    @Test void absentScheduleOrAvailabilityCannotBeProgrammed() {
        when(jdbc.queryForObject(contains("pa.status='AVAILABLE'"),eq(Integer.class),any(Object[].class))).thenReturn(0);
        assertThatThrownBy(()->service.requireSlot(physician,org,speciality,start,visit)).isInstanceOf(BusinessRuleException.class);
    }
}
