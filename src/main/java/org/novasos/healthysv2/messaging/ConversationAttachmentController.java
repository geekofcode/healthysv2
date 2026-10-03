package org.novasos.healthysv2.messaging;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.novasos.healthysv2.document.ConversationDocumentFacade;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/attachments")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','DOCTOR','NURSE','PHARMACIST','LAB_TECHNICIAN','PATIENT')")
class ConversationAttachmentController {
    private final ConversationService conversations;
    private final ConversationDocumentFacade documents;
    ConversationAttachmentController(ConversationService conversations,ConversationDocumentFacade documents){this.conversations=conversations;this.documents=documents;}

    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ConversationDocumentFacade.Attachment> upload(@PathVariable UUID conversationId,@RequestPart("file")MultipartFile file){
        var attachment=documents.upload(conversationId,conversations.requireMember(conversationId),file);
        return ResponseEntity.created(URI.create("/api/v1/conversations/"+conversationId+"/attachments/"+attachment.id())).body(attachment);
    }
    @GetMapping("/{id}")
    ConversationDocumentFacade.Attachment detail(@PathVariable UUID conversationId,@PathVariable UUID id){return documents.detail(conversationId,conversations.requireMember(conversationId),id);}
    @GetMapping("/{id}/content")
    ResponseEntity<InputStreamResource> content(@PathVariable UUID conversationId,@PathVariable UUID id){
        var d=documents.download(conversationId,conversations.requireMember(conversationId),id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.contentType())).contentLength(d.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.fileName().replaceAll("[\\r\\n\\\\/]","_"),StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.VARY,"Authorization").header("X-Content-Type-Options","nosniff")
                .body(new InputStreamResource(d.content()));
    }
}
