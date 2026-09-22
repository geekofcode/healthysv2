package org.novasos.healthysv2.document;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="document",schema="document")
class HealthDocument {
 @Id private UUID id;
 @Column(name="document_number",nullable=false,unique=true) private String number;
 @Column(name="owner_patient_id") private UUID patientId;
 @Column(name="category_id") private UUID categoryId;
 @Column(name="file_name",nullable=false) private String fileName;
 @Column(name="storage_key",nullable=false,unique=true) private String storageKey;
 @Column(name="storage_provider",nullable=false) private String storageProvider;
 @Column(name="mime_type") private String mimeType;
 @Column(name="size_bytes") private Long sizeBytes;
 private String checksum;
 @Column(name="uploaded_by") private UUID uploadedBy;
 @Column(name="uploaded_at",nullable=false) private Instant uploadedAt;
 @Column(nullable=false) private String status;
 protected HealthDocument(){}
 static HealthDocument create(UUID patientId,UUID categoryId,String fileName,String key,String mimeType,long size,String checksum,UUID uploadedBy){var d=new HealthDocument();d.id=UUID.randomUUID();d.number="DOC-"+UUID.randomUUID().toString().substring(0,12).toUpperCase();d.patientId=patientId;d.categoryId=categoryId;d.fileName=fileName;d.storageKey=key;d.storageProvider="MINIO";d.mimeType=mimeType;d.sizeBytes=size;d.checksum=checksum;d.uploadedBy=uploadedBy;d.uploadedAt=Instant.now();d.status="ACTIVE";return d;}
 void archive(){status="ARCHIVED";}
 UUID id(){return id;}String number(){return number;}UUID patientId(){return patientId;}UUID categoryId(){return categoryId;}String fileName(){return fileName;}String storageKey(){return storageKey;}String mimeType(){return mimeType;}Long sizeBytes(){return sizeBytes;}String checksum(){return checksum;}UUID uploadedBy(){return uploadedBy;}Instant uploadedAt(){return uploadedAt;}String status(){return status;}
}
