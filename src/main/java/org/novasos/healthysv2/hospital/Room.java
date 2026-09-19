package org.novasos.healthysv2.hospital;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "room", schema = "organization")
class Room {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "department_id") private Department department;
    @Column(name = "room_number", nullable = false, length = 50) private String roomNumber;
    private String type;
    @Column(nullable = false, length = 30) private String status = "AVAILABLE";
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true) private List<Bed> beds = new ArrayList<>();
    protected Room() {}
    static Room create(Organization organization, Department department, String number, String type, String status) { Room item = new Room(); item.organization = organization; item.department = department; item.roomNumber = required(number); item.update(department, type, status); return item; }
    void update(Department department, String type, String status) { if (department != null && department.getOrganization() != organization) throw new IllegalArgumentException("Department must belong to the organization"); this.department = department; this.type = type; this.status = status == null ? "AVAILABLE" : required(status).toUpperCase(); }
    void assignDepartment(Department department) { this.department = department; }
    Bed addBed(String number, String status) { Bed item = Bed.create(this, number, status); beds.add(item); return item; }
    void removeBed(Bed item) { beds.remove(item); }
    UUID getId() { return id; } Organization getOrganization() { return organization; } Department getDepartment() { return department; } String getRoomNumber() { return roomNumber; } String getType() { return type; } String getStatus() { return status; } List<Bed> getBeds() { return beds; }
    private static String required(String v) { if (v == null || v.isBlank()) throw new IllegalArgumentException("value must not be blank"); return v.trim(); }
}
