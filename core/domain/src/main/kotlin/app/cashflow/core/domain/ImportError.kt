package app.cashflow.core.domain

/** Whole-file problems. A single bad row is a warning on the receipt instead. */
enum class ImportError : Error {
    UNSUPPORTED_FORMAT,
    EMPTY_FILE,
    UNREADABLE_FILE,
    UNKNOWN_ACCOUNT,
    BALANCE_CHAIN_BREAK,
    STORAGE,
}
