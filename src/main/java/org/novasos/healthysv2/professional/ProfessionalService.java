package org.novasos.healthysv2.professional;

import static org.novasos.healthysv2.professional.api.ProfessionalDtos.*;
import java.util.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;

@Service @Transactional
class ProfessionalService {
 private final ProfessionalRepository professionals; private final ProfessionalLicenseRepository licenses; private final ProfessionalSpecialityRepository specialities; private final ProfessionalAssignmentRepository assignments; private final ProfessionalScheduleRepository schedules; private final ProfessionalAvailabilityRepository availabilities; private final JdbcTemplate jdbc;
 ProfessionalService(ProfessionalRepository p,ProfessionalLicenseRepository l,ProfessionalSpecialityRepository s,ProfessionalAssignmentRepository a,ProfessionalScheduleRepository sc,ProfessionalAvailabilityRepository av,JdbcTemplate jdbc){professionals=p;licenses=l;specialities=s;assignments=a;schedules=sc;availabilities=av;this.jdbc=jdbc;}

 ProfessionalResponse create(ProfessionalRequest r){requireExists("identity.person",r.personId(),"Person");if(professionals.existsByPersonId(r.personId()))conflict("PERSON_ALREADY_PROFESSIONAL","error.professional.person.exists");if(professionals.existsByNumber(r.professionalNumber()))conflict("PROFESSIONAL_NUMBER_ALREADY_EXISTS","error.professional.number.exists");return response(professionals.save(Professional.create(r.personId(),r.professionalNumber(),r.professionalType(),r.status())));}
 @Transactional(readOnly=true) PageResponse<ProfessionalSummary> findAll(String query,Pageable pageable){return PageResponse.from(professionals.search(query==null?"":query.trim(),pageable).map(this::summary));}
 @Transactional(readOnly=true) ProfessionalResponse find(UUID id){return response(professional(id));}
 ProfessionalResponse update(UUID id,ProfessionalUpdateRequest r){var p=professional(id);p.update(r.professionalType(),r.status());return response(p);}
 void delete(UUID id){professionals.delete(professional(id));}

 LicenseResponse addLicense(UUID professionalId,LicenseRequest r){var p=professional(professionalId);if(licenses.existsByNumberAndAuthority(r.licenseNumber(),r.issuingAuthority()))conflict("PROFESSIONAL_LICENSE_ALREADY_EXISTS","error.professional.license.exists");return license(licenses.saveAndFlush(p.addLicense(r.licenseNumber(),r.issuingAuthority(),r.countryId(),r.issuedAt(),r.expiresAt(),r.status())));}
 LicenseResponse updateLicense(UUID professionalId,UUID id,LicenseRequest r){var x=licenseEntity(professionalId,id);x.update(r.licenseNumber(),r.issuingAuthority(),r.countryId(),r.issuedAt(),r.expiresAt(),r.status());return license(x);}
 void deleteLicense(UUID professionalId,UUID id){professional(professionalId).removeLicense(licenseEntity(professionalId,id));}

 SpecialityResponse addSpeciality(UUID professionalId,SpecialityRequest r){var p=professional(professionalId);specialityInfo(r.specialityCatalogId());if(specialities.findByProfessionalIdAndSpecialityCatalogId(professionalId,r.specialityCatalogId()).isPresent())conflict("PROFESSIONAL_SPECIALITY_ALREADY_EXISTS","error.professional.speciality.exists");return speciality(specialities.saveAndFlush(p.addSpeciality(r.specialityCatalogId(),r.primary())));}
 void deleteSpeciality(UUID professionalId,UUID catalogueId){professional(professionalId).removeSpeciality(specialities.findByProfessionalIdAndSpecialityCatalogId(professionalId,catalogueId).orElseThrow(()->notFound("ProfessionalSpeciality",catalogueId)));}
 @Transactional(readOnly=true) List<SpecialityCatalogResponse> specialityCatalog(){return jdbc.query("select id,code,name from catalog.speciality_catalog where active=true order by name",(rs,n)->new SpecialityCatalogResponse(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name")));}

 AssignmentResponse addAssignment(UUID professionalId,AssignmentRequest r){validateAssignment(r);var p=professional(professionalId);return assignment(assignments.saveAndFlush(p.addAssignment(r.organizationId(),r.departmentId(),r.serviceId(),r.position(),r.employeeNumber(),r.startDate(),r.endDate(),r.status())));}
 AssignmentResponse updateAssignment(UUID professionalId,UUID id,AssignmentRequest r){validateAssignment(r);var x=assignmentEntity(professionalId,id);x.update(r.organizationId(),r.departmentId(),r.serviceId(),r.position(),r.employeeNumber(),r.startDate(),r.endDate(),r.status());return assignment(x);}
 void deleteAssignment(UUID professionalId,UUID id){professional(professionalId).removeAssignment(assignmentEntity(professionalId,id));}

 ScheduleResponse addSchedule(UUID professionalId,UUID assignmentId,ScheduleRequest r){var a=assignmentEntity(professionalId,assignmentId);return schedule(schedules.saveAndFlush(a.addSchedule(r.dayOfWeek(),r.startTime(),r.endTime(),r.slotDurationMinutes())));}
 ScheduleResponse updateSchedule(UUID professionalId,UUID assignmentId,UUID id,ScheduleRequest r){assignmentEntity(professionalId,assignmentId);var x=schedules.findByIdAndAssignmentId(id,assignmentId).orElseThrow(()->notFound("ProfessionalSchedule",id));x.update(r.dayOfWeek(),r.startTime(),r.endTime(),r.slotDurationMinutes());return schedule(x);}
 void deleteSchedule(UUID professionalId,UUID assignmentId,UUID id){var a=assignmentEntity(professionalId,assignmentId);var x=schedules.findByIdAndAssignmentId(id,assignmentId).orElseThrow(()->notFound("ProfessionalSchedule",id));a.removeSchedule(x);}

 AvailabilityResponse addAvailability(UUID professionalId,UUID assignmentId,AvailabilityRequest r){var a=assignmentEntity(professionalId,assignmentId);return availability(availabilities.saveAndFlush(a.addAvailability(r.startAt(),r.endAt(),r.availabilityType(),r.status())));}
 AvailabilityResponse updateAvailability(UUID professionalId,UUID assignmentId,UUID id,AvailabilityRequest r){assignmentEntity(professionalId,assignmentId);var x=availabilities.findByIdAndAssignmentId(id,assignmentId).orElseThrow(()->notFound("ProfessionalAvailability",id));x.update(r.startAt(),r.endAt(),r.availabilityType(),r.status());return availability(x);}
 void deleteAvailability(UUID professionalId,UUID assignmentId,UUID id){var a=assignmentEntity(professionalId,assignmentId);var x=availabilities.findByIdAndAssignmentId(id,assignmentId).orElseThrow(()->notFound("ProfessionalAvailability",id));a.removeAvailability(x);}

 private void validateAssignment(AssignmentRequest r){requireExists("organization.organization",r.organizationId(),"Organization");if(r.departmentId()!=null&&!exists("select count(*) from organization.department where id=? and organization_id=?",r.departmentId(),r.organizationId()))throw notFound("Department",r.departmentId());if(r.serviceId()!=null&&!exists("select count(*) from organization.service where id=? and department_id=?",r.serviceId(),r.departmentId()))throw notFound("Service",r.serviceId());}
 private void requireExists(String table,UUID id,String resource){if(!exists("select count(*) from "+table+" where id=?",id))throw notFound(resource,id);}
 private boolean exists(String sql,Object...args){return Boolean.TRUE.equals(jdbc.queryForObject(sql,Integer.class,args)>0);}
 private String[] specialityInfo(UUID id){try{return jdbc.queryForObject("select code,name from catalog.speciality_catalog where id=? and active=true",(rs,n)->new String[]{rs.getString(1),rs.getString(2)},id);}catch(EmptyResultDataAccessException e){throw notFound("Speciality",id);}}
 private Professional professional(UUID id){return professionals.findById(id).orElseThrow(()->notFound("Professional",id));} private ProfessionalLicense licenseEntity(UUID p,UUID id){return licenses.findByIdAndProfessionalId(id,p).orElseThrow(()->notFound("ProfessionalLicense",id));} private ProfessionalAssignment assignmentEntity(UUID p,UUID id){return assignments.findByIdAndProfessionalId(id,p).orElseThrow(()->notFound("ProfessionalAssignment",id));}
 private ResourceNotFoundException notFound(String r,Object id){return new ResourceNotFoundException(r,id);} private void conflict(String c,String k){throw new ConflictException(c,k);}
 private ProfessionalSummary summary(Professional p){return new ProfessionalSummary(p.getId(),p.getPersonId(),p.getNumber(),p.getType(),p.getStatus());}
 private ProfessionalResponse response(Professional p){return new ProfessionalResponse(p.getId(),p.getPersonId(),p.getNumber(),p.getType(),p.getStatus(),p.getCreatedAt(),p.getUpdatedAt(),p.getLicenses().stream().map(this::license).toList(),p.getSpecialities().stream().map(this::speciality).toList(),p.getAssignments().stream().map(this::assignment).toList());}
 private LicenseResponse license(ProfessionalLicense x){return new LicenseResponse(x.getId(),x.getNumber(),x.getAuthority(),x.getCountryId(),x.getIssuedAt(),x.getExpiresAt(),x.getStatus());}
 private SpecialityResponse speciality(ProfessionalSpeciality x){var i=specialityInfo(x.getSpecialityCatalogId());return new SpecialityResponse(x.getSpecialityCatalogId(),i[0],i[1],x.isPrimary());}
 private AssignmentResponse assignment(ProfessionalAssignment x){return new AssignmentResponse(x.getId(),x.getOrganizationId(),x.getDepartmentId(),x.getServiceId(),x.getPosition(),x.getEmployeeNumber(),x.getStartDate(),x.getEndDate(),x.getStatus(),x.getSchedules().stream().map(this::schedule).toList(),x.getAvailabilities().stream().map(this::availability).toList());}
 private ScheduleResponse schedule(ProfessionalSchedule x){return new ScheduleResponse(x.getId(),x.getDayOfWeek(),x.getStartTime(),x.getEndTime(),x.getSlotDurationMinutes());}
 private AvailabilityResponse availability(ProfessionalAvailability x){return new AvailabilityResponse(x.getId(),x.getStartAt(),x.getEndAt(),x.getType(),x.getStatus());}
}
