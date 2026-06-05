package com.assinafy.sdk

/**
 * Logging hook for the SDK. Provide an implementation via [AssinafyClientConfig.logger]; when it is
 * `null` (the default) the client uses a built-in no-op logger. Reference [Logger.NONE] if you need
 * to name that no-op explicitly.
 */
interface Logger {
    fun debug(message: String, context: Map<String, Any> = emptyMap())
    fun info(message: String, context: Map<String, Any> = emptyMap())
    fun warn(message: String, context: Map<String, Any> = emptyMap())
    fun error(message: String, context: Map<String, Any> = emptyMap())

    companion object {
        /** A logger that discards all messages (the SDK's default when no logger is configured). */
        val NONE: Logger = NoOpLogger
    }
}

internal object NoOpLogger : Logger {
    override fun debug(message: String, context: Map<String, Any>) = Unit
    override fun info(message: String, context: Map<String, Any>) = Unit
    override fun warn(message: String, context: Map<String, Any>) = Unit
    override fun error(message: String, context: Map<String, Any>) = Unit
}
