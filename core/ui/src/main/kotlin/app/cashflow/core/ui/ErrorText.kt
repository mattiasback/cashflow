package app.cashflow.core.ui

import app.cashflow.core.domain.DataError
import app.cashflow.core.domain.ImportError

fun DataError.toUiText(): UiText = when (this) {
    DataError.Local.DISK_FULL -> UiText.StringResource(R.string.error_disk_full)
    DataError.Local.NOT_FOUND -> UiText.StringResource(R.string.error_not_found)
    DataError.Local.UNKNOWN -> UiText.StringResource(R.string.error_unknown)
}

fun ImportError.toUiText(): UiText = when (this) {
    ImportError.UNSUPPORTED_FORMAT -> UiText.StringResource(R.string.error_import_unsupported)
    ImportError.EMPTY_FILE -> UiText.StringResource(R.string.error_import_empty)
    ImportError.UNREADABLE_FILE -> UiText.StringResource(R.string.error_import_unreadable)
    ImportError.UNKNOWN_ACCOUNT -> UiText.StringResource(R.string.error_import_unknown_account)
    ImportError.BALANCE_CHAIN_BREAK -> UiText.StringResource(R.string.error_import_chain_break)
    ImportError.STORAGE -> UiText.StringResource(R.string.error_unknown)
}
