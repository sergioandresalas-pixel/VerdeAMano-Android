package com.verdeamano.contracts

import kotlinx.serialization.Serializable

@Serializable enum class PrivacyStatus { OPEN, IN_REVIEW, RESOLVED, CLOSED }
@Serializable data class PrivacyHistory(val id:String,val status:String,val response:String,val createdAt:String,val assignedName:String?)
@Serializable data class PrivacyReview(val status:PrivacyStatus,val response:String,val policyNote:String,val assignedId:String?,val version:Int,val requestId:String)
@Serializable data class PrivacyRequestPage(val items:List<PrivacyRequest>,val total:Int,val page:Int,val pageSize:Int)

@Serializable
enum class DataCategory {
    AUTH_CREDENTIALS,
    PROFILE_DATA,
    POSTAL_ADDRESSES,
    COMMERCIAL_ORDERS,
    PAYMENT_TRANSACTIONS,
    CLAIMS_DISPUTES,
    AUDIT_TRAILS,
    STORAGE_MEDIA
}

@Serializable
enum class EvaluationStatus {
    CHECKED_WITH_RECORDS,
    CHECKED_NO_RECORDS,
    NOT_EVALUATED,
    EXTERNAL_VERIFICATION_REQUIRED
}

@Serializable
enum class RetentionObligationStatus {
    MANDATORY_LEGAL_GROUND_IDENTIFIED,
    NO_MANDATORY_RETENTION_FOUND,
    PENDING_LEGAL_REVIEW
}

@Serializable
enum class RetentionEvaluationVerdict {
    REQUIRES_REVISION,
    RETENCION_PARCIAL,
    SIN_BLOQUEOS_IDENTIFICADOS
}

@Serializable
data class CategoryRetentionAssessment(
    val category: DataCategory,
    val status: EvaluationStatus,
    val recordCount: Int? = null,
    val obligationStatus: RetentionObligationStatus,
    val legalGround: String,
    val observation: String
)

@Serializable
data class UserRetentionEvaluation(
    val userId: String,
    val activeOrdersCount: Int,
    val completedOrdersCount: Int,
    val openClaimsCount: Int,
    val activeCartItemsCount: Int,
    val verdict: RetentionEvaluationVerdict,
    val summaryJustification: String,
    val categories: List<CategoryRetentionAssessment>,
    val cancelledOrdersCount: Int = 0,
    val sellerOrdersCount: Int = 0
)
