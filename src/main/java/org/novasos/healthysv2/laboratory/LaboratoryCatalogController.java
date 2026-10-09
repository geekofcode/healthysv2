package org.novasos.healthysv2.laboratory;

import java.util.UUID;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','DOCTOR','NURSE','LAB_TECHNICIAN')")
class LaboratoryCatalogController {
    private final JdbcTemplate jdbc;
    LaboratoryCatalogController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @GetMapping("/api/v1/lab-exams")
    PageResponse<Exam> exams(@RequestParam(defaultValue="") String query,
            @RequestParam(defaultValue="0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue="20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size) {
        var pageable=PageRequest.of(page,size);
        String term="%"+query.trim()+"%";
        String where=" where active=true and (name ilike ? or code ilike ?)";
        var rows=jdbc.query("select id,code,name,specimen_type from catalog.laboratory_exam_catalog"+where+" order by name,id limit ? offset ?",
                (row,index)->new Exam(row.getObject("id",UUID.class),row.getString("code"),row.getString("name"),row.getString("specimen_type")),term,term,size,pageable.getOffset());
        Long total=jdbc.queryForObject("select count(*) from catalog.laboratory_exam_catalog"+where,Long.class,term,term);
        return PageResponse.from(new PageImpl<>(rows,pageable,total==null?0:total));
    }
    record Exam(UUID id,String code,String name,String specimenType) {}
}
