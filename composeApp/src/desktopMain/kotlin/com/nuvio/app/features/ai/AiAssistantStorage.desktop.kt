package com.nuvio.app.features.ai

import com.nuvio.app.core.storage.DesktopStorage

internal actual object AiAssistantStorage {
    private val store = DesktopStorage.store("nuvio_ai_assistant")
    private const val payloadKey = "ai_assistant_settings"

    actual fun loadPayload(): String? = store.getString(payloadKey)

    actual fun savePayload(payload: String) {
        store.putString(payloadKey, payload)
    }
}
