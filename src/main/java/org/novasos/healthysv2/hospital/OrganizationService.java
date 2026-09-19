package org.novasos.healthysv2.hospital;

import java.util.Comparator;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.novasos.healthysv2.hospital.api.OrganizationDtos.*;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;

@Service
@Transactional
class OrganizationService {
    private final OrganizationRepository organizations;
    private final DepartmentRepository departments;
    private final CareServiceRepository services;
    private final RoomRepository rooms;
    private final BedRepository beds;

    OrganizationService(OrganizationRepository organizations, DepartmentRepository departments,
            CareServiceRepository services, RoomRepository rooms, BedRepository beds) {
        this.organizations = organizations; this.departments = departments;
        this.services = services; this.rooms = rooms; this.beds = beds;
    }

    OrganizationResponse create(OrganizationRequest request) {
        if (organizations.existsByNumber(request.number())) conflict("ORGANIZATION_NUMBER_ALREADY_EXISTS", "error.organization.number.exists");
        return response(organizations.save(Organization.create(request.number(), request.name(), request.legalName(),
                request.organizationTypeId(), request.phone(), request.email(), request.website(), request.status())));
    }
    @Transactional(readOnly=true) PageResponse<OrganizationSummary> findAll(Pageable pageable) {
        return PageResponse.from(organizations.findAll(pageable).map(this::summary));
    }
    @Transactional(readOnly=true) OrganizationResponse find(UUID id) { return response(organization(id)); }
    OrganizationResponse update(UUID id, OrganizationUpdateRequest request) {
        Organization item = organization(id); item.update(request.name(), request.legalName(), request.organizationTypeId(), request.phone(), request.email(), request.website(), request.status()); return response(item);
    }
    void delete(UUID id) { organizations.delete(organization(id)); }

    DepartmentResponse addDepartment(UUID organizationId, DepartmentRequest request) {
        Organization org = organization(organizationId);
        if (departments.existsByOrganizationIdAndCode(organizationId, request.code())) conflict("DEPARTMENT_CODE_ALREADY_EXISTS", "error.department.code.exists");
        return department(departments.saveAndFlush(
                org.addDepartment(request.code(), request.name(), request.description(), request.status())));
    }
    DepartmentResponse updateDepartment(UUID organizationId, UUID id, DepartmentUpdateRequest request) {
        Department item = departmentEntity(organizationId, id); item.update(request.name(), request.description(), request.status()); return department(item);
    }
    void deleteDepartment(UUID organizationId, UUID id) { Organization org = organization(organizationId); org.removeDepartment(departmentEntity(organizationId, id)); }

    ServiceResponse addService(UUID organizationId, UUID departmentId, ServiceRequest request) {
        Department parent = departmentEntity(organizationId, departmentId);
        if (services.existsByDepartmentIdAndCode(departmentId, request.code())) conflict("SERVICE_CODE_ALREADY_EXISTS", "error.service.code.exists");
        return service(services.saveAndFlush(
                parent.addService(request.code(), request.name(), request.description(), request.status())));
    }
    ServiceResponse updateService(UUID organizationId, UUID departmentId, UUID id, ServiceUpdateRequest request) {
        departmentEntity(organizationId, departmentId);
        CareService item = services.findByIdAndDepartmentId(id, departmentId).orElseThrow(() -> notFound("Service", id));
        item.update(request.name(), request.description(), request.status()); return service(item);
    }
    void deleteService(UUID organizationId, UUID departmentId, UUID id) {
        Department parent = departmentEntity(organizationId, departmentId);
        CareService item = services.findByIdAndDepartmentId(id, departmentId).orElseThrow(() -> notFound("Service", id)); parent.removeService(item);
    }

    RoomResponse addRoom(UUID organizationId, RoomRequest request) {
        Organization org = organization(organizationId);
        if (rooms.existsByOrganizationIdAndRoomNumber(organizationId, request.roomNumber())) conflict("ROOM_NUMBER_ALREADY_EXISTS", "error.room.number.exists");
        Department department = request.departmentId() == null ? null : departmentEntity(organizationId, request.departmentId());
        return room(rooms.saveAndFlush(
                org.addRoom(department, request.roomNumber(), request.type(), request.status())));
    }
    RoomResponse updateRoom(UUID organizationId, UUID id, RoomUpdateRequest request) {
        Room item = roomEntity(organizationId, id);
        Department department = request.departmentId() == null ? null : departmentEntity(organizationId, request.departmentId());
        item.update(department, request.type(), request.status()); return room(item);
    }
    void deleteRoom(UUID organizationId, UUID id) { organization(organizationId).removeRoom(roomEntity(organizationId, id)); }

    BedResponse addBed(UUID organizationId, UUID roomId, BedRequest request) {
        Room parent = roomEntity(organizationId, roomId);
        if (beds.existsByRoomIdAndBedNumber(roomId, request.bedNumber())) conflict("BED_NUMBER_ALREADY_EXISTS", "error.bed.number.exists");
        return bed(beds.saveAndFlush(
                parent.addBed(request.bedNumber(), request.status())));
    }
    BedResponse updateBed(UUID organizationId, UUID roomId, UUID id, BedUpdateRequest request) {
        roomEntity(organizationId, roomId);
        Bed item = beds.findByIdAndRoomId(id, roomId).orElseThrow(() -> notFound("Bed", id)); item.update(request.status()); return bed(item);
    }
    void deleteBed(UUID organizationId, UUID roomId, UUID id) {
        Room parent = roomEntity(organizationId, roomId);
        Bed item = beds.findByIdAndRoomId(id, roomId).orElseThrow(() -> notFound("Bed", id)); parent.removeBed(item);
    }

    private Organization organization(UUID id) { return organizations.findById(id).orElseThrow(() -> notFound("Organization", id)); }
    private Department departmentEntity(UUID orgId, UUID id) { return departments.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> notFound("Department", id)); }
    private Room roomEntity(UUID orgId, UUID id) { return rooms.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> notFound("Room", id)); }
    private ResourceNotFoundException notFound(String resource, Object id) { return new ResourceNotFoundException(resource, id); }
    private void conflict(String code, String key) { throw new ConflictException(code, key); }
    private OrganizationSummary summary(Organization o) { return new OrganizationSummary(o.getId(), o.getNumber(), o.getName(), o.getLegalName(), o.getStatus()); }
    private OrganizationResponse response(Organization o) { return new OrganizationResponse(o.getId(), o.getNumber(), o.getName(), o.getLegalName(), o.getOrganizationTypeId(), o.getPhone(), o.getEmail(), o.getWebsite(), o.getStatus(), o.getCreatedAt(), o.getUpdatedAt(), o.getDepartments().stream().sorted(Comparator.comparing(Department::getCode)).map(this::department).toList(), o.getRooms().stream().sorted(Comparator.comparing(Room::getRoomNumber)).map(this::room).toList()); }
    private DepartmentResponse department(Department d) { return new DepartmentResponse(d.getId(), d.getCode(), d.getName(), d.getDescription(), d.getStatus(), d.getServices().stream().sorted(Comparator.comparing(CareService::getCode)).map(this::service).toList()); }
    private ServiceResponse service(CareService s) { return new ServiceResponse(s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getStatus()); }
    private RoomResponse room(Room r) { return new RoomResponse(r.getId(), r.getDepartment() == null ? null : r.getDepartment().getId(), r.getRoomNumber(), r.getType(), r.getStatus(), r.getBeds().stream().sorted(Comparator.comparing(Bed::getBedNumber)).map(this::bed).toList()); }
    private BedResponse bed(Bed b) { return new BedResponse(b.getId(), b.getBedNumber(), b.getStatus()); }
}
