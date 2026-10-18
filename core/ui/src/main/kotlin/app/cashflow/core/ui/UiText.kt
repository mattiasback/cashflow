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
        is StringResource -> stringResource(id, *resolved())
        is PluralResource -> pluralStringResource(id, count, *resolved())
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> context.getString(id, *resolved(context))
        is PluralResource -> context.resources.getQuantityString(id, count, *resolved(context))
    }

    // Arguments may themselves be UiText ("Utgifter · okt 2026"), resolved first.
    @Composable
    private fun resolved(): Array<Any> = argsOf().map { if (it is UiText) it.asString() else it }.toTypedArray()

    private fun resolved(context: Context): Array<Any> = argsOf().map { if (it is UiText) it.asString(context) else it }.toTypedArray()

    private fun argsOf(): Array<Any> = when (this) {
        is DynamicString -> emptyArray()
        is StringResource -> args
        is PluralResource -> args
    }
}

