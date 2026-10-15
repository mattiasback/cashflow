package app.cashflow.core.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/** A string that may come from resources; resolved only in the UI. */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText

    class StringResource(@param:StringRes val id: Int, val args: Array<Any> = emptyArray()) : UiText {
        override fun equals(other: Any?) = other is StringResource && other.id == id && other.args.contentEquals(args)
        override fun hashCode() = 31 * id + args.contentHashCode()
    }

    class PluralResource(@param:PluralsRes val id: Int, val count: Int, val args: Array<Any> = arrayOf(count)) : UiText {
        override fun equals(other: Any?) = other is PluralResource && other.id == id && other.count == count && other.args.contentEquals(args)
        override fun hashCode() = 31 * (31 * id + count) + args.contentHashCode()
    }

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> stringResource(id, *args)
        is PluralResource -> pluralStringResource(id, count, *args)
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> context.getString(id, *args)
        is PluralResource -> context.resources.getQuantityString(id, count, *args)
    }
}

