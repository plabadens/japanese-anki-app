package dev.pierre.japaneseanki.network

import kotlinx.serialization.json.Json

internal val GoogleJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = false
}
