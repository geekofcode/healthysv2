package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care/visits")
class VisitIntakeController {
    private final VisitIntakeService service;
    VisitIntakeController(VisitIntakeService service){this.service=service;}
    @GetMapping("/{id}/intake") ReceptionDtos.Intake read(@PathVariable UUID id){return service.read(id);}
    @PutMapping("/{id}/intake") ReceptionDtos.Intake write(@PathVariable UUID id,@Valid @RequestBody ReceptionDtos.Intake request){return service.write(id,request);}
}
