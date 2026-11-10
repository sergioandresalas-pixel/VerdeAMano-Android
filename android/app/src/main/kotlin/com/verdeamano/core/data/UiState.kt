package com.verdeamano.core.data

import com.verdeamano.contracts.ApiError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

data class UiState<T>(val data: T? = null, val loading: Boolean = false, val error: String? = null)
fun Throwable.userMessage(): String {
    if (this is CancellationException) throw this
    return when (this) {
        is HttpException -> {
            val safeMessage = runCatching { Json.decodeFromString<ApiError>(response()?.errorBody()?.string().orEmpty()).message }.getOrNull()
            safeMessage ?: when (code()) {
                401 -> "La sesión venció. Inicia sesión de nuevo."
                403 -> "No tienes permiso para esta operación."
                404 -> "El recurso no está disponible."
                409 -> "Los datos cambiaron. Recarga e intenta de nuevo."
                429 -> "Demasiados intentos. Espera un minuto."
                else -> "El servidor no pudo completar la operación."
            }
        }
        is IOException -> "No pudimos conectar con Verde a Mano. Revisa tu conexión y el servidor."
        else -> "No pudimos leer la respuesta. Intenta de nuevo."
    }
}
