package org.novasos.healthysv2.hospital;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "department", schema = "organization")
class Department {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id")
    private Organization organization;
    @Column(nullable = false, length = 50) private String code;
    @Column(nullable = false) private String name;
    private String description;
    @Column(nullable = false, length = 30) private String status = "ACTIVE";
    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CareService> services = new ArrayList<>();
    protected Department() {}
    static Department create(Organization org, String code, String name, String description, String status) {
        Department item = new Department(); item.organization = org; item.code = required(code); item.update(name, description, status); return item;
    }
    void update(String name, String description, String status) { this.name = required(name); this.description = trim(description); this.status = status == null ? "ACTIVE" : required(status).toUpperCase(); }
    CareService addService(String code, String name, String description, String status) { CareService item = CareService.create(this, code, name, description, status); services.add(item); return item; }
    void removeService(CareService item) { services.remove(item); }
    UUID getId() { return id; } Organization getOrganization() { return organization; } String getCode() { return code; } String getName() { return name; } String getDescription() { return description; } String getStatus() { return status; } List<CareService> getServices() { return services; }
    private static String required(String v) { if (v == null || v.isBlank()) throw new IllegalArgumentException("value must not be blank"); return v.trim(); }
    private static String trim(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
