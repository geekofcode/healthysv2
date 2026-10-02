package org.novasos.healthysv2.prescription;
import static org.novasos.healthysv2.prescription.api.PatientPrescriptionDtos.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.audit.AuditTrail;
import org.novasos.healthysv2.patient.PatientSelfAccess;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
class PatientPrescriptionService {
 private static final Set<String> ISSUED=Set.of("ACTIVE","PARTIALLY_DISPENSED","DISPENSED","CANCELLED");
 private static final String SELECT="select r.*,concat_ws(' ',i.first_name,i.last_name) prescriber_name,g.name organization_name from prescription.prescription r join professional.professional p on p.id=r.prescriber_id join identity.person i on i.id=p.person_id left join organization.organization g on g.id=r.organization_id ";
 private static final String VISIBLE="r.status in ('ACTIVE','PARTIALLY_DISPENSED','DISPENSED','CANCELLED')";
 private final PatientSelfAccess selves; private final JdbcTemplate jdbc; private final AuditTrail audit;
 PatientPrescriptionService(PatientSelfAccess selves,JdbcTemplate jdbc,AuditTrail audit){this.selves=selves;this.jdbc=jdbc;this.audit=audit;}
 PageResponse<Summary> list(UUID subject,int page,int size){
  var self=selves.resolve(subject);if(page<0||size<1||size>100)throw new BusinessRuleException("INVALID_PAGE","error.malformed");
  long total=Objects.requireNonNull(jdbc.queryForObject("select count(*) from prescription.prescription r where r.patient_id=? and "+VISIBLE,Long.class,self.patient()));
  var content=jdbc.query(SELECT+"where r.patient_id=? and "+VISIBLE+" order by r.prescribed_at desc,r.id desc limit ? offset ?",this::summary,self.patient(),size,(long)page*size);
  accessed(self,self.patient(),"LIST");return PageResponse.from(new PageImpl<>(content,PageRequest.of(page,size),total));
 }
 Detail detail(UUID subject,UUID id){
  var self=selves.resolve(subject);var found=jdbc.query(SELECT+"where r.id=?",this::summary,id);
  if(found.isEmpty())throw new ResourceNotFoundException("Prescription",id);var summary=found.getFirst();
  if(!self.patient().equals(summary.patientId())){accessed(self,id,"DENIED");throw new AccessDeniedException("Prescription belongs to another patient");}
  if(!ISSUED.contains(summary.status()))throw new ResourceNotFoundException("Issued Prescription",id);
  var items=jdbc.query("select i.*,m.code medication_code,m.name medication_name,m.generic_name,m.form,m.strength from prescription.prescription_item i join catalog.medication_catalog m on m.id=i.medication_catalog_id where i.prescription_id=? order by m.name,i.id",(rs,row)->new Item(rs.getObject("id",UUID.class),rs.getObject("medication_catalog_id",UUID.class),rs.getString("medication_code"),rs.getString("medication_name"),rs.getString("generic_name"),rs.getString("form"),rs.getString("strength"),rs.getString("dosage"),rs.getString("frequency"),rs.getString("route"),rs.getString("duration"),rs.getBigDecimal("quantity"),rs.getBigDecimal("quantity_dispensed"),rs.getBigDecimal("quantity").subtract(rs.getBigDecimal("quantity_dispensed")),rs.getString("instructions")),id);
  var dispensations=jdbc.query("select d.*,g.name pharmacy_name from pharmacy.dispense d join organization.organization g on g.id=d.pharmacy_organization_id where d.prescription_id=? order by d.dispensed_at desc,d.id desc",(rs,row)->dispensation(rs),id);
  accessed(self,id,"READ");return new Detail(summary,items,dispensations);
 }
 private Dispensation dispensation(ResultSet rs)throws SQLException{
  UUID id=rs.getObject("id",UUID.class);
  var items=jdbc.query("select d.id,d.prescription_item_id,d.quantity_dispensed,m.name medication_name from pharmacy.dispense_item d join prescription.prescription_item i on i.id=d.prescription_item_id join catalog.medication_catalog m on m.id=i.medication_catalog_id where d.dispense_id=? and i.prescription_id=? order by m.name,d.id",(line,row)->new DispensationItem(line.getObject("id",UUID.class),line.getObject("prescription_item_id",UUID.class),line.getString("medication_name"),line.getBigDecimal("quantity_dispensed")),id,rs.getObject("prescription_id",UUID.class));
  return new Dispensation(id,rs.getString("dispense_number"),rs.getObject("pharmacy_organization_id",UUID.class),rs.getString("pharmacy_name"),instant(rs,"dispensed_at"),rs.getString("status"),items);
 }
 private Summary summary(ResultSet rs,int row)throws SQLException{
  var expires=instant(rs,"expires_at");var status=rs.getString("status");
  boolean expired=Set.of("ACTIVE","PARTIALLY_DISPENSED").contains(status)&&expires!=null&&!expires.isAfter(Instant.now());
  return new Summary(rs.getObject("id",UUID.class),rs.getString("prescription_number"),rs.getObject("patient_id",UUID.class),rs.getObject("consultation_id",UUID.class),rs.getObject("prescriber_id",UUID.class),rs.getString("prescriber_name"),rs.getObject("organization_id",UUID.class),rs.getString("organization_name"),instant(rs,"prescribed_at"),expires,status,expired);
 }
 private Instant instant(ResultSet rs,String column)throws SQLException {var timestamp=rs.getTimestamp(column);return timestamp==null?null:timestamp.toInstant();}
 private void accessed(PatientSelfAccess.Identity self,UUID id,String action){audit.access(self.person(),self.patient(),null,"PRESCRIPTIONS",id,action,"PATIENT_SELF",Map.of("allowed",!"DENIED".equals(action)));}
}
