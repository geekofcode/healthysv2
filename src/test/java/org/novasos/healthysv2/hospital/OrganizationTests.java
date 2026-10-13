package org.novasos.healthysv2.hospital;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OrganizationTests {
    @Test void buildsAConsistentOrganizationHierarchy() {
        Organization organization = Organization.create("ORG-1", "Hospital", null, null, null, null, null, null);
        Department department = organization.addDepartment("CARD", "Cardiology", null, null);
        department.addService("CONS", "Consultations", null, null);
        Room room = organization.addRoom(department, "101", "CARE", null);
        room.addBed("A", null);
        assertThat(organization.getDepartments()).hasSize(1);
        assertThat(department.getServices()).hasSize(1);
        assertThat(organization.getRooms()).hasSize(1);
        assertThat(room.getBeds()).hasSize(1);
    }

    @Test void refusesADepartmentFromAnotherOrganizationForARoom() {
        Organization first = Organization.create("ORG-1", "First", null, null, null, null, null, null);
        Organization second = Organization.create("ORG-2", "Second", null, null, null, null, null, null);
        Department foreign = second.addDepartment("ER", "Emergency", null, null);
        assertThatThrownBy(() -> first.addRoom(foreign, "101", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void removingDepartmentUnassignsItsRooms() {
        Organization organization = Organization.create("ORG-1", "Hospital", null, null, null, null, null, null);
        Department department = organization.addDepartment("ER", "Emergency", null, null);
        Room room = organization.addRoom(department, "101", null, null);
        organization.removeDepartment(department);
        assertThat(room.getDepartment()).isNull();
    }
    @Test void validatesTimezonesAndPreservesInactiveStatusWhenOmitted() {
        var organization=Organization.create("ORG-TZ","Clinic",null,null,null,null,null,"INACTIVE");
        organization.configure("America/Toronto",java.util.UUID.randomUUID());
        organization.update("Clinic",null,null,null,null,null,null);
        assertThat(organization.getTimezone()).isEqualTo("America/Toronto");
        assertThat(organization.getStatus()).isEqualTo("INACTIVE");
        assertThatThrownBy(()->organization.configure("not-a-zone",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->organization.changeStatus("anything")).isInstanceOf(IllegalArgumentException.class);
    }
}
