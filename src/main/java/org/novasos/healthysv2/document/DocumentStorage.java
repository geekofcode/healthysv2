package org.novasos.healthysv2.document;
import java.io.*;import org.novasos.healthysv2.ObjectStorageConfiguration.StorageProperties;import org.springframework.stereotype.Component;import software.amazon.awssdk.core.sync.RequestBody;import software.amazon.awssdk.services.s3.S3Client;import software.amazon.awssdk.services.s3.model.*;
@Component class DocumentStorage{
 private final S3Client client;private final StorageProperties properties;DocumentStorage(S3Client client,StorageProperties properties){this.client=client;this.properties=properties;}
 void put(String key,InputStream content,long size,String type){client.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentType(type).build(),RequestBody.fromInputStream(content,size));}
 StoredObject get(String key){var response=client.getObject(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build());return new StoredObject(response,response.response().contentLength(),response.response().contentType());}
 void delete(String key){client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());}
 record StoredObject(InputStream content,long size,String contentType){}
}
