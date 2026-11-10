package com.verdeamano.contracts

import kotlinx.serialization.Serializable

@Serializable enum class PaymentStatus { PENDING, APPROVED, REJECTED, EXPIRED, COMMUNICATION_ERROR, UNKNOWN, RECONCILIATION_REQUIRED, CANCELLED, REFUNDED }
@Serializable data class PaymentCheckout(val orderId:String,val reference:String,val amountCop:Long,val status:PaymentStatus,val expiresAt:String,val checkoutUrl:String?=null,val transactionId:String?=null,val message:String="")
@Serializable data class PaymentQuery(val transactionId:String)
@Serializable enum class ClaimKind { RETRACTO, DEVOLUCION, REVERSION, PQR, GARANTIA }
@Serializable enum class CaseStatus { RECEIVED, IN_REVIEW, RESOLVED, CLOSED }
@Serializable data class ClaimInput(val orderId:String,val kind:ClaimKind,val reason:String,val description:String,val requestId:String)
@Serializable data class ClaimUpdate(val status:CaseStatus,val note:String,val version:Int,val assignedId:String?=null,val responseDueAt:String?=null)
@Serializable data class ClaimHistory(val id:String,val status:CaseStatus,val note:String,val createdAt:String)
@Serializable data class ClaimCase(val id:String,val orderId:String,val reference:String,val kind:ClaimKind,val reason:String,val description:String,
 val status:CaseStatus,val version:Int,val assignedId:String?,val createdAt:String,val updatedAt:String,val responseDueAt:String?,val policy:String,
 val history:List<ClaimHistory>,val attachments:List<String>,val assignedName:String?=null)
@Serializable data class NotificationDeliveryState(val channel:String,val status:String,val attempts:Int)
@Serializable data class NotificationItem(val id:String,val event:String,val resourceType:String,val resourceId:String,val createdAt:String,val read:Boolean,
 val delivery:List<NotificationDeliveryState> = emptyList(),val receivedOnDeviceAt:String?=null)
@Serializable data class PushRegistration(val token:String)
@Serializable data class RefundAuthorization(val caseVersion:Int,val note:String)
@Serializable data class SandboxRefund(val id:String,val claimId:String,val orderId:String,val amountCop:Long,val status:String,val reference:String,val providerId:String?)

@Serializable
enum class PolicyLifecycleStatus {
    DRAFT,
    PROPOSED_PENDING_APPROVAL,
    ACTIVE,
    DEPRECATED
}

@Serializable
data class CommercialPolicyDefinition(
    val policyId: String,
    val kind: ClaimKind,
    val version: String,
    val title: String,
    val status: PolicyLifecycleStatus,
    val legalBasis: String,
    val termsSummary: String,
    val eligibilityConditions: List<String>,
    val mandatoryEvidence: List<String>,
    val exerciseTermDescription: String,
    val executionTermDescription: String,
    val rejectionCauses: List<String>
)


