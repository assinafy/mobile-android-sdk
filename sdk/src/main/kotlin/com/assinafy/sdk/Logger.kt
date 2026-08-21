package com.assinafy.sdk

/**
 * Logging hook for the SDK. Provide an implementation via [AssinafyClientConfig.logger]; when it is
 * `null` (the default) the client uses a built-in no-op logger. Reference [Logger.NONE] if you need
 * to name that no-op explicitly.
 */
interface Logger {
    /**
     * Records diagnostic detail intended for development and troubleshooting.
     *
     * @param message Human-readable event description.
     * @param context Structured non-secret event values.
     */
    fun debug(message: String, context: Map<String, Any> = emptyMap())

    /**
     * Records a normal SDK lifecycle event.
     *
     * @param message Human-readable event description.
     * @param context Structured non-secret event values.
     */
    fun info(message: String, context: Map<String, Any> = emptyMap())

    /**
     * Records a recoverable or potentially problematic condition.
     *
     * @param message Human-readable event description.
     * @param context Structured non-secret event values.
     */
    fun warn(message: String, context: Map<String, Any> = emptyMap())

    /**
     * Records an operation failure.
     *
     * @param message Human-readable event description.
     * @param context Structured non-secret event values.
     */
    fun error(message: String, context: Map<String, Any> = emptyMap())

    /** Built-in logger implementations. */
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
