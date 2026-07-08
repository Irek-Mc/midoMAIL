package midomail.domain.adapter

/**
 * Port logowania (SPEC-0010-Plugin-SDK-Contract.md, §Porty przekazywane adapterowi).
 * Niezależny od konkretnej biblioteki logowania — implementacja platformowa (np. SLF4J na JVM,
 * Android Log na Androidzie) znajduje się poza `:domain`.
 */
interface Logger {
    fun info(message: String)
    fun warn(message: String, throwable: Throwable? = null)
    fun error(message: String, throwable: Throwable? = null)
}
