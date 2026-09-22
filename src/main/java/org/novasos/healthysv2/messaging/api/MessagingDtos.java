package org.novasos.healthysv2.messaging.api;
import java.time.Instant;import java.util.*;import jakarta.validation.constraints.*;
public final class MessagingDtos{private MessagingDtos(){}
 public record CreateConversationRequest(@NotBlank String type,@Size(max=500)String subject,UUID patientId,@NotEmpty Set<UUID> participantPersonIds){}
 public record AddParticipantRequest(@NotNull UUID personId,@Size(max=50)String role){}
 public record SendMessageRequest(@NotBlank String type,@Size(max=10000)String content,UUID replyToMessageId,Set<UUID> documentIds){public SendMessageRequest{documentIds=documentIds==null?Set.of():Set.copyOf(documentIds);}}
 public record ParticipantResponse(UUID personId,String displayName,String role,Instant joinedAt,String status){}
 public record ConversationSummary(UUID id,String conversationNumber,String type,String subject,UUID patientId,Instant createdAt,String status,String lastMessage,Instant lastMessageAt,long unreadCount,List<ParticipantResponse>participants){}
 public record MessageResponse(UUID id,UUID conversationId,UUID senderPersonId,String senderName,String type,String content,UUID replyToMessageId,Instant sentAt,Instant editedAt,Instant deletedAt,List<UUID>documentIds,boolean readByCurrentUser){}
 public record ConversationResponse(UUID id,String conversationNumber,String type,String subject,UUID patientId,UUID createdBy,Instant createdAt,String status,List<ParticipantResponse>participants){}
 public record ReadReceiptResponse(UUID messageId,UUID personId,Instant readAt){}
}
