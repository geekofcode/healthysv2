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
    private final org.novasos.healthysv2.patient.CurrentUserContext users;
    private final OrganizationRepository organizations;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final DepartmentRepository departments;
    private final CareServiceRepository services;
    private final RoomRepository rooms;
    private final BedRepository beds;

    OrganizationService(OrganizationRepository organizations, DepartmentRepository departments,
            CareServiceRepository services, RoomRepository rooms, BedRepository beds,
            org.novasos.healthysv2.patient.CurrentUserContext users, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.users = users; this.jdbc = jdbc;
        this.organizations = organizations; this.departments = departments;
        this.services = services; this.rooms = rooms; this.beds = beds;
    }

    OrganizationResponse create(OrganizationRequest request) {
        if (organizations.existsByNumber(request.number())) conflict("ORGANIZATION_NUMBER_ALREADY_EXISTS", "error.organization.number.exists");
        var item = Organization.create(request.number(), request.name(), request.legalName(),
                request.organizationTypeId(), request.phone(), request.email(), request.website(), request.status());
        validateAdministrator(request.administratorPersonId());
        item.configure(request.timezone(), request.administratorPersonId());
        return response(organizations.saveAndFlush(item));
    }
    @Transactional(readOnly=true) PageResponse<OrganizationSummary> findAll(String query, String status, Pageable pageable) {
        var viewer = viewerOrganization();
        var page = organizations.search(viewer, query.trim(), status.trim(), pageable);
        return PageResponse.from(page.map(this::summary));
    }
    @Transactional(readOnly=true) OrganizationResponse find(UUID id) {
        UUID scope = viewerOrganization();
        if (scope != null && !scope.equals(id)) throw new org.springframework.security.access.AccessDeniedException("Organization outside viewer scope");
        return response(organization(id));
    }
    OrganizationResponse update(UUID id, OrganizationUpdateRequest request) {
        requireAdministrator(id); Organization item = organization(id);
        boolean platform = users.current().has("PLATFORM_ADMIN");
        if (!platform && ((request.administratorPersonId()!=null && !request.administratorPersonId().equals(item.getAdministratorPersonId())) || (request.status()!=null && !request.status().equals(item.getStatus())))) throw new org.springframework.security.access.AccessDeniedException("Platform administration required");
        validateAdministrator(request.administratorPersonId());
        item.configure(request.timezone(), request.administratorPersonId()==null ? item.getAdministratorPersonId() : request.administratorPersonId()); item.update(request.name(), request.legalName(), request.organizationTypeId(), request.phone(), request.email(), request.website(), request.status()); return response(item);
    }
    void delete(UUID id) { changeStatus(id, "INACTIVE"); }
    OrganizationResponse changeStatus(UUID id, String status) {
        if (!users.current().has("PLATFORM_ADMIN")) throw new org.springframework.security.access.AccessDeniedException("Platform administration required");
        var item=organization(id); item.changeStatus(status); return response(item);
    }

    DepartmentResponse addDepartment(UUID organizationId, DepartmentRequest request) {
        requireAdministrator(organizationId); Organization org = organization(organizationId);
        if (departments.existsByOrganizationIdAndCode(organizationId, request.code())) conflict("DEPARTMENT_CODE_ALREADY_EXISTS", "error.department.code.exists");
        return department(departments.saveAndFlush(
                org.addDepartment(request.code(), request.name(), request.description(), request.status())));
    }
    DepartmentResponse updateDepartment(UUID organizationId, UUID id, DepartmentUpdateRequest request) {
        requireAdministrator(organizationId); Department item = departmentEntity(organizationId, id); item.update(request.name(), request.description(), request.status()); return department(item);
    }
    void deleteDepartment(UUID organizationId, UUID id) { requireAdministrator(organizationId); Organization org = organization(organizationId); org.removeDepartment(departmentEntity(organizationId, id)); }

    ServiceResponse addService(UUID organizationId, UUID departmentId, ServiceRequest request) {
        requireAdministrator(organizationId); Department parent = departmentEntity(organizationId, departmentId);
        if (services.existsByDepartmentIdAndCode(departmentId, request.code())) conflict("SERVICE_CODE_ALREADY_EXISTS", "error.service.code.exists");
        return service(services.saveAndFlush(
                parent.addService(request.code(), request.name(), request.description(), request.status())));
    }
    ServiceResponse updateService(UUID organizationId, UUID departmentId, UUID id, ServiceUpdateRequest request) {
        requireAdministrator(organizationId); departmentEntity(organizationId, departmentId);
        CareService item = services.findByIdAndDepartmentId(id, departmentId).orElseThrow(() -> notFound("Service", id));
        item.update(request.name(), request.description(), request.status()); return service(item);
    }
    void deleteService(UUID organizationId, UUID departmentId, UUID id) {
        requireAdministrator(organizationId); Department parent = departmentEntity(organizationId, departmentId);
        CareService item = services.findByIdAndDepartmentId(id, departmentId).orElseThrow(() -> notFound("Service", id)); parent.removeService(item);
    }

    RoomResponse addRoom(UUID organizationId, RoomRequest request) {
        requireAdministrator(organizationId); Organization org = organization(organizationId);
        if (rooms.existsByOrganizationIdAndRoomNumber(organizationId, request.roomNumber())) conflict("ROOM_NUMBER_ALREADY_EXISTS", "error.room.number.exists");
        Department department = request.departmentId() == null ? null : departmentEntity(organizationId, request.departmentId());
        return room(rooms.saveAndFlush(
                org.addRoom(department, request.roomNumber(), request.type(), request.status())));
    }
    RoomResponse updateRoom(UUID organizationId, UUID id, RoomUpdateRequest request) {
        requireAdministrator(organizationId); Room item = roomEntity(organizationId, id);
        Department department = request.departmentId() == null ? null : departmentEntity(organizationId, request.departmentId());
        item.update(department, request.type(), request.status()); return room(item);
    }
    void deleteRoom(UUID organizationId, UUID id) { requireAdministrator(organizationId); organization(organizationId).removeRoom(roomEntity(organizationId, id)); }

    BedResponse addBed(UUID organizationId, UUID roomId, BedRequest request) {
        requireAdministrator(organizationId); Room parent = roomEntity(organizationId, roomId);
        if (beds.existsByRoomIdAndBedNumber(roomId, request.bedNumber())) conflict("BED_NUMBER_ALREADY_EXISTS", "error.bed.number.exists");
        return bed(beds.saveAndFlush(
                parent.addBed(request.bedNumber(), request.status())));
    }
    BedResponse updateBed(UUID organizationId, UUID roomId, UUID id, BedUpdateRequest request) {
        requireAdministrator(organizationId); roomEntity(organizationId, roomId);
        Bed item = beds.findByIdAndRoomId(id, roomId).orElseThrow(() -> notFound("Bed", id)); item.update(request.status()); return bed(item);
    }
    void deleteBed(UUID organizationId, UUID roomId, UUID id) {
        requireAdministrator(organizationId); Room parent = roomEntity(organizationId, roomId);
        Bed item = beds.findByIdAndRoomId(id, roomId).orElseThrow(() -> notFound("Bed", id)); parent.removeBed(item);
    }

    private UUID viewerOrganization() {
        var user = users.current();
        if (user.has("PLATFORM_ADMIN")) return null;
        if (user.organizationId() == null) throw new org.springframework.security.access.AccessDeniedException("Organization context required for gestionnaire");
        return user.organizationId();
    }

    private void validateAdministrator(UUID person) {
        if (person != null && !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from identity.person where id=? and status='ACTIVE')", Boolean.class, person))) throw notFound("Person",person);
    }
    private void requireAdministrator(UUID organization) {
        var user=users.current();
        if (user.has("PLATFORM_ADMIN")) return;
        if (user.personId()==null || !organization.equals(user.organizationId())) throw new org.springframework.security.access.AccessDeniedException("Organization administrator required");
        boolean allowed=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from organization.organization where id=? and administrator_person_id=?) or exists(select 1 from licensing.member where organization_id=? and person_id=? and role='HOSPITAL_ADMIN' and status='ACTIVE')",Boolean.class,organization,user.personId(),organization,user.personId()));
        if (!allowed) throw new org.springframework.security.access.AccessDeniedException("Organization administrator required");
    }
    private Organization organization(UUID id) { return organizations.findById(id).orElseThrow(() -> notFound("Organization", id)); }
    private Department departmentEntity(UUID orgId, UUID id) { return departments.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> notFound("Department", id)); }
    private Room roomEntity(UUID orgId, UUID id) { return rooms.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> notFound("Room", id)); }
    private ResourceNotFoundException notFound(String resource, Object id) { return new ResourceNotFoundException(resource, id); }
    private void conflict(String code, String key) { throw new ConflictException(code, key); }
    private OrganizationSummary summary(Organization o) { return new OrganizationSummary(o.getId(), o.getNumber(), o.getName(), o.getLegalName(), o.getStatus()); }
    private OrganizationResponse response(Organization o) { return new OrganizationResponse(o.getId(), o.getNumber(), o.getName(), o.getLegalName(), o.getOrganizationTypeId(), o.getPhone(), o.getEmail(), o.getWebsite(), o.getStatus(), o.getCreatedAt(), o.getUpdatedAt(), o.getDepartments().stream().sorted(Comparator.comparing(Department::getCode)).map(this::department).toList(), o.getRooms().stream().sorted(Comparator.comparing(Room::getRoomNumber)).map(this::room).toList(), o.getTimezone(), o.getAdministratorPersonId()); }
    private DepartmentResponse department(Department d) { return new DepartmentResponse(d.getId(), d.getCode(), d.getName(), d.getDescription(), d.getStatus(), d.getServices().stream().sorted(Comparator.comparing(CareService::getCode)).map(this::service).toList()); }
    private ServiceResponse service(CareService s) { return new ServiceResponse(s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getStatus()); }
    private RoomResponse room(Room r) { return new RoomResponse(r.getId(), r.getDepartment() == null ? null : r.getDepartment().getId(), r.getRoomNumber(), r.getType(), r.getStatus(), r.getBeds().stream().sorted(Comparator.comparing(Bed::getBedNumber)).map(this::bed).toList()); }
    private BedResponse bed(Bed b) { return new BedResponse(b.getId(), b.getBedNumber(), b.getStatus()); }
}
