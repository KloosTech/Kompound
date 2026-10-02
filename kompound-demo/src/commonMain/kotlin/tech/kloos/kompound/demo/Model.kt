package tech.kloos.kompound.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/** Metadata of one demo, mirrors `@KompoundDemo`. */
@Immutable
public class DemoMeta(
    public val id: String,
    public val title: String,
    public val description: String,
    public val category: String,
    public val tags: List<String>,
    public val since: String,
    public val status: String,
    public val platforms: List<String>,
    public val aliases: List<String>,
)

/** Receiver for demo functions that want interactive controls (grows in phase 2). */
public interface DemoScope

/** One demo. [qualifiedId] is `<moduleId>/<id>` and unique across the catalog. */
@Immutable
public class DemoEntry(
    public val moduleId: String,
    public val meta: DemoMeta,
    public val content: @Composable DemoScope.() -> Unit,
) {
    public val qualifiedId: String get() = "$moduleId/${meta.id}"
}

/** Implemented by the generated `KompoundRegistry_<moduleId>` objects. */
public interface DemoRegistry {
    public val moduleId: String
    public val entries: List<DemoEntry>
}
