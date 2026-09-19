package org.novasos.healthysv2.hospital;

import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "bed", schema = "organization")
class Bed {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id") private Room room;
    @Column(name = "bed_number", nullable = false, length = 50) private String bedNumber;
    @Column(nullable = false, length = 30) private String status = "AVAILABLE";
    protected Bed() {}
    static Bed create(Room room, String number, String status) { Bed item = new Bed(); item.room = room; item.bedNumber = required(number); item.update(status); return item; }
    void update(String status) { this.status = status == null ? "AVAILABLE" : required(status).toUpperCase(); }
    UUID getId() { return id; } Room getRoom() { return room; } String getBedNumber() { return bedNumber; } String getStatus() { return status; }
    private static String required(String v) { if (v == null || v.isBlank()) throw new IllegalArgumentException("value must not be blank"); return v.trim(); }
}
