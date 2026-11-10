package com.verdeamano.contracts

import kotlinx.serialization.Serializable

@Serializable enum class PublicationState { DRAFT, PENDING, APPROVED, REJECTED, SUSPENDED, ARCHIVED }
@Serializable enum class CatalogSort { NAME, PRICE_ASC, PRICE_DESC, RECENT }
@Serializable data class ProductPhoto(val id: String, val url: String, val position: Int, val primary: Boolean,
    val width: Int, val height: Int, val byteSize: Long, val contentType: String = "image/jpeg")
@Serializable data class CategoryInput(val name: String, val active: Boolean = true, val position: Int = 0,
    val version: Int? = null, val requestId: String)
@Serializable data class PublicationInput(val state: PublicationState, val version: Int, val requestId: String,
    val reason: String = "", val featured: Boolean? = null)
@Serializable data class InventoryAdjustment(val delta: Int, val reason: String, val version: Int, val requestId: String)
@Serializable data class InventoryRecord(val id: String, val productId: String, val actorId: String, val before: Int,
    val after: Int, val delta: Int, val reason: String, val createdAt: String)
@Serializable data class ImageOrderInput(val imageIds: List<String>, val version: Int, val requestId: String)
