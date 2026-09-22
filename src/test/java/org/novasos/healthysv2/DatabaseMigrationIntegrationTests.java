package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DatabaseMigrationIntegrationTests {

    private static final List<String> EXPECTED_SCHEMAS = List.of(
            "shared", "identity", "catalog", "organization", "professional",
            "patient", "appointment", "registration", "consultation", "document",
            "laboratory", "pharmacy", "maternal_child", "communication",
            "teleconsultation", "notification", "billing", "audit");

    private static final List<String> CRITICAL_TABLES = List.of(
            "identity.person",
            "organization.organization",
            "organization.department",
            "organization.service",
            "professional.professional",
            "professional.professional_assignment",
            "patient.patient",
            "patient.patient_registration",
            "patient.consent",
            "appointment.appointment",
            "consultation.consultation",
            "laboratory.lab_order",
            "pharmacy.prescription",
            "maternal_child.pregnancy",
            "maternal_child.child_health_record",
            "maternal_child.vaccination",
            "communication.message",
            "billing.invoice",
            "audit.audit_log");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    @Test
    void flywayAppliesAllMigrations() {
        List<String> versions = jdbc.queryForList(
                """
                SELECT version
                FROM flyway_schema_history
                WHERE success
                ORDER BY installed_rank
                """,
                String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
    }

    @Test
    void createsAllExpectedSchemas() {
        assertThat(EXPECTED_SCHEMAS)
                .allSatisfy(schema -> assertThat(schemaExists(schema))
                        .as("schema %s", schema)
                        .isTrue());
    }

    @Test
    void createsCriticalTables() {
        assertThat(CRITICAL_TABLES)
                .allSatisfy(table -> assertThat(tableExists(table))
                        .as("table %s", table)
                        .isTrue());
    }

    @Test
    void patientMustReferenceAnExistingPerson() {
        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO patient.patient (id, person_id, patient_number)
                VALUES (?, ?, ?)
                """,
                UUID.randomUUID(), UUID.randomUUID(), unique("PAT")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void professionalMustReferenceAnExistingPerson() {
        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO professional.professional
                    (id, person_id, professional_number, professional_type)
                VALUES (?, ?, ?, 'DOCTOR')
                """,
                UUID.randomUUID(), UUID.randomUUID(), unique("PRO")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void patientNumberIsUnique() {
        UUID firstPerson = insertPerson();
        UUID secondPerson = insertPerson();
        String patientNumber = unique("PAT");

        insertPatient(firstPerson, patientNumber);

        assertThatThrownBy(() -> insertPatient(secondPerson, patientNumber))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void professionalNumberIsUnique() {
        UUID firstPerson = insertPerson();
        UUID secondPerson = insertPerson();
        String professionalNumber = unique("PRO");

        insertProfessional(firstPerson, professionalNumber);

        assertThatThrownBy(() -> insertProfessional(secondPerson, professionalNumber))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void patientCanBelongToSeveralOrganizations() {
        UUID patient = insertPatient(insertPerson(), unique("PAT"));
        UUID firstOrganization = insertOrganization();
        UUID secondOrganization = insertOrganization();

        insertPatientRegistration(patient, firstOrganization, unique("REG"));
        insertPatientRegistration(patient, secondOrganization, unique("REG"));

        Integer registrations = jdbc.queryForObject(
                "SELECT count(*) FROM patient.patient_registration WHERE patient_id = ?",
                Integer.class,
                patient);

        assertThat(registrations).isEqualTo(2);
    }

    @Test
    void professionalCanBelongToSeveralOrganizations() {
        UUID professional = insertProfessional(insertPerson(), unique("PRO"));
        UUID firstOrganization = insertOrganization();
        UUID secondOrganization = insertOrganization();

        insertAssignment(professional, firstOrganization, null, null);
        insertAssignment(professional, secondOrganization, null, null);

        Integer assignments = jdbc.queryForObject(
                "SELECT count(*) FROM professional.professional_assignment WHERE professional_id = ?",
                Integer.class,
                professional);

        assertThat(assignments).isEqualTo(2);
    }

    @Test
    void acceptsConsistentOrganizationDepartmentAndService() {
        UUID organization = insertOrganization();
        UUID department = insertDepartment(organization);
        UUID service = insertService(department);
        UUID professional = insertProfessional(insertPerson(), unique("PRO"));

        int inserted = insertAssignment(professional, organization, department, service);

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void rejectsDepartmentFromAnotherOrganization() {
        UUID expectedOrganization = insertOrganization();
        UUID otherOrganization = insertOrganization();
        UUID department = insertDepartment(otherOrganization);
        UUID professional = insertProfessional(insertPerson(), unique("PRO"));

        assertThatThrownBy(() ->
                insertAssignment(professional, expectedOrganization, department, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsServiceFromAnotherDepartment() {
        UUID organization = insertOrganization();
        UUID expectedDepartment = insertDepartment(organization);
        UUID otherDepartment = insertDepartment(organization);
        UUID service = insertService(otherDepartment);
        UUID professional = insertProfessional(insertPerson(), unique("PRO"));

        assertThatThrownBy(() ->
                insertAssignment(professional, organization, expectedDepartment, service))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void consentAcceptsExactlyOnePersonGrantee() {
        UUID patient = insertPatient(insertPerson(), unique("PAT"));
        UUID grantee = insertPerson();

        int inserted = jdbc.update(
                """
                INSERT INTO patient.consent
                    (id, patient_id, grantee_person_id, scope)
                VALUES (?, ?, ?, 'MEDICAL_RECORD')
                """,
                UUID.randomUUID(), patient, grantee);

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void consentAcceptsExactlyOneOrganizationGrantee() {
        UUID patient = insertPatient(insertPerson(), unique("PAT"));
        UUID organization = insertOrganization();

        int inserted = jdbc.update(
                """
                INSERT INTO patient.consent
                    (id, patient_id, grantee_organization_id, scope)
                VALUES (?, ?, ?, 'MEDICAL_RECORD')
                """,
                UUID.randomUUID(), patient, organization);

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void consentRejectsMissingGrantee() {
        UUID patient = insertPatient(insertPerson(), unique("PAT"));

        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO patient.consent (id, patient_id, scope)
                VALUES (?, ?, 'MEDICAL_RECORD')
                """,
                UUID.randomUUID(), patient))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void consentRejectsPersonAndOrganizationTogether() {
        UUID patient = insertPatient(insertPerson(), unique("PAT"));
        UUID grantee = insertPerson();
        UUID organization = insertOrganization();

        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO patient.consent
                    (id, patient_id, grantee_person_id, grantee_organization_id, scope)
                VALUES (?, ?, ?, ?, 'MEDICAL_RECORD')
                """,
                UUID.randomUUID(), patient, grantee, organization))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsForeignKeyViolation() {
        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO patient.patient_registration
                    (id, patient_id, organization_id, registration_number)
                VALUES (?, ?, ?, ?)
                """,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), unique("REG")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidCheckConstraint() {
        UUID organization = insertOrganization();
        UUID professional = insertProfessional(insertPerson(), unique("PRO"));

        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO professional.professional_assignment
                    (id, professional_id, organization_id, start_date, end_date)
                VALUES (?, ?, ?, DATE '2026-09-18', DATE '2026-09-17')
                """,
                UUID.randomUUID(), professional, organization))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackFailedTransaction() {
        String personNumber = unique("ROLLBACK");

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.update(
                    """
                    INSERT INTO identity.person
                        (id, person_number, first_name, last_name)
                    VALUES (?, ?, 'Rollback', 'Test')
                    """,
                    UUID.randomUUID(), personNumber);
            throw new ExpectedRollbackException();
        })).isInstanceOf(ExpectedRollbackException.class);

        Integer persisted = jdbc.queryForObject(
                "SELECT count(*) FROM identity.person WHERE person_number = ?",
                Integer.class,
                personNumber);

        assertThat(persisted).isZero();
    }

    private boolean schemaExists(String schema) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regnamespace(?) IS NOT NULL",
                Boolean.class,
                schema));
    }

    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL",
                Boolean.class,
                table));
    }

    private UUID insertPerson() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO identity.person
                    (id, person_number, first_name, last_name)
                VALUES (?, ?, 'Integration', 'Test')
                """,
                id, unique("PER"));
        return id;
    }

    private UUID insertPatient(UUID person, String patientNumber) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO patient.patient (id, person_id, patient_number)
                VALUES (?, ?, ?)
                """,
                id, person, patientNumber);
        return id;
    }

    private UUID insertProfessional(UUID person, String professionalNumber) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO professional.professional
                    (id, person_id, professional_number, professional_type)
                VALUES (?, ?, ?, 'DOCTOR')
                """,
                id, person, professionalNumber);
        return id;
    }

    private UUID insertOrganization() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO organization.organization
                    (id, organization_number, name)
                VALUES (?, ?, 'Integration Test Organization')
                """,
                id, unique("ORG"));
        return id;
    }

    private UUID insertDepartment(UUID organization) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO organization.department
                    (id, organization_id, code, name)
                VALUES (?, ?, ?, 'Integration Test Department')
                """,
                id, organization, unique("DEP"));
        return id;
    }

    private UUID insertService(UUID department) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO organization.service
                    (id, department_id, code, name)
                VALUES (?, ?, ?, 'Integration Test Service')
                """,
                id, department, unique("SVC"));
        return id;
    }

    private int insertAssignment(
            UUID professional,
            UUID organization,
            UUID department,
            UUID service) {
        return jdbc.update(
                """
                INSERT INTO professional.professional_assignment
                    (id, professional_id, organization_id, department_id, service_id, start_date)
                VALUES (?, ?, ?, ?, ?, CURRENT_DATE)
                """,
                UUID.randomUUID(), professional, organization, department, service);
    }

    private void insertPatientRegistration(
            UUID patient,
            UUID organization,
            String registrationNumber) {
        jdbc.update(
                """
                INSERT INTO patient.patient_registration
                    (id, patient_id, organization_id, registration_number)
                VALUES (?, ?, ?, ?)
                """,
                UUID.randomUUID(), patient, organization, registrationNumber);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static final class ExpectedRollbackException extends RuntimeException {
    }
}
