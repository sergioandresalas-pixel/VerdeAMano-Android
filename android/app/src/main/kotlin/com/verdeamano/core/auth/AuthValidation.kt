package com.verdeamano.core.auth

import com.verdeamano.contracts.Validation

object AuthValidation {
    fun error(name: String?, email: String, password: String, confirmation: String, acceptedTerms: Boolean): String? = when {
        !Validation.email(email.trim()) -> "Indica un correo válido."
        name == null && password.length !in 6..128 -> "Indica una contraseña de 6 a 128 caracteres."
        name != null && name.trim().length !in 2..120 -> "Indica un nombre de 2 a 120 caracteres."
        name != null && password.length !in 16..128 -> "Usa una contraseña de 16 a 128 caracteres."
        name != null && password != confirmation -> "Las contraseñas no coinciden."
        name != null && !acceptedTerms -> "Acepta las condiciones para crear tu cuenta."
        else -> null
    }
}
