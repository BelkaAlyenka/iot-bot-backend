package io.github.belkaalyenka

import kotlinx.serialization.Serializable

@Serializable
data class LedRequest(val state: String)

@Serializable
data class LedResponse(val status: String, val led: String)

@Serializable
data class ErrorResponse(val error: String)