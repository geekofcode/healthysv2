package org.novasos.healthysv2.hospital;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.novasos.healthysv2.shared.persistence.AuditableEntity;

@Entity
@Table(name = "organization", schema = "organization")
class Organization extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "organization_number", nullable = false, unique = true, length = 50)
    private String number;
    @Column(nullable = false)
    private String name;
    @Column(name = "legal_name")
    private String legalName;
    @Column(name = "organization_type_id")
    private UUID organizationTypeId;
    private String phone;
    private String email;
    private String website;
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Department> departments = new ArrayList<>();
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Room> rooms = new ArrayList<>();

    protected Organization() {}

    static Organization create(String number, String name, String legalName, UUID typeId,
            String phone, String email, String website, String status) {
        Organization item = new Organization();
        item.number = required(number, "number");
        item.update(name, legalName, typeId, phone, email, website, status);
        return item;
    }

    void update(String name, String legalName, UUID typeId, String phone,
            String email, String website, String status) {
        this.name = required(name, "name");
        this.legalName = trim(legalName);
        this.organizationTypeId = typeId;
        this.phone = trim(phone);
        this.email = trim(email);
        this.website = trim(website);
        this.status = status == null ? "ACTIVE" : required(status, "status").toUpperCase();
    }

    Department addDepartment(String code, String name, String description, String status) {
        Department child = Department.create(this, code, name, description, status);
        departments.add(child);
        return child;
    }

    Room addRoom(Department department, String roomNumber, String type, String status) {
        if (department != null && department.getOrganization() != this) {
            throw new IllegalArgumentException("Department must belong to the organization");
        }
        Room child = Room.create(this, department, roomNumber, type, status);
        rooms.add(child);
        return child;
    }

    void removeDepartment(Department child) {
        rooms.stream().filter(room -> room.getDepartment() == child)
                .forEach(room -> room.assignDepartment(null));
        departments.remove(child);
    }
    void removeRoom(Room child) { rooms.remove(child); }
    UUID getId() { return id; }
    String getNumber() { return number; }
    String getName() { return name; }
    String getLegalName() { return legalName; }
    UUID getOrganizationTypeId() { return organizationTypeId; }
    String getPhone() { return phone; }
    String getEmail() { return email; }
    String getWebsite() { return website; }
    String getStatus() { return status; }
    List<Department> getDepartments() { return departments; }
    List<Room> getRooms() { return rooms; }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
