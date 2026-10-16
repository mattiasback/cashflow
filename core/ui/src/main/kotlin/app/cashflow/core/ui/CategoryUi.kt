package app.cashflow.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.domain.model.Category
import app.cashflow.core.domain.model.CategoryId
import app.cashflow.core.domain.model.CategoryKind

/** Flow colour family of a category kind, resolved in the theme. */
enum class FlowTone { Income, Fixed, Variable, Savings, Neutral, Transfer }

fun CategoryKind.tone(): FlowTone = when (this) {
    CategoryKind.INCOME -> FlowTone.Income
    CategoryKind.FIXED -> FlowTone.Fixed
    CategoryKind.VARIABLE -> FlowTone.Variable
    CategoryKind.SAVINGS -> FlowTone.Savings
    CategoryKind.TRANSFER -> FlowTone.Transfer
    CategoryKind.INVESTMENT -> FlowTone.Savings
    CategoryKind.UNCATEGORIZED -> FlowTone.Neutral
}

@Immutable
data class ToneColors(val strong: Color, val fill: Color)

@Composable
@ReadOnlyComposable
fun FlowTone.colors(): ToneColors = with(CashFlowTheme.colors) {
    when (this@colors) {
        FlowTone.Income -> ToneColors(vizIncome, vizIncomeFill)
        FlowTone.Fixed -> ToneColors(vizFixed, vizFixedFill)
        FlowTone.Variable -> ToneColors(vizVariable, vizVariableFill)
        FlowTone.Savings -> ToneColors(vizSavings, vizSavingsFill)
        FlowTone.Neutral -> ToneColors(vizNeutral, vizNeutralFill)
        FlowTone.Transfer -> ToneColors(vizInvest, vizInvestFill)
    }
}

/**
 * Built-in categories are named through string resources so English works; categories a
 * user creates later keep their own name.
 */
fun Category.displayName(): UiText = categoryName(id, name)

fun categoryName(id: CategoryId, fallback: String): UiText = when (id.value) {
    "lon" -> UiText.StringResource(R.string.category_lon)
    "barnbidrag" -> UiText.StringResource(R.string.category_barnbidrag)
    "ersattning" -> UiText.StringResource(R.string.category_ersattning)
    "utdelning" -> UiText.StringResource(R.string.category_utdelning)
    "inkomst_ovrig" -> UiText.StringResource(R.string.category_inkomst_ovrig)
    "boende" -> UiText.StringResource(R.string.category_boende)
    "transport" -> UiText.StringResource(R.string.category_transport)
    "barnomsorg" -> UiText.StringResource(R.string.category_barnomsorg)
    "forsakringar" -> UiText.StringResource(R.string.category_forsakringar)
    "abonnemang" -> UiText.StringResource(R.string.category_abonnemang)
    "lan" -> UiText.StringResource(R.string.category_lan)
    "fack" -> UiText.StringResource(R.string.category_fack)
    "ovrigt_fast" -> UiText.StringResource(R.string.category_ovrigt_fast)
    "mat" -> UiText.StringResource(R.string.category_mat)
    "restaurang" -> UiText.StringResource(R.string.category_restaurang)
    "shopping" -> UiText.StringResource(R.string.category_shopping)
    "hem_bygg" -> UiText.StringResource(R.string.category_hem_bygg)
    "halsa" -> UiText.StringResource(R.string.category_halsa)
    "noje" -> UiText.StringResource(R.string.category_noje)
    "resa" -> UiText.StringResource(R.string.category_resa)
    "ovrigt_rorligt" -> UiText.StringResource(R.string.category_ovrigt_rorligt)
    "sparande" -> UiText.StringResource(R.string.category_sparande)
    "fonder" -> UiText.StringResource(R.string.category_fonder)
    "buffert" -> UiText.StringResource(R.string.category_buffert)
    "pension" -> UiText.StringResource(R.string.category_pension)
    "barnspar" -> UiText.StringResource(R.string.category_barnspar)
    "amortering" -> UiText.StringResource(R.string.category_amortering)
    "overforing" -> UiText.StringResource(R.string.category_overforing)
    "vardepapper" -> UiText.StringResource(R.string.category_vardepapper)
    "avkastning" -> UiText.StringResource(R.string.category_avkastning)
    "sparkostnad" -> UiText.StringResource(R.string.category_sparkostnad)
    "okat" -> UiText.StringResource(R.string.category_okat)
    else -> UiText.DynamicString(fallback)
}

/** Group headings for category pickers, in the order the prototype uses. */
fun CategoryKind.groupName(): UiText = when (this) {
    CategoryKind.INCOME -> UiText.StringResource(R.string.group_income)
    CategoryKind.FIXED -> UiText.StringResource(R.string.group_fixed)
    CategoryKind.VARIABLE -> UiText.StringResource(R.string.group_variable)
    CategoryKind.SAVINGS -> UiText.StringResource(R.string.group_savings)
    CategoryKind.TRANSFER -> UiText.StringResource(R.string.group_transfer)
    CategoryKind.INVESTMENT -> UiText.StringResource(R.string.group_investment)
    CategoryKind.UNCATEGORIZED -> UiText.StringResource(R.string.group_uncategorized)
}
