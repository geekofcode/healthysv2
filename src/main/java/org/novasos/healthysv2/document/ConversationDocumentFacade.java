package org.novasos.healthysv2.document;

import java.io.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

/** Conversation attachments stay scoped to the conversation, never published to a patient dossier. */
@Service
@Transactional
public class ConversationDocumentFacade {
    private final DocumentService validation;
    private final HealthDocumentRepository documents;
    private final DocumentStorage storage;
    private final JdbcTemplate jdbc;

    ConversationDocumentFacade(DocumentService validation,HealthDocumentRepository documents,DocumentStorage storage,JdbcTemplate jdbc){
        this.validation=validation;this.documents=documents;this.storage=storage;this.jdbc=jdbc;
    }

    public Attachment upload(UUID conversation,UUID actor,MultipartFile file){
        requireMember(conversation,actor);
        validation.validate(file);
        String name=validation.safeName(file.getOriginalFilename());
        String key="conversations/"+conversation+"/"+UUID.randomUUID()+"/"+name;
        try{
            byte[] bytes=file.getBytes();
            validation.validateContent(name,file.getContentType(),bytes);
            String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            storage.put(key,new ByteArrayInputStream(bytes),bytes.length,file.getContentType());
            if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)delete(key);}});
            var document=documents.saveAndFlush(HealthDocument.create(null,null,name,key,file.getContentType(),bytes.length,checksum,actor));
            jdbc.update("insert into document.document_link(document_id,resource_type,resource_id) values (?,'CONVERSATION',?)",document.id(),conversation);
            audit(actor,document.id(),"UPLOAD_ATTACHMENT");
            return metadata(document);
        }catch(IOException|NoSuchAlgorithmException exception){delete(key);throw new IllegalStateException("Attachment upload failed",exception);}
        catch(RuntimeException exception){delete(key);throw exception;}
    }

    @Transactional(readOnly=true)
    public Attachment detail(UUID conversation,UUID actor,UUID id){return metadata(allowed(conversation,actor,id));}

    public Download download(UUID conversation,UUID actor,UUID id){
        var document=allowed(conversation,actor,id);
        var object=storage.get(document.storageKey());
        if(document.sizeBytes()==null||object.size()!=document.sizeBytes()){
            try{object.content().close();}catch(IOException ignored){}
            throw new BusinessRuleException("DOCUMENT_CONTENT_MISMATCH","error.document.type");
        }
        audit(actor,id,"DOWNLOAD_ATTACHMENT");
        return new Download(object.content(),object.size(),document.mimeType(),document.fileName());
    }

    private HealthDocument allowed(UUID conversation,UUID actor,UUID id){
        requireMember(conversation,actor);
        var document=documents.findById(id).orElseThrow(()->new ResourceNotFoundException("Attachment",id));
        boolean shared=exists("select count(*) from communication.message_attachment a join communication.message m on m.id=a.message_id where a.document_id=? and m.conversation_id=? and m.deleted_at is null",id,conversation);
        boolean draft=actor.equals(document.uploadedBy())&&exists("select count(*) from document.document_link where document_id=? and resource_type='CONVERSATION' and resource_id=?",id,conversation);
        if(!"ACTIVE".equals(document.status())||(!shared&&!draft))throw new AccessDeniedException("ATTACHMENT_ACCESS_DENIED");
        return document;
    }
    private void requireMember(UUID conversation,UUID actor){
        if(actor==null||!exists("select count(*) from communication.conversation_participant cp join communication.conversation c on c.id=cp.conversation_id where cp.conversation_id=? and cp.person_id=? and cp.status='ACTIVE' and cp.left_at is null and c.status='ACTIVE'",conversation,actor))throw new AccessDeniedException("CONVERSATION_ACCESS_DENIED");
    }
    private boolean exists(String sql,Object...args){return Objects.requireNonNull(jdbc.queryForObject(sql,Integer.class,args))>0;}
    private Attachment metadata(HealthDocument d){return new Attachment(d.id(),d.fileName(),d.mimeType(),d.sizeBytes()==null?0:d.sizeBytes(),d.uploadedAt(),d.status());}
    private void audit(UUID actor,UUID id,String action){jdbc.update("insert into audit.audit_log(actor_person_id,module,entity_type,entity_id,action) values (?,'MESSAGING','Document',?,?)",actor,id,action);}
    private void delete(String key){try{storage.delete(key);}catch(RuntimeException ignored){}}
    public record Attachment(UUID id,String fileName,String mimeType,long sizeBytes,Instant uploadedAt,String status){}
    public record Download(InputStream content,long size,String contentType,String fileName){}
}
