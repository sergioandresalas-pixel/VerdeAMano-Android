package com.verdeamano.contracts

import kotlinx.serialization.Serializable

@Serializable data class UserSummary(val user: User, val createdAt: String, val updatedAt: String, val version: Int)
@Serializable data class UserPage(val items: List<UserSummary>, val total: Int, val page: Int, val pageSize: Int)
@Serializable data class ProfileDetails(val user: User, val phone: String?, val createdAt: String, val updatedAt: String, val version: Int)
@Serializable data class ProfileUpdate(val name: String, val phone: String?, val version: Int, val requestId: String)
@Serializable data class AccessUpdate(val role: Role, val active: Boolean, val version: Int, val requestId: String)
@Serializable data class AdministrativeAudit(val id: String, val actorId: String, val actorName: String, val resourceId: String,
    val action: String, val before: String, val after: String, val result: String, val createdAt: String)
@Serializable data class AuditPage(val items: List<AdministrativeAudit>, val total: Int, val page: Int, val pageSize: Int)
@Serializable data class UserDetails(val profile: ProfileDetails, val providers: List<String>, val emailVerified: Boolean,
    val protectedAccount: Boolean, val history: List<AdministrativeAudit>)
@Serializable data class UserCreation(val name: String, val email: String, val role: Role, val requestId: String)
@Serializable data class UserCreationResult(val user: UserSummary, val invitationStatus: String, val invitation:SellerInvitation?=null)
@Serializable data class SellerInvitation(val id:String,val email:String,val status:String,val expiresAt:String?,val consumedAt:String?=null,val revokedAt:String?=null)
@Serializable data class InvitationAcceptance(val token:String){override fun toString()="InvitationAcceptance(token=[REDACTED])"}
@Serializable data class AddressInput(val recipient: String, val phone: String, val department: String, val city: String,
    val line: String, val neighborhood: String = "", val complement: String = "", val instructions: String = "",
    val latitude: Double? = null, val longitude: Double? = null, val isDefault: Boolean = false,
    val version: Int? = null, val requestId: String)
@Serializable data class MutationInput(val version: Int, val requestId: String)
@Serializable data class Preferences(val emailNotifications: Boolean = true, val promotions: Boolean = false,
    val version: Int = 1, val updatedAt: String = "")
@Serializable data class PreferencesUpdate(val emailNotifications: Boolean, val promotions: Boolean, val version: Int, val requestId: String)
@Serializable data class ConsentRecord(val id:String,val purpose:String,val accepted:Boolean,val policyVersion:String,val createdAt:String)
@Serializable enum class PrivacyRequestType { ACCESS, EXPORT, DELETION, CORRECTION }
@Serializable data class PrivacyRequestInput(val type: PrivacyRequestType, val note: String = "", val requestId: String)
@Serializable data class PrivacyRequest(val id: String, val type: PrivacyRequestType, val note: String, val status: String, val createdAt: String,
    val version:Int=1,val response:String="",val assignedId:String?=null,val assignedName:String?=null,val policyNote:String="",val history:List<PrivacyHistory> = emptyList(),val ownerName:String?=null)
@Serializable data class PersonalDataExport(val profile: ProfileDetails, val addresses: List<Address>, val preferences: Preferences,
    val requests: List<PrivacyRequest>, val exportedAt: String,val consents:List<ConsentRecord> = emptyList())

object AccountValidation {
    fun phone(value: String?) = value.isNullOrBlank() || Regex("^\\+?[0-9]{7,15}$").matches(value)
    fun profile(name: String, phone: String?): String? = when {
        name.trim().length !in 2..120 -> "Indica un nombre de 2 a 120 caracteres."
        !phone(phone) -> "El teléfono debe contener de 7 a 15 dígitos, con + opcional."
        else -> null
    }
    fun address(input: AddressInput): String? = when {
        input.recipient.trim().length !in 2..120 -> "Indica el destinatario."
        input.phone.isBlank() || !phone(input.phone) -> "Indica un teléfono válido."
        input.department.trim().length !in 2..100 -> "Indica el departamento."
        input.city.trim().length !in 2..120 -> "Indica el municipio o ciudad."
        input.line.trim().length !in 5..254 -> "Indica una dirección de 5 a 254 caracteres."
        input.neighborhood.length > 120 || input.complement.length > 254 || input.instructions.length > 1000 -> "Revisa la longitud de barrio, complemento y referencias."
        (input.latitude == null) != (input.longitude == null) -> "Indica ambas coordenadas o deja ambas vacías."
        input.latitude != null && (!input.latitude.isFinite() || input.latitude !in -90.0..90.0) -> "Latitud inválida."
        input.longitude != null && (!input.longitude.isFinite() || input.longitude !in -180.0..180.0) -> "Longitud inválida."
        else -> null
    }
}
