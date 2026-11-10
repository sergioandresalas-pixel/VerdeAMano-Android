package com.verdeamano.contracts

import kotlinx.serialization.Serializable

@Serializable enum class Role { BUYER, SELLER, ADMIN }
@Serializable data class User(val id: String, val name: String, val email: String, val role: Role, val active: Boolean = true)
@Serializable data class Category(val id: String, val name: String, val active: Boolean = true, val position: Int = 0, val version: Int = 1)
@Serializable data class Product(
    val id: String, val sellerId: String, val sellerName: String, val categoryId: String,
    val sku: String, val name: String, val description: String, val material: String,
    val dimensions: String, val priceCop: Long, val stock: Int,
    val imageUrl: String? = null, val active: Boolean = true, val version: Int = 1,
    val publicationState: PublicationState = PublicationState.APPROVED, val images: List<ProductPhoto> = emptyList(),
    val instructions: String = "", val weightGrams: Int? = null, val deliveryDetails: String = "",
    val warranty: String = "", val terms: String = "", val featured: Boolean = false,
    val createdAt: String = "", val updatedAt: String = "", val moderationReason: String = "",
)
@Serializable data class ProductPage(val items: List<Product>, val total: Int, val page: Int, val pageSize: Int)
@Serializable data class ProductInput(
    val categoryId: String, val sku: String, val name: String, val description: String,
    val material: String, val dimensions: String, val priceCop: Long, val stock: Int,
    val imageUrl: String? = null, val version: Int? = null,
    val instructions: String = "", val weightGrams: Int? = null, val deliveryDetails: String = "",
    val warranty: String = "", val terms: String = "",
    val requestId: String? = null,
)
@Serializable data class CartItem(val product: Product, val quantity: Int, val subtotalCop: Long, val available: Boolean = true, val notice: String? = null)
@Serializable data class Cart(val items: List<CartItem> = emptyList(), val subtotalCop: Long = 0)
@Serializable data class QuantityInput(val quantity: Int)
@Serializable data class LoginInput(val email: String, val password: String)
@Serializable data class RegisterInput(val name: String, val email: String, val password: String)
// The bearer token supplies UID/email; public registration accepts neither roles nor identifiers.
@Serializable data class ProfileInput(val name: String? = null, val acceptedTerms: Boolean = false)
@Serializable data class Session(val token: String, val user: User, val expiresAt: String, val development: Boolean = true)
@Serializable data class ApiError(val code: String, val message: String)
@Serializable data class Health(val status: String, val storage: String, val developmentAuth: Boolean, val firebaseAuth: Boolean = false)
@Serializable data class Audit(val id: String, val actorId: String, val action: String, val resourceId: String, val createdAt: String)

// Address and historical commercial contracts are shared without persistence entities.
@Serializable data class Address(val id: String, val buyerId: String, val recipient: String, val city: String, val line: String,
    val phone: String = "", val department: String = "", val neighborhood: String = "", val complement: String = "", val instructions: String = "",
    val latitude: Double? = null, val longitude: Double? = null, val isDefault: Boolean = false, val active: Boolean = true,
    val createdAt: String = "", val updatedAt: String = "", val version: Int = 1)
@Serializable data class OrderLine(val productId: String, val sellerId: String, val name: String, val quantity: Int, val unitPriceCop: Long,
    val sku: String = "", val sellerName: String = "", val subtotalCop: Long = unitPriceCop * quantity)
@Serializable data class Payment(val id: String, val orderId: String, val reference: String, val amountCop: Long, val status: String)
@Serializable data class Claim(val id: String, val orderId: String, val buyerId: String, val reference: String, val reason: String, val status: String)
@Serializable data class ProductImage(val id: String, val productId: String, val url: String)
