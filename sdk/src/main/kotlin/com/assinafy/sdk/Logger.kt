package com.assinafy.sdk

interface Logger {
    fun debug(message: String, context: Map<String, Any> = emptyMap())
    fun info(message: String, context: Map<String, Any> = emptyMap())
    fun warn(message: String, context: Map<String, Any> = emptyMap())
    fun error(message: String, context: Map<String, Any> = emptyMap())
}

internal object NoOpLogger : Logger {
    override fun debug(message: String, context: Map<String, Any>) = Unit
    override fun info(message: String, context: Map<String, Any>) = Unit
    override fun warn(message: String, context: Map<String, Any>) = Unit
    override fun error(message: String, context: Map<String, Any>) = Unit
}
