package org.novasos.healthysv2.patienthealth;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Service
@Transactional
class PatientPhotoService {
  private final PatientHealthService health;
  private final CurrentUserContext users;
  private final JdbcTemplate db;
  private final S3Client storage;
  private final String bucket;

  PatientPhotoService(
      PatientHealthService health,
      CurrentUserContext users,
      JdbcTemplate db,
      S3Client storage,
      @Value("${healthys.storage.bucket}") String bucket) {
    this.health = health;
    this.users = users;
    this.db = db;
    this.storage = storage;
    this.bucket = bucket;
  }

  @Transactional(readOnly = true)
  byte[] self() {
    return read(health.selectedPatient());
  }

  @Transactional(readOnly = true)
  byte[] clinical(UUID patient) {
    health.clinicalRead(patient);
    return read(patient);
  }

  private byte[] read(UUID patient) {
    var keys =
        db.queryForList(
            "select d.storage_key from patienthealth.photo p join document.document d on"
                + " d.id=p.document_id where p.patient_id=? and d.status='ACTIVE' order by"
                + " p.created_at desc,p.id desc limit 1",
            String.class,
            patient);
    if (keys.isEmpty()) throw new ResourceNotFoundException("PatientPhoto", patient);
    return storage
        .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(keys.getFirst()).build())
        .asByteArray();
  }

  void put(MultipartFile file) {
    UUID patient = health.lockSelectedPatient();
    byte[] bytes = normalize(file);
    UUID document = UUID.randomUUID();
    String key = "patients/" + patient + "/profile-photos/" + document + ".png";
    storage.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType("image/png").build(),
        RequestBody.fromBytes(bytes));
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status != STATUS_COMMITTED)
                try {
                  storage.deleteObject(
                      DeleteObjectRequest.builder().bucket(bucket).key(key).build());
                } catch (RuntimeException ignored) {
                }
            }
          });
    db.update(
        "insert into"
            + " document.document(id,document_number,owner_patient_id,file_name,storage_key,storage_provider,mime_type,size_bytes,checksum,uploaded_by,patient_visible)"
            + " values(?,?,?,'photo-patient.png',?,'MINIO','image/png',?,?,?,true)",
        document,
        "DOC-" + document,
        patient,
        key,
        bytes.length,
        sha(bytes),
        users.current().personId());
    db.update(
        "insert into patienthealth.photo(patient_id,document_id,actor_person_id,created_at)"
            + " values(?,?,?,clock_timestamp())",
        patient,
        document,
        users.current().personId());
  }

  static byte[] normalize(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > 2000000)
      throw new IllegalArgumentException("INVALID_PATIENT_PHOTO");
    try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IllegalArgumentException("INVALID_PATIENT_PHOTO");
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        if (!Set.of("png", "jpeg", "jpg").contains(reader.getFormatName().toLowerCase(Locale.ROOT)))
          throw new IllegalArgumentException("INVALID_PATIENT_PHOTO");
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w < 24 || h < 24 || w > 2048 || h > 2048 || (long) w * h > 3000000)
          throw new IllegalArgumentException("INVALID_PHOTO_DIMENSIONS");
        var raw = reader.read(0);
        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, w, h);
        graphics.drawImage(raw, 0, 0, null);
        graphics.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("INVALID_PATIENT_PHOTO");
    }
  }

  private String sha(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA256_UNAVAILABLE");
    }
  }
}
