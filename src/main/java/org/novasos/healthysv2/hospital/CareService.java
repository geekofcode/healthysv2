package org.novasos.healthysv2.hospital;

import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "service", schema = "organization")
class CareService {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "department_id") private Department department;
    @Column(nullable = false, length = 50) private String code;
    @Column(nullable = false) private String name;
    private String description;
    @Column(nullable = false, length = 30) private String status = "ACTIVE";
    protected CareService() {}
    static CareService create(Department department, String code, String name, String description, String status) { CareService item = new CareService(); item.department = department; item.code = required(code); item.update(name, description, status); return item; }
    void update(String name, String description, String status) { this.name = required(name); this.description = description; this.status = status == null ? "ACTIVE" : required(status).toUpperCase(); }
    UUID getId() { return id; } Department getDepartment() { return department; } String getCode() { return code; } String getName() { return name; } String getDescription() { return description; } String getStatus() { return status; }
    private static String required(String v) { if (v == null || v.isBlank()) throw new IllegalArgumentException("value must not be blank"); return v.trim(); }
}
