package org.novasos.healthysv2.pharmacy;

import static org.novasos.healthysv2.pharmacy.api.PharmacyDtos.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.novasos.healthysv2.prescription.PrescriptionDispensing;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class PharmacyService {
    private final PrescriptionDispensing prescriptions;
    private final DispenseRepository dispenses;
    private final MedicationStockRepository stocks;
    private final JdbcTemplate jdbc;

    PharmacyService(PrescriptionDispensing prescriptions, DispenseRepository dispenses,
                    MedicationStockRepository stocks, JdbcTemplate jdbc) {
        this.prescriptions = prescriptions;
        this.dispenses = dispenses;
        this.stocks = stocks;
        this.jdbc = jdbc;
    }

    DispenseResponse dispense(UUID prescriptionId, CreateDispenseRequest request) {
        var prescription = prescriptions.prepare(prescriptionId);
        require("organization.organization", request.pharmacyOrganizationId(), "Organization");
        requirePharmacist(request.pharmacistId(), request.pharmacyOrganizationId());

        var stockUses = new ArrayList<StockUse>();
        var prescriptionLines = new ArrayList<PrescriptionDispensing.DispenseLine>();
        var dispenseLines = new ArrayList<Dispense.DispenseLine>();
        var seenItems = new HashSet<UUID>();
        for (var line : request.items()) {
            if (!seenItems.add(line.prescriptionItemId())) {
                throw rule("INVALID_DISPENSE_QUANTITY", "error.pharmacy.dispense-quantity");
            }
            PrescriptionDispensing.ItemForDispensing item;
            try {
                item = prescription.item(line.prescriptionItemId());
            } catch (IllegalArgumentException exception) {
                throw notFound("PrescriptionItem", line.prescriptionItemId());
            }
            if (line.quantityDispensed().compareTo(item.quantityRemaining()) > 0) {
                throw rule("INVALID_DISPENSE_QUANTITY", "error.pharmacy.dispense-quantity");
            }
            var stock = stocks.lock(request.pharmacyOrganizationId(), item.medicationCatalogId(), line.batchNumber())
                    .orElseThrow(() -> rule("MEDICATION_STOCK_NOT_FOUND", "error.pharmacy.stock-not-found"));
            if (stock.expired()) {
                throw rule("MEDICATION_BATCH_EXPIRED", "error.pharmacy.batch-expired");
            }
            if (stock.getQuantity().compareTo(line.quantityDispensed()) < 0) {
                throw rule("INSUFFICIENT_STOCK", "error.pharmacy.insufficient-stock");
            }
            stockUses.add(new StockUse(stock, line.quantityDispensed()));
            prescriptionLines.add(new PrescriptionDispensing.DispenseLine(
                    line.prescriptionItemId(), line.quantityDispensed()));
            dispenseLines.add(new Dispense.DispenseLine(
                    line.prescriptionItemId(), line.quantityDispensed(), line.batchNumber()));
        }

        prescriptions.record(prescriptionId, prescriptionLines);
        stockUses.forEach(use -> use.stock().consume(use.quantity()));
        var dispense = dispenses.saveAndFlush(Dispense.create(prescriptionId, request.pharmacyOrganizationId(),
                request.pharmacistId(), dispenseLines));
        stocks.flush();
        auditDispense(dispense, "DISPENSE");
        return response(dispense);
    }

    @Transactional(readOnly = true)
    List<DispenseResponse> history(UUID prescriptionId) {
        prescriptions.verifyReadAccess(prescriptionId);
        return dispenses.findByPrescriptionIdOrderByDispensedAtDesc(prescriptionId).stream()
                .map(this::response).toList();
    }

    MedicationStockResponse replenish(ReplenishStockRequest request) {
        require("organization.organization", request.organizationId(), "Organization");
        requireMedication(request.medicationCatalogId());
        if (request.expirationDate() != null && request.expirationDate().isBefore(LocalDate.now())) {
            throw rule("MEDICATION_BATCH_EXPIRED", "error.pharmacy.batch-expired");
        }
        var stock = stocks.lock(request.organizationId(), request.medicationCatalogId(), request.batchNumber())
                .orElse(null);
        if (stock == null) {
            stock = MedicationStock.create(request.organizationId(), request.medicationCatalogId(),
                    request.batchNumber(), request.quantity(), request.expirationDate());
        } else {
            stock.replenish(request.quantity(), request.expirationDate());
        }
        stock = stocks.saveAndFlush(stock);
        auditStock(stock, "REPLENISH_STOCK");
        return response(stock);
    }

    @Transactional(readOnly = true)
    List<MedicationStockResponse> stock(UUID organization, UUID medication) {
        return stocks.search(organization, medication).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    List<MedicationCatalogResponse> catalog(String query) {
        String value = query == null ? "" : query.trim();
        return jdbc.query("select id,code,name,generic_name,form,strength,atc_code from catalog.medication_catalog "
                        + "where active=true and (?='' or lower(coalesce(code,'')) like lower(?) "
                        + "or lower(name) like lower(?) or lower(coalesce(generic_name,'')) like lower(?)) "
                        + "order by name limit 100",
                (result, row) -> new MedicationCatalogResponse((UUID) result.getObject(1), result.getString(2),
                        result.getString(3), result.getString(4), result.getString(5), result.getString(6),
                        result.getString(7)), value, "%" + value + "%", "%" + value + "%", "%" + value + "%");
    }

    private void requireMedication(UUID id) {
        if (!exists("select count(*) from catalog.medication_catalog where id=? and active=true", id)) {
            throw notFound("MedicationCatalog", id);
        }
    }

    private void requirePharmacist(UUID id, UUID organization) {
        if (!exists("select count(*) from professional.professional p "
                        + "join professional.professional_assignment a on a.professional_id=p.id "
                        + "where p.id=? and p.professional_type='PHARMACIST' and p.status='ACTIVE' "
                        + "and a.organization_id=? and a.status='ACTIVE' and a.start_date<=current_date "
                        + "and (a.end_date is null or a.end_date>=current_date)", id, organization)) {
            throw rule("PHARMACIST_NOT_ASSIGNED", "error.pharmacy.pharmacist-assignment");
        }
    }

    private void require(String table, UUID id, String resource) {
        if (!exists("select count(*) from " + table + " where id=?", id)) throw notFound(resource, id);
    }

    private boolean exists(String sql, Object... arguments) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class, arguments)) > 0;
    }

    private DispenseResponse response(Dispense dispense) {
        return new DispenseResponse(dispense.getId(), dispense.getNumber(), dispense.getPharmacyOrganizationId(),
                dispense.getPharmacistId(), dispense.getDispensedAt(), dispense.getStatus(),
                dispense.getItems().stream().map(this::response).toList());
    }

    private DispenseItemResponse response(DispenseItem item) {
        return new DispenseItemResponse(item.getId(), item.getPrescriptionItemId(),
                item.getQuantity(), item.getBatch());
    }

    private MedicationStockResponse response(MedicationStock stock) {
        String[] medication = jdbc.query("select code,name from catalog.medication_catalog where id=?",
                result -> result.next() ? new String[]{result.getString(1), result.getString(2)}
                        : new String[]{null, null}, stock.getMedicationId());
        return new MedicationStockResponse(stock.getId(), stock.getOrganizationId(), stock.getMedicationId(),
                medication[0], medication[1], stock.getBatch(), stock.getQuantity(), stock.getExpirationDate(),
                stock.expired());
    }

    private void auditDispense(Dispense dispense, String action) {
        jdbc.update("insert into audit.audit_log(actor_person_id,organization_id,module,entity_type,entity_id,action,new_value) "
                        + "values (?,?,?,?,?,?,?::jsonb)", actor(), dispense.getPharmacyOrganizationId(), "PHARMACY",
                "Dispense", dispense.getId(), action,
                "{\"prescriptionId\":\"" + dispense.getPrescriptionId() + "\"}");
    }

    private void auditStock(MedicationStock stock, String action) {
        jdbc.update("insert into audit.audit_log(actor_person_id,organization_id,module,entity_type,entity_id,action,new_value) "
                        + "values (?,?,?,?,?,?,?::jsonb)", actor(), stock.getOrganizationId(), "PHARMACY",
                "MedicationStock", stock.getId(), action, "{\"quantity\":" + stock.getQuantity() + "}");
    }

    private UUID actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return null;
        try {
            UUID subject = UUID.fromString(jwt.getToken().getSubject());
            return jdbc.query("select id from identity.person where keycloak_user_id=? or id=? limit 1",
                    result -> result.next() ? (UUID) result.getObject(1) : null, subject, subject);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ResourceNotFoundException notFound(String resource, Object id) {
        return new ResourceNotFoundException(resource, id);
    }

    private BusinessRuleException rule(String code, String messageKey) {
        return new BusinessRuleException(code, messageKey);
    }

    private record StockUse(MedicationStock stock, BigDecimal quantity) {}
}
