package org.novasos.healthysv2.laboratory;
import static org.novasos.healthysv2.laboratory.api.PatientLaboratoryDtos.*;
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
class PatientLaboratoryService {
 private static final String SELECT="select r.id,r.result_number,r.lab_order_id,o.order_number,o.patient_id,o.laboratory_organization_id,g.name laboratory_name,o.ordered_at,r.performed_at,r.validated_at,r.status from laboratory.lab_result r join laboratory.lab_order o on o.id=r.lab_order_id left join organization.organization g on g.id=o.laboratory_organization_id ";
 private static final String FINAL="r.status='FINAL' and r.validated_at is not null and r.validated_by is not null";
 private final PatientSelfAccess selves; private final JdbcTemplate jdbc; private final AuditTrail audit;
 PatientLaboratoryService(PatientSelfAccess selves,JdbcTemplate jdbc,AuditTrail audit){this.selves=selves;this.jdbc=jdbc;this.audit=audit;}
 PageResponse<ResultSummary> list(UUID subject,int page,int size){
  var self=selves.resolve(subject);checkPage(page,size);
  long total=Objects.requireNonNull(jdbc.queryForObject("select count(*) from laboratory.lab_result r join laboratory.lab_order o on o.id=r.lab_order_id where o.patient_id=? and "+FINAL,Long.class,self.patient()));
  var content=jdbc.query(SELECT+"where o.patient_id=? and "+FINAL+" order by r.validated_at desc,r.id desc limit ? offset ?",this::summary,self.patient(),size,(long)page*size);
  accessed(self,self.patient(),"LIST");return PageResponse.from(new PageImpl<>(content,PageRequest.of(page,size),total));
 }
 ResultDetail detail(UUID subject,UUID id){
  var self=selves.resolve(subject);
  var found=jdbc.query(SELECT+"where r.id=?",this::summary,id);
  if(found.isEmpty())throw new ResourceNotFoundException("LabResult",id);
  var summary=found.getFirst();
  if(!self.patient().equals(summary.patientId())){accessed(self,id,"DENIED");throw new AccessDeniedException("Result belongs to another patient");}
  if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from laboratory.lab_result r where r.id=? and "+FINAL+")",Boolean.class,id)))throw new ResourceNotFoundException("Validated LabResult",id);
  var items=jdbc.query("select i.*,e.code exam_code,e.name exam_name,coalesce(i.parameter,p.name) parameter_name from laboratory.lab_result_item i join laboratory.lab_order_item oi on oi.id=i.lab_order_item_id join catalog.laboratory_exam_catalog e on e.id=oi.lab_exam_catalog_id left join catalog.laboratory_parameter_catalog p on p.id=i.parameter_catalog_id where i.lab_result_id=? and oi.lab_order_id=? order by e.name,i.parameter,i.id",(rs,row)->new ResultItem(rs.getObject("id",UUID.class),rs.getObject("lab_order_item_id",UUID.class),rs.getString("exam_code"),rs.getString("exam_name"),rs.getObject("parameter_catalog_id",UUID.class),rs.getString("parameter_name"),rs.getString("value"),rs.getString("unit"),rs.getBigDecimal("reference_min"),rs.getBigDecimal("reference_max"),rs.getString("interpretation"),rs.getString("abnormal_flag")),id,summary.labOrderId());
  accessed(self,id,"READ");return new ResultDetail(summary,items);
 }
 private ResultSummary summary(ResultSet rs,int row)throws SQLException{return new ResultSummary(rs.getObject("id",UUID.class),rs.getString("result_number"),rs.getObject("lab_order_id",UUID.class),rs.getString("order_number"),rs.getObject("patient_id",UUID.class),rs.getObject("laboratory_organization_id",UUID.class),rs.getString("laboratory_name"),instant(rs,"ordered_at"),instant(rs,"performed_at"),instant(rs,"validated_at"),rs.getString("status"));}
 private Instant instant(ResultSet rs,String column)throws SQLException {var timestamp=rs.getTimestamp(column);return timestamp==null?null:timestamp.toInstant();}
 private void checkPage(int page,int size){if(page<0||size<1||size>100)throw new BusinessRuleException("INVALID_PAGE","error.malformed");}
 private void accessed(PatientSelfAccess.Identity self,UUID id,String action){audit.access(self.person(),self.patient(),null,"LAB_RESULTS",id,action,"PATIENT_SELF",Map.of("allowed",!"DENIED".equals(action)));}
}
