package app.cashflow.feature.profile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cashflow.core.designsystem.component.AccentHeader
import app.cashflow.core.designsystem.component.BandIconButton
import app.cashflow.core.designsystem.component.CfCard
import app.cashflow.core.designsystem.component.CfSwitchRow
import app.cashflow.core.designsystem.component.GroupHeading
import app.cashflow.core.designsystem.component.ListGroup
import app.cashflow.core.designsystem.component.ListRow
import app.cashflow.core.designsystem.component.Segment
import app.cashflow.core.designsystem.component.SegmentedControl
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Spacing
import app.cashflow.core.domain.model.ThemeMode
import app.cashflow.core.ui.ObserveAsEvents
import app.cashflow.core.ui.UiText
import app.cashflow.feature.profile.ui.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileRoot(
    contentPadding: PaddingValues,
    onOpenAccounts: () -> Unit,
    onOpenMortgage: () -> Unit,
    onOpenImportHistory: () -> Unit,
    onOpenRules: () -> Unit,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ProfileEvent.OpenAccounts -> onOpenAccounts()
            ProfileEvent.OpenImportHistory -> onOpenImportHistory()
            ProfileEvent.OpenRules -> onOpenRules()
            ProfileEvent.OpenMortgage -> onOpenMortgage()
            ProfileEvent.NavigateBack -> onBack()
        }
    }
    ProfileScreen(state, viewModel::onAction, contentPadding)
}

@Composable
fun ProfileScreen(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val c = CashFlowTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bgCanvas)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        AccentHeader(
            accent = Accent.Settings,
            title = stringResource(R.string.profile_title),
            actions = { BandIconButton(CfIcons.ChevronLeft, stringResource(R.string.cd_back), { onAction(ProfileAction.OnBackClick) }) },
        ) {
            CfCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                    Text(stringResource(R.string.profile_intro_title), style = CashFlowTheme.typography.bodyStrong, color = c.textPrimary)
                    Text(stringResource(R.string.profile_intro_body), style = CashFlowTheme.typography.label, color = c.textSecondary)
                }
            }
        }
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            GroupHeading(stringResource(R.string.profile_group_accounts))
            ListGroup {
                NavRow(stringResource(R.string.profile_accounts), state.accountsLine.asString()) { onAction(ProfileAction.OnAccountsClick) }
                NavRow(stringResource(R.string.profile_import_history), state.importsLine.asString(), divider = true) { onAction(ProfileAction.OnImportHistoryClick) }
            }

            GroupHeading(stringResource(R.string.profile_group_calc))
            ListGroup {
                Column(Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s2)) {
                    CfSwitchRow(
                        label = stringResource(R.string.profile_mortgage_switch),
                        checked = state.mortgageEnabled,
                        onCheckedChange = { onAction(ProfileAction.OnMortgageToggle(it)) },
                    )
                    Text(stringResource(R.string.profile_mortgage_hint), style = CashFlowTheme.typography.label, color = c.textSecondary)
                }
                state.mortgageLine?.let { line ->
                    NavRow(stringResource(R.string.profile_mortgage_split), line.asString(), divider = true) { onAction(ProfileAction.OnMortgageClick) }
                }
            }

            GroupHeading(stringResource(R.string.profile_group_categories))
            ListGroup {
                NavRow(stringResource(R.string.profile_rules), state.rulesLine.asString()) { onAction(ProfileAction.OnRulesClick) }
            }

            GroupHeading(stringResource(R.string.profile_group_display))
            ListGroup {
                Column(Modifier.padding(Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                    CfSwitchRow(
                        label = stringResource(R.string.profile_percent_decimals),
                        checked = state.percentDecimals,
                        onCheckedChange = { onAction(ProfileAction.OnPercentDecimalsToggle(it)) },
                    )
                    Text(
                        stringResource(R.string.profile_percent_example, if (state.percentDecimals) "28,6 %" else "29 %"),
                        style = CashFlowTheme.typography.label,
                        color = c.textSecondary,
                    )
                    Text(stringResource(R.string.profile_theme), style = CashFlowTheme.typography.body, color = c.textPrimary)
                    SegmentedControl(
                        segments = listOf(
                            Segment(ThemeMode.SYSTEM.name, stringResource(R.string.profile_theme_system)),
                            Segment(ThemeMode.LIGHT.name, stringResource(R.string.profile_theme_light)),
                            Segment(ThemeMode.DARK.name, stringResource(R.string.profile_theme_dark)),
                        ),
                        selectedKey = state.theme.name,
                        onSelect = { onAction(ProfileAction.OnThemeSelect(ThemeMode.valueOf(it))) },
                    )
                }
            }
            Text(
                stringResource(R.string.profile_footer),
                style = CashFlowTheme.typography.label,
                color = c.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s6, bottom = Spacing.s6),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, divider: Boolean = false, onClick: () -> Unit) {
    ListRow(
        title = title,
        subtitle = subtitle,
        showDivider = divider,
        onClick = onClick,
        trailing = { Icon(CfIcons.ChevronRight, contentDescription = null, tint = CashFlowTheme.colors.textSecondary, modifier = Modifier.padding(top = 2.dp)) },
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    CashFlowTheme {
        ProfileScreen(
            ProfileState(
                accountsLine = UiText.DynamicString("9 konton · sparande 214 500 kr"),
                importsLine = UiText.DynamicString("23 importer · ångra en hel import när som helst"),
                rulesLine = UiText.DynamicString("2 egna regler · 41 ihågkomna mottagare"),
                mortgageEnabled = true,
                mortgageLine = UiText.DynamicString("Amortering 2 500 kr per betalning"),
                isLoading = false,
            ),
            onAction = {},
        )
    }
}
