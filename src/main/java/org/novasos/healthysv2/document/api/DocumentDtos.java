package org.novasos.healthysv2.document.api;
import java.time.Instant;import java.util.UUID;
public final class DocumentDtos{private DocumentDtos(){}
 public record DocumentResponse(UUID id,String documentNumber,UUID patientId,UUID categoryId,String categoryCode,String categoryName,String fileName,String mimeType,long sizeBytes,String checksum,UUID uploadedBy,Instant uploadedAt,String status){}
 public record DocumentCategoryResponse(UUID id,String code,String name){}
}
