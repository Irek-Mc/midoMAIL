package midomail.domain.port

/**
 * Port bezpiecznego przechowywania sekretów (10-Core/18-Porty.md, §4;
 * SPEC-0017-Secret-Store-Contract.md).
 *
 * [reference] odpowiada formatowi już ustalonemu w SPEC-0005-Configuration-Model.md
 * (`credentials.secretRef`, np. `"email-primary/credentials"`) — port nie interpretuje jego
 * wewnętrznej struktury.
 */
interface SecretStore {
    fun read(reference: String): String?
    fun write(reference: String, value: String)
}
