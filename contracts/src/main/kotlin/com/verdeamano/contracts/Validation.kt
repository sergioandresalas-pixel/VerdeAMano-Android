package com.verdeamano.contracts

object Validation {
    fun email(value: String) = value.length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value)
    fun product(input: ProductInput): String? = when {
        input.name.trim().length !in 3..120 -> "El nombre debe tener entre 3 y 120 caracteres."
        input.sku.trim().length !in 1..40 -> "Indica un SKU de hasta 40 caracteres."
        input.description.trim().length !in 10..2000 -> "La descripción debe tener entre 10 y 2000 caracteres."
        input.material.trim().length !in 2..120 -> "Indica el material."
        input.dimensions.trim().length !in 2..120 -> "Indica las medidas."
        input.priceCop !in 1..100_000_000 -> "El precio debe ser positivo y expresado en COP enteros."
        input.stock !in 0..100_000 -> "Las existencias deben estar entre 0 y 100000."
        input.weightGrams != null && input.weightGrams !in 1..1_000_000 -> "El peso debe ser positivo y menor a 1000000 gramos."
        listOf(input.instructions, input.deliveryDetails, input.warranty, input.terms).any { it.length > 2000 } -> "Los detalles comerciales no pueden superar 2000 caracteres."
        input.imageUrl != null && (input.imageUrl.length > 2048 || !input.imageUrl.startsWith("https://")) -> "La imagen debe usar una URL HTTPS."
        else -> null
    }
}
