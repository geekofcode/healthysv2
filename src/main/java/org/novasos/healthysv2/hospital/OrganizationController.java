package org.novasos.healthysv2.hospital;

import java.net.URI;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.novasos.healthysv2.hospital.api.OrganizationDtos.*;
import org.novasos.healthysv2.shared.api.ApiPaths;
import org.novasos.healthysv2.shared.api.dto.PageResponse;

@RestController
@RequestMapping(ApiPaths.V1 + "/organizations")
@Tag(name="Organizations", description="Organizations, departments, services, rooms and beds")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','PROFESSIONAL')")
class OrganizationController {
    private static final String WRITE = "hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN')";
    private final OrganizationService service;
    OrganizationController(OrganizationService service) { this.service = service; }
    @GetMapping @Operation(summary="List organizations") PageResponse<OrganizationSummary> list(Pageable pageable) { return service.findAll(pageable); }
    @GetMapping("/{id}") @Operation(summary="Get an organization and its structure") OrganizationResponse get(@PathVariable UUID id) { return service.find(id); }
    @PostMapping @PreAuthorize(WRITE) @Operation(summary="Create an organization") ResponseEntity<OrganizationResponse> create(@Valid @RequestBody OrganizationRequest request) { var result=service.create(request); return ResponseEntity.created(URI.create(ApiPaths.V1+"/organizations/"+result.id())).body(result); }
    @PutMapping("/{id}") @PreAuthorize(WRITE) @Operation(summary="Update an organization") OrganizationResponse update(@PathVariable UUID id,@Valid @RequestBody OrganizationUpdateRequest request){return service.update(id,request);}
    @DeleteMapping("/{id}") @PreAuthorize(WRITE) @Operation(summary="Delete an organization") ResponseEntity<Void> delete(@PathVariable UUID id){service.delete(id);return ResponseEntity.noContent().build();}
    @PostMapping("/{organizationId}/departments") @PreAuthorize(WRITE) @Operation(summary="Create a department") ResponseEntity<DepartmentResponse> addDepartment(@PathVariable UUID organizationId,@Valid @RequestBody DepartmentRequest request){return ResponseEntity.status(201).body(service.addDepartment(organizationId,request));}
    @PutMapping("/{organizationId}/departments/{id}") @PreAuthorize(WRITE) @Operation(summary="Update a department") DepartmentResponse updateDepartment(@PathVariable UUID organizationId,@PathVariable UUID id,@Valid @RequestBody DepartmentUpdateRequest request){return service.updateDepartment(organizationId,id,request);}
    @DeleteMapping("/{organizationId}/departments/{id}") @PreAuthorize(WRITE) @Operation(summary="Delete a department") ResponseEntity<Void> deleteDepartment(@PathVariable UUID organizationId,@PathVariable UUID id){service.deleteDepartment(organizationId,id);return ResponseEntity.noContent().build();}
    @PostMapping("/{organizationId}/departments/{departmentId}/services") @PreAuthorize(WRITE) @Operation(summary="Create a service") ResponseEntity<ServiceResponse> addService(@PathVariable UUID organizationId,@PathVariable UUID departmentId,@Valid @RequestBody ServiceRequest request){return ResponseEntity.status(201).body(service.addService(organizationId,departmentId,request));}
    @PutMapping("/{organizationId}/departments/{departmentId}/services/{id}") @PreAuthorize(WRITE) @Operation(summary="Update a service") ServiceResponse updateService(@PathVariable UUID organizationId,@PathVariable UUID departmentId,@PathVariable UUID id,@Valid @RequestBody ServiceUpdateRequest request){return service.updateService(organizationId,departmentId,id,request);}
    @DeleteMapping("/{organizationId}/departments/{departmentId}/services/{id}") @PreAuthorize(WRITE) @Operation(summary="Delete a service") ResponseEntity<Void> deleteService(@PathVariable UUID organizationId,@PathVariable UUID departmentId,@PathVariable UUID id){service.deleteService(organizationId,departmentId,id);return ResponseEntity.noContent().build();}
    @PostMapping("/{organizationId}/rooms") @PreAuthorize(WRITE) @Operation(summary="Create a room") ResponseEntity<RoomResponse> addRoom(@PathVariable UUID organizationId,@Valid @RequestBody RoomRequest request){return ResponseEntity.status(201).body(service.addRoom(organizationId,request));}
    @PutMapping("/{organizationId}/rooms/{id}") @PreAuthorize(WRITE) @Operation(summary="Update a room") RoomResponse updateRoom(@PathVariable UUID organizationId,@PathVariable UUID id,@Valid @RequestBody RoomUpdateRequest request){return service.updateRoom(organizationId,id,request);}
    @DeleteMapping("/{organizationId}/rooms/{id}") @PreAuthorize(WRITE) @Operation(summary="Delete a room") ResponseEntity<Void> deleteRoom(@PathVariable UUID organizationId,@PathVariable UUID id){service.deleteRoom(organizationId,id);return ResponseEntity.noContent().build();}
    @PostMapping("/{organizationId}/rooms/{roomId}/beds") @PreAuthorize(WRITE) @Operation(summary="Create a bed") ResponseEntity<BedResponse> addBed(@PathVariable UUID organizationId,@PathVariable UUID roomId,@Valid @RequestBody BedRequest request){return ResponseEntity.status(201).body(service.addBed(organizationId,roomId,request));}
    @PutMapping("/{organizationId}/rooms/{roomId}/beds/{id}") @PreAuthorize(WRITE) @Operation(summary="Update a bed") BedResponse updateBed(@PathVariable UUID organizationId,@PathVariable UUID roomId,@PathVariable UUID id,@Valid @RequestBody BedUpdateRequest request){return service.updateBed(organizationId,roomId,id,request);}
    @DeleteMapping("/{organizationId}/rooms/{roomId}/beds/{id}") @PreAuthorize(WRITE) @Operation(summary="Delete a bed") ResponseEntity<Void> deleteBed(@PathVariable UUID organizationId,@PathVariable UUID roomId,@PathVariable UUID id){service.deleteBed(organizationId,roomId,id);return ResponseEntity.noContent().build();}
}
