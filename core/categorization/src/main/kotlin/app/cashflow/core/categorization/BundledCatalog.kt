package app.cashflow.core.categorization

import app.cashflow.core.domain.datasource.RuleCatalog
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind
import app.cashflow.core.domain.model.CategoryRule
import app.cashflow.core.domain.model.RuleMatch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Built-in reference data (default category tree and chain/merchant rules) ships as JSON
 * inside the app, versioned with it, and is never copied into the database.
 */
class BundledCatalog private constructor(
    private val categories: List<Category>,
    private val rules: List<CategoryRule>,
) : RuleCatalog {

    override suspend fun builtInRules(): List<CategoryRule> = rules
    override suspend fun defaultCategories(): List<Category> = categories

    companion object {
        private const val RESOURCE = "/cashflow/builtin-catalog.json"
        private val json = Json { ignoreUnknownKeys = true }

        /** Loads the catalog bundled on the classpath. */
        fun load(): BundledCatalog {
            val text = BundledCatalog::class.java.getResourceAsStream(RESOURCE)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Missing $RESOURCE")
            return parse(text)
        }

        fun parse(text: String): BundledCatalog {
            val dto = json.decodeFromString<CatalogDto>(text)
            return BundledCatalog(
                categories = dto.categories.mapIndexed { i, c ->
                    Category(CategoryId(c.id), c.name, CategoryKind.valueOf(c.kind), sortOrder = i)
                },
                rules = dto.rules.mapIndexed { i, r ->
                    CategoryRule(
                        id = "builtin-$i",
                        pattern = r.pattern,
                        match = RuleMatch.valueOf(r.match.uppercase()),
                        categoryId = CategoryId(r.category),
                        sign = r.sign,
                        isBuiltIn = true,
                    )
                },
            )
        }
    }

    @Serializable
    private data class CatalogDto(
        val version: Int,
        val categories: List<CategoryDto>,
        val rules: List<RuleDto>,
    )

    @Serializable
    private data class CategoryDto(val id: String, val name: String, val kind: String)

    @Serializable
    private data class RuleDto(
        @SerialName("p") val pattern: String,
        @SerialName("c") val category: String,
        @SerialName("m") val match: String = "contains",
        @SerialName("s") val sign: Int? = null,
    )
}
