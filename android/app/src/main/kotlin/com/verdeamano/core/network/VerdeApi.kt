package com.verdeamano.core.network

import com.verdeamano.contracts.*
import retrofit2.http.*
import okhttp3.MultipartBody

interface VerdeApi {
    @GET("api/privacy/requests/{id}") suspend fun privacyDetail(@Path("id") id:String):PrivacyRequest
    @GET("api/admin/privacy/requests") suspend fun privacyPage(@Query("page") page:Int,@Query("pageSize") size:Int=20,@Query("status") status:PrivacyStatus?=null):PrivacyRequestPage
    @GET("api/admin/privacy/responsibles") suspend fun privacyResponsibles():List<User>
    @PUT("api/admin/privacy/requests/{id}") suspend fun reviewPrivacy(@Path("id") id:String,@Body input:PrivacyReview):PrivacyRequest
    @GET("api/admin/privacy/requests/{id}/retention-evaluation") suspend fun privacyRetentionEvaluation(@Path("id") id:String):UserRetentionEvaluation
    @GET("api/admin/claims/policies") suspend fun adminClaimPolicies():List<CommercialPolicyDefinition>
    @GET("api/claims/{id}/refund") suspend fun refund(@Path("id") id:String):SandboxRefund
    @POST("api/claims/{id}/refund") suspend fun authorizeRefund(@Path("id") id:String,@Body input:RefundAuthorization):SandboxRefund
    @POST("api/claims/{id}/refund/reconcile") suspend fun reconcileRefund(@Path("id") id:String):SandboxRefund
    @GET("api/orders/{id}/payment") suspend fun payment(@Path("id") id:String):PaymentCheckout
    @POST("api/orders/{id}/payment/checkout") suspend fun paymentCheckout(@Path("id") id:String):PaymentCheckout
    @POST("api/orders/{id}/payment/reconcile") suspend fun paymentReconcile(@Path("id") id:String,@Body input:PaymentQuery):PaymentCheckout
    @GET("api/claims") suspend fun claims():List<ClaimCase>
    @POST("api/claims") suspend fun createClaim(@Body input:ClaimInput):ClaimCase
    @GET("api/claims/{id}") suspend fun claim(@Path("id") id:String):ClaimCase
    @GET("api/claims/{id}/responsibles") suspend fun claimResponsibles(@Path("id") id:String):List<User>
    @PUT("api/claims/{id}") suspend fun updateClaim(@Path("id") id:String,@Body input:ClaimUpdate):ClaimCase
    @POST("api/claims/{id}/attachments") suspend fun claimAttachment(@Path("id") id:String,@Query("requestId") request:String,@Body bytes:okhttp3.RequestBody):ClaimCase
    @GET("api/claims/{id}/attachments/{attachment}") suspend fun claimPhoto(@Path("id") id:String,@Path("attachment") attachment:String):okhttp3.ResponseBody
    @GET("api/notifications") suspend fun notifications():List<NotificationItem>
    @POST("api/notifications/{id}/read") suspend fun readNotification(@Path("id") id:String)
    @POST("api/notifications/push") suspend fun registerPush(@Body input:PushRegistration)
    @HTTP(method="DELETE",path="api/notifications/push",hasBody=true) suspend fun unregisterPush(@Body input:PushRegistration)
    @POST("api/checkout/preview") suspend fun checkout(@Body input:CheckoutPreviewInput):CheckoutQuote
    @POST("api/orders") suspend fun confirmOrder(@Body input:OrderConfirmation):Order
    @GET("api/orders") suspend fun orders(@Query("page") page:Int=1,@Query("pageSize") size:Int=20,@Query("status") status:OrderStatus?=null):OrderPage
    @GET("api/orders/{id}") suspend fun order(@Path("id") id:String):Order
    @POST("api/orders/{id}/cancel") suspend fun cancelOrder(@Path("id") id:String,@Body input:OrderCancellation):Order
    @POST("api/orders/{id}/fulfillments/{seller}/transition") suspend fun transitionOrder(@Path("id") id:String,@Path("seller") seller:String,@Body input:OrderTransitionInput):Order
    @GET("api/products") suspend fun catalog(@Query("q") q:String?,@Query("category") category:String?,@Query("page") page:Int,@Query("pageSize") size:Int=20,@Query("minPrice") minimum:Long?=null,@Query("maxPrice") maximum:Long?=null,@Query("available") available:Boolean?=null,@Query("sort") sort:CatalogSort=CatalogSort.NAME,@Query("featured") featured:Boolean?=null):ProductPage
    @GET("api/admin/categories") suspend fun allCategories():List<Category>
    @POST("api/admin/categories") suspend fun createCategory(@Body input:CategoryInput):Category
    @PUT("api/admin/categories/{id}") suspend fun updateCategory(@Path("id") id:String,@Body input:CategoryInput):Category
    @GET("api/seller/products/{id}") suspend fun ownedProduct(@Path("id") id:String):Product
    @GET("api/admin/products/{id}") suspend fun supervisedProduct(@Path("id") id:String):Product
    @GET("api/admin/products") suspend fun supervisedProducts(@Query("q") q:String?,@Query("category") category:String?,@Query("seller") seller:String?,@Query("state") state:PublicationState?,@Query("page") page:Int,@Query("pageSize") size:Int=20):ProductPage
    @PUT("api/{scope}/products/{id}/publication") suspend fun publication(@Path("scope") scope:String,@Path("id") id:String,@Body input:PublicationInput):Product
    @POST("api/{scope}/products/{id}/inventory") suspend fun inventory(@Path("scope") scope:String,@Path("id") id:String,@Body input:InventoryAdjustment):Product
    @GET("api/{scope}/products/{id}/inventory") suspend fun inventoryHistory(@Path("scope") scope:String,@Path("id") id:String):List<InventoryRecord>
    @Multipart @POST("api/seller/products/{id}/images") suspend fun uploadPhoto(@Path("id") id:String,@Query("version") version:Int,@Query("requestId") request:String,@Part file:MultipartBody.Part):Product
    @Multipart @POST("api/seller/products/{id}/images/{image}") suspend fun replacePhoto(@Path("id") id:String,@Path("image") image:String,@Query("version") version:Int,@Query("requestId") request:String,@Part file:MultipartBody.Part):Product
    @PUT("api/seller/products/{id}/images/order") suspend fun orderPhotos(@Path("id") id:String,@Body input:ImageOrderInput):Product
    @HTTP(method="DELETE",path="api/seller/products/{id}/images/{image}",hasBody=true) suspend fun removePhoto(@Path("id") id:String,@Path("image") image:String,@Body input:MutationInput):Product
    @POST("api/auth/profile") suspend fun firebaseProfile(@Body input: ProfileInput): User
    @POST("api/logout") suspend fun logout()
    @GET("api/me") suspend fun me(): User
    @GET("api/categories") suspend fun categories(): List<Category>
    @GET("api/products") suspend fun products(@Query("q") query: String?, @Query("category") category: String?, @Query("page") page: Int, @Query("pageSize") pageSize: Int = 20): ProductPage
    @GET("api/products/{id}") suspend fun product(@Path("id") id: String): Product
    @GET("api/cart") suspend fun cart(): Cart
    @PUT("api/cart/items/{id}") suspend fun quantity(@Path("id") id: String, @Body input: QuantityInput): Cart
    @DELETE("api/cart/items/{id}") suspend fun remove(@Path("id") id: String): Cart
    @DELETE("api/cart") suspend fun clearCart(): Cart
    @GET("api/seller/products") suspend fun ownProducts(): List<Product>
    @POST("api/seller/products") suspend fun createProduct(@Body input: ProductInput): Product
    @PUT("api/seller/products/{id}") suspend fun updateProduct(@Path("id") id: String, @Body input: ProductInput): Product
    @DELETE("api/seller/products/{id}") suspend fun archive(@Path("id") id: String, @Query("version") version: Int)
    @GET("api/admin/users") suspend fun users(): List<User>
    @GET("api/admin/audit") suspend fun audits(): List<Audit>
    @GET("api/profile") suspend fun profile(): ProfileDetails
    @POST("api/notifications/{id}/device-receipt") suspend fun deviceReceipt(@Path("id") id:String)
    @GET("api/admin/invitations") suspend fun sellerInvitations():List<SellerInvitation>
    @POST("api/admin/invitations/{id}/revoke") suspend fun revokeInvitation(@Path("id") id:String):SellerInvitation
    @POST("api/admin/invitations/{id}/send") suspend fun sendInvitation(@Path("id") id:String):SellerInvitation
    @POST("api/invitations/{id}/accept") suspend fun acceptInvitation(@Path("id") id:String,@Body input:InvitationAcceptance):User
    @PUT("api/profile") suspend fun updateProfile(@Body input: ProfileUpdate): ProfileDetails
    @GET("api/addresses") suspend fun addresses(): List<Address>
    @GET("api/addresses/{id}") suspend fun address(@Path("id") id:String): Address
    @POST("api/addresses") suspend fun createAddress(@Body input:AddressInput): Address
    @PUT("api/addresses/{id}") suspend fun updateAddress(@Path("id") id:String,@Body input:AddressInput): Address
    @PUT("api/addresses/{id}/default") suspend fun defaultAddress(@Path("id") id:String,@Body input:MutationInput): Address
    @HTTP(method="DELETE",path="api/addresses/{id}",hasBody=true) suspend fun deleteAddress(@Path("id") id:String,@Body input:MutationInput)
    @GET("api/preferences") suspend fun preferences(): Preferences
    @GET("api/preferences/consents") suspend fun consents():List<ConsentRecord>
    @PUT("api/preferences") suspend fun updatePreferences(@Body input:PreferencesUpdate): Preferences
    @GET("api/privacy/requests") suspend fun privacyRequests(): List<PrivacyRequest>
    @POST("api/privacy/requests") suspend fun privacyRequest(@Body input:PrivacyRequestInput): PrivacyRequest
    @GET("api/privacy/export") suspend fun exportData(): PersonalDataExport
    @GET("api/admin/users") suspend fun userPage(@Query("page") page:Int,@Query("pageSize") size:Int=20,@Query("q") q:String?=null,@Query("role") role:String?=null,@Query("active") active:Boolean?=null): UserPage
    @GET("api/admin/users/{id}") suspend fun userDetails(@Path("id") id:String): UserDetails
    @PUT("api/admin/users/{id}/access") suspend fun userAccess(@Path("id") id:String,@Body input:AccessUpdate): ProfileDetails
    @POST("api/admin/users") suspend fun createUser(@Body input:UserCreation): UserCreationResult
    @GET("api/admin/audit") suspend fun auditPage(@Query("page") page:Int,@Query("pageSize") size:Int=20,@Query("q") q:String?=null,@Query("result") result:String?=null,@Query("user") user:String?=null): AuditPage
}
