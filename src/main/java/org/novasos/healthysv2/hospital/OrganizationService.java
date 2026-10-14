package org.novasos.healthysv2.hospital;

import java.util.Comparator;
import java.util.UUID;
import org.novasos.healthysv2.hospital.api.OrganizationDtos.*;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  OrganizationService(
      OrganizationRepository organizations,
      DepartmentRepository departments,
      CareServiceRepository services,
      RoomRepository rooms,
      BedRepository beds,
      org.novasos.healthysv2.patient.CurrentUserContext users,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.users = users;
    this.jdbc = jdbc;
    this.organizations = organizations;
    this.departments = departments;
    this.services = services;
    this.rooms = rooms;
    this.beds = beds;
  }

  private org.novasos.healthysv2.identity.api.EmployeeAccountProvisioning accounts;

  @org.springframework.beans.factory.annotation.Autowired
  void accounts(org.novasos.healthysv2.identity.api.EmployeeAccountProvisioning accounts) {
    this.accounts = accounts;
  }

  private org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions;

  @org.springframework.beans.factory.annotation.Autowired
  void permissions(
      org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions) {
    this.permissions = permissions;
  }

  private void requireStructure(UUID organizationId, String action) {
    var user = users.current();
    if (user.has("PLATFORM_ADMIN") || !organizationId.equals(user.organizationId()))
      throw new org.springframework.security.access.AccessDeniedException(
          "ORGANIZATION_OPERATION_DENIED");
    permissions.require(organizationId, "orgconfiguration", action);
  }

  OrganizationResponse create(OrganizationRequest request) {
    requireSupportedType(request.organizationTypeId());
    if (organizations.existsByNumber(request.number()))
      conflict("ORGANIZATION_NUMBER_ALREADY_EXISTS", "error.organization.number.exists");
    var item =
        Organization.create(
            request.number(),
            request.name(),
            request.legalName(),
            request.organizationTypeId(),
            request.phone(),
            request.email(),
            request.website(),
            request.status());
    if (!users.current().has("PLATFORM_ADMIN"))
      throw new org.springframework.security.access.AccessDeniedException(
          "PLATFORM_ADMIN_REQUIRED");
    UUID administrator = request.administratorPersonId();
    if (administrator != null && request.administratorAccount() != null)
      throw new IllegalArgumentException("ONE_ADMINISTRATOR_IDENTITY_REQUIRED");
    if (administrator == null && request.administratorAccount() != null) {
      var account = request.administratorAccount();
      administrator =
          accounts
              .ensureAccount(account.firstName(), account.lastName(), account.email())
              .personId();
    }
    if (administrator == null)
      throw new IllegalArgumentException("HOSPITAL_ADMIN_ACCOUNT_REQUIRED");
    validateAdministrator(administrator);
    item.configure(request.timezone(), administrator);
    organizations.saveAndFlush(item);
    jdbc.update(
        "insert into licensing.member(organization_id,person_id,role,status)"
            + " values(?,?,'HOSPITAL_ADMIN','ACTIVE')",
        item.getId(),
        administrator);
    return response(item);
  }

  @Transactional(readOnly = true)
  PageResponse<OrganizationSummary> findAll(String query, String status, Pageable pageable) {
    var viewer = viewerOrganization();
    var page = organizations.search(viewer, query.trim(), status.trim(), pageable);
    return PageResponse.from(page.map(this::summary));
  }

  @Transactional(readOnly = true)
  OrganizationResponse find(UUID id) {
    UUID scope = viewerOrganization();
    if (scope != null && !scope.equals(id))
      throw new org.springframework.security.access.AccessDeniedException(
          "Organization outside viewer scope");
    return response(organization(id));
  }

  OrganizationResponse update(UUID id, OrganizationUpdateRequest request) {
    requireAdministrator(id);
    Organization item = organization(id);
    boolean platform = users.current().has("PLATFORM_ADMIN");
    if (!platform
        && ((request.administratorPersonId() != null
                && !request.administratorPersonId().equals(item.getAdministratorPersonId()))
            || (request.status() != null && !request.status().equals(item.getStatus()))))
      throw new org.springframework.security.access.AccessDeniedException(
          "Platform administration required");
    if (!java.util.Objects.equals(request.organizationTypeId(), item.getOrganizationTypeId()))
      requireSupportedType(request.organizationTypeId());
    validateAdministrator(request.administratorPersonId());
    UUID previousAdministrator = item.getAdministratorPersonId();
    if (platform
        && request.administratorPersonId() != null
        && !request.administratorPersonId().equals(previousAdministrator)) {
      jdbc.update(
          "update licensing.member set status='INACTIVE',updated_at=now() where organization_id=?"
              + " and person_id=? and role='HOSPITAL_ADMIN' and status='ACTIVE'",
          id,
          previousAdministrator);
      jdbc.update(
          "insert into licensing.member(organization_id,person_id,role,status) select"
              + " ?,?,'HOSPITAL_ADMIN','ACTIVE' where not exists(select 1 from licensing.member"
              + " where organization_id=? and person_id=? and role='HOSPITAL_ADMIN' and"
              + " status<>'INACTIVE')",
          id,
          request.administratorPersonId(),
          id,
          request.administratorPersonId());
    }
    item.configure(
        request.timezone(),
        request.administratorPersonId() == null
            ? item.getAdministratorPersonId()
            : request.administratorPersonId());
    item.update(
        request.name(),
        request.legalName(),
        request.organizationTypeId(),
        request.phone(),
        request.email(),
        request.website(),
        request.status());
    return response(item);
  }

  private void requireSupportedType(UUID typeId) {
    if (typeId == null) return;
    var codes =
        jdbc.query(
            "select code from catalog.organization_type where id=?",
            (row, index) -> row.getString("code"),
            typeId);
    if (codes.stream()
        .anyMatch(
            code ->
                java.util.Set.of("LABORATORY", "LAB", "LABORATOIRE", "PHARMACY", "PHARMACIE")
                    .contains(code.toUpperCase(java.util.Locale.ROOT)))) {
      throw new org.novasos.healthysv2.shared.api.error.BusinessRuleException(
          "ORGANIZATION_TYPE_OUT_OF_SCOPE", "error.organization.type");
    }
  }

  void delete(UUID id) {
    changeStatus(id, "INACTIVE");
  }

  OrganizationResponse changeStatus(UUID id, String status) {
    if (!users.current().has("PLATFORM_ADMIN"))
      throw new org.springframework.security.access.AccessDeniedException(
          "Platform administration required");
    var item = organization(id);
    item.changeStatus(status);
    return response(item);
  }

  DepartmentResponse addDepartment(UUID organizationId, DepartmentRequest request) {
    requireStructure(organizationId, "CREATE");
    Organization org = organization(organizationId);
    if (departments.existsByOrganizationIdAndCode(organizationId, request.code()))
      conflict("DEPARTMENT_CODE_ALREADY_EXISTS", "error.department.code.exists");
    return department(
        departments.saveAndFlush(
            org.addDepartment(
                request.code(), request.name(), request.description(), request.status())));
  }

  DepartmentResponse updateDepartment(
      UUID organizationId, UUID id, DepartmentUpdateRequest request) {
    requireStructure(organizationId, "UPDATE");
    Department item = departmentEntity(organizationId, id);
    item.update(request.name(), request.description(), request.status());
    return department(item);
  }

  void deleteDepartment(UUID organizationId, UUID id) {
    requireStructure(organizationId, "DELETE");
    Organization org = organization(organizationId);
    org.removeDepartment(departmentEntity(organizationId, id));
  }

  ServiceResponse addService(UUID organizationId, UUID departmentId, ServiceRequest request) {
    requireStructure(organizationId, "CREATE");
    Department parent = departmentEntity(organizationId, departmentId);
    if (services.existsByDepartmentIdAndCode(departmentId, request.code()))
      conflict("SERVICE_CODE_ALREADY_EXISTS", "error.service.code.exists");
    return service(
        services.saveAndFlush(
            parent.addService(
                request.code(), request.name(), request.description(), request.status())));
  }

  ServiceResponse updateService(
      UUID organizationId, UUID departmentId, UUID id, ServiceUpdateRequest request) {
    requireStructure(organizationId, "UPDATE");
    departmentEntity(organizationId, departmentId);
    CareService item =
        services
            .findByIdAndDepartmentId(id, departmentId)
            .orElseThrow(() -> notFound("Service", id));
    item.update(request.name(), request.description(), request.status());
    return service(item);
  }

  void deleteService(UUID organizationId, UUID departmentId, UUID id) {
    requireStructure(organizationId, "DELETE");
    Department parent = departmentEntity(organizationId, departmentId);
    CareService item =
        services
            .findByIdAndDepartmentId(id, departmentId)
            .orElseThrow(() -> notFound("Service", id));
    parent.removeService(item);
  }

  RoomResponse addRoom(UUID organizationId, RoomRequest request) {
    requireStructure(organizationId, "CREATE");
    Organization org = organization(organizationId);
    if (rooms.existsByOrganizationIdAndRoomNumber(organizationId, request.roomNumber()))
      conflict("ROOM_NUMBER_ALREADY_EXISTS", "error.room.number.exists");
    Department department =
        request.departmentId() == null
            ? null
            : departmentEntity(organizationId, request.departmentId());
    return room(
        rooms.saveAndFlush(
            org.addRoom(department, request.roomNumber(), request.type(), request.status())));
  }

  RoomResponse updateRoom(UUID organizationId, UUID id, RoomUpdateRequest request) {
    requireStructure(organizationId, "UPDATE");
    Room item = roomEntity(organizationId, id);
    Department department =
        request.departmentId() == null
            ? null
            : departmentEntity(organizationId, request.departmentId());
    item.update(department, request.type(), request.status());
    return room(item);
  }

  void deleteRoom(UUID organizationId, UUID id) {
    requireStructure(organizationId, "DELETE");
    organization(organizationId).removeRoom(roomEntity(organizationId, id));
  }

  BedResponse addBed(UUID organizationId, UUID roomId, BedRequest request) {
    requireStructure(organizationId, "CREATE");
    Room parent = roomEntity(organizationId, roomId);
    if (beds.existsByRoomIdAndBedNumber(roomId, request.bedNumber()))
      conflict("BED_NUMBER_ALREADY_EXISTS", "error.bed.number.exists");
    return bed(beds.saveAndFlush(parent.addBed(request.bedNumber(), request.status())));
  }

  BedResponse updateBed(UUID organizationId, UUID roomId, UUID id, BedUpdateRequest request) {
    requireStructure(organizationId, "UPDATE");
    roomEntity(organizationId, roomId);
    Bed item = beds.findByIdAndRoomId(id, roomId).orElseThrow(() -> notFound("Bed", id));
    item.update(request.status());
    return bed(item);
  }

  void deleteBed(UUID organizationId, UUID roomId, UUID id) {
    requireStructure(organizationId, "DELETE");
    Room parent = roomEntity(organizationId, roomId);
    Bed item = beds.findByIdAndRoomId(id, roomId).orElseThrow(() -> notFound("Bed", id));
    parent.removeBed(item);
  }

  private UUID viewerOrganization() {
    var user = users.current();
    if (user.has("PLATFORM_ADMIN")) return null;
    if (user.organizationId() == null)
      throw new org.springframework.security.access.AccessDeniedException(
          "Organization context required for gestionnaire");
    return user.organizationId();
  }

  private void validateAdministrator(UUID person) {
    if (person != null
        && !Boolean.TRUE.equals(
            jdbc.queryForObject(
                "select exists(select 1 from identity.person p where id=? and status='ACTIVE' and"
                    + " (keycloak_user_id is not null or exists(select 1 from"
                    + " identity.staff_account_outbox a where a.person_id=p.id)))",
                Boolean.class,
                person))) throw notFound("Person", person);
  }

  private void requireAdministrator(UUID organization) {
    var user = users.current();
    if (user.has("PLATFORM_ADMIN")) return;
    if (!user.has("HOSPITAL_ADMIN")
        || user.personId() == null
        || !organization.equals(user.organizationId()))
      throw new org.springframework.security.access.AccessDeniedException(
          "Organization administrator required");
    boolean allowed =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "select exists(select 1 from organization.organization where id=? and"
                    + " administrator_person_id=?) or exists(select 1 from licensing.member where"
                    + " organization_id=? and person_id=? and role='HOSPITAL_ADMIN' and"
                    + " status='ACTIVE')",
                Boolean.class,
                organization,
                user.personId(),
                organization,
                user.personId()));
    if (!allowed)
      throw new org.springframework.security.access.AccessDeniedException(
          "Organization administrator required");
  }

  private Organization organization(UUID id) {
    return organizations.findById(id).orElseThrow(() -> notFound("Organization", id));
  }

  private Department departmentEntity(UUID orgId, UUID id) {
    return departments
        .findByIdAndOrganizationId(id, orgId)
        .orElseThrow(() -> notFound("Department", id));
  }

  private Room roomEntity(UUID orgId, UUID id) {
    return rooms.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> notFound("Room", id));
  }

  private ResourceNotFoundException notFound(String resource, Object id) {
    return new ResourceNotFoundException(resource, id);
  }

  private void conflict(String code, String key) {
    throw new ConflictException(code, key);
  }

  private OrganizationSummary summary(Organization o) {
    return new OrganizationSummary(
        o.getId(), o.getNumber(), o.getName(), o.getLegalName(), o.getStatus());
  }

  private boolean canReadStructure(UUID organizationId) {
    if (users.current().has("PLATFORM_ADMIN")) return false;
    try {
      permissions.require(organizationId, "orgconfiguration", "READ");
      return true;
    } catch (org.springframework.security.access.AccessDeniedException denied) {
      return false;
    }
  }

  private OrganizationResponse response(Organization o) {
    boolean structure = canReadStructure(o.getId());
    return new OrganizationResponse(
        o.getId(),
        o.getNumber(),
        o.getName(),
        o.getLegalName(),
        o.getOrganizationTypeId(),
        o.getPhone(),
        o.getEmail(),
        o.getWebsite(),
        o.getStatus(),
        o.getCreatedAt(),
        o.getUpdatedAt(),
        (!structure ? java.util.List.<Department>of() : o.getDepartments())
            .stream()
                .sorted(Comparator.comparing(Department::getCode))
                .map(this::department)
                .toList(),
        (!structure ? java.util.List.<Room>of() : o.getRooms())
            .stream().sorted(Comparator.comparing(Room::getRoomNumber)).map(this::room).toList(),
        o.getTimezone(),
        o.getAdministratorPersonId());
  }

  private DepartmentResponse department(Department d) {
    return new DepartmentResponse(
        d.getId(),
        d.getCode(),
        d.getName(),
        d.getDescription(),
        d.getStatus(),
        d.getServices().stream()
            .sorted(Comparator.comparing(CareService::getCode))
            .map(this::service)
            .toList());
  }

  private ServiceResponse service(CareService s) {
    return new ServiceResponse(
        s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getStatus());
  }

  private RoomResponse room(Room r) {
    return new RoomResponse(
        r.getId(),
        r.getDepartment() == null ? null : r.getDepartment().getId(),
        r.getRoomNumber(),
        r.getType(),
        r.getStatus(),
        r.getBeds().stream()
            .sorted(Comparator.comparing(Bed::getBedNumber))
            .map(this::bed)
            .toList());
  }

  private BedResponse bed(Bed b) {
    return new BedResponse(b.getId(), b.getBedNumber(), b.getStatus());
  }
}
