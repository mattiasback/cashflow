package app.cashflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import app.cashflow.core.designsystem.theme.band
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import app.cashflow.core.designsystem.component.BottomNavPill
import app.cashflow.core.designsystem.component.NavItem
import app.cashflow.core.designsystem.icon.CfIcons
import app.cashflow.core.designsystem.theme.Accent
import app.cashflow.core.designsystem.theme.CashFlowTheme
import app.cashflow.core.designsystem.theme.Sizes
import app.cashflow.feature.events.ui.CategoryEventsRoute
import app.cashflow.feature.events.ui.EventsRoute
import app.cashflow.feature.events.ui.ReviewRoute
import app.cashflow.feature.events.ui.TransactionDetailRoute
import app.cashflow.feature.events.ui.eventsEntries
import app.cashflow.feature.flow.ui.FlowRoute
import app.cashflow.feature.flow.ui.flowEntries
import app.cashflow.feature.importer.ui.ImportReceiptRoute
import app.cashflow.feature.importer.ui.ImportRoute
import app.cashflow.feature.importer.ui.importEntries
import app.cashflow.feature.profile.ui.AccountRoute
import app.cashflow.feature.profile.ui.AccountsRoute
import app.cashflow.feature.profile.ui.MortgageRoute
import app.cashflow.feature.profile.ui.ProfileRoute
import app.cashflow.feature.profile.ui.profileEntries
import app.cashflow.navigation.BottomSheetSceneStrategy
import app.cashflow.navigation.Navigator
import app.cashflow.navigation.rememberNavigationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

private val TopLevel = listOf<NavKey>(FlowRoute, EventsRoute)

/**
 * App shell: two tabs (Flöde, Händelser) with their own back stacks, the floating nav pill,
 * and every cross-feature callback turned into a Navigator call.
 */
@Composable
fun CashFlowAppShell(
    sharedUris: Flow<List<String>>,
) {
    val state = rememberNavigationState(startRoute = FlowRoute, topLevelRoutes = TopLevel)
    val navigator = remember(state) { Navigator(state) }
    val sheet = remember { BottomSheetSceneStrategy<NavKey>() }

    LaunchedEffect(sharedUris) {
        sharedUris.collect { uris -> navigator.navigateIn(EventsRoute, ImportRoute(uris)) }
    }

    // Lists scroll behind the floating pill; their last row clears it.
    val navInset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val contentPadding = PaddingValues(bottom = Sizes.navHeight + 32.dp + navInset)

    val flowReselect = remember(navigator) { navigator.reselectEvents.filter { it == FlowRoute }.map { } }
    val eventsReselect = remember(navigator) { navigator.reselectEvents.filter { it == EventsRoute }.map { } }

    val provider = entryProvider<NavKey> {
        flowEntries(
            contentPadding = contentPadding,
            scrollToTop = flowReselect,
            onOpenCategory = { id, period -> navigator.navigateIn(EventsRoute, CategoryEventsRoute(id, period)) },
            onOpenImport = { navigator.navigateIn(EventsRoute, ImportRoute()) },
            onOpenProfile = { navigator.navigate(ProfileRoute) },
        )
        eventsEntries(
            contentPadding = contentPadding,
            sheetMetadata = BottomSheetSceneStrategy.sheet,
            scrollToTop = eventsReselect,
            onOpenDetail = { navigator.navigate(TransactionDetailRoute(it)) },
            onOpenImport = { navigator.navigate(ImportRoute()) },
            onOpenReview = { navigator.navigate(ReviewRoute) },
            onOpenProfile = { navigator.navigate(ProfileRoute) },
            onBack = { navigator.goBack() },
        )
        profileEntries(
            contentPadding = contentPadding,
            sheetMetadata = BottomSheetSceneStrategy.sheet,
            onOpenAccounts = { navigator.navigate(AccountsRoute) },
            onOpenAccount = { navigator.navigate(AccountRoute(it)) },
            onOpenMortgage = { navigator.navigate(MortgageRoute) },
            onOpenImportHistory = { },
            onOpenRules = { },
            onBack = { navigator.goBack() },
        )
        importEntries(
            sheetMetadata = BottomSheetSceneStrategy.sheet,
            onShowReceipt = { ids -> navigator.replace(ImportReceiptRoute(ids)) },
            onOpenReview = { navigator.replace(ReviewRoute) },
            onBack = { navigator.goBack() },
        )
    }

    val current = state.currentKey
    val showNav = current !is ImportRoute
    val items = listOf(
        NavItem(FlowRoute.toString(), stringResource(R.string.nav_flow), CfIcons.Cashflow, Accent.Cashflow),
        NavItem(EventsRoute.toString(), stringResource(R.string.nav_events), CfIcons.Transfer, Accent.Events),
    )

    Box(Modifier.fillMaxSize().background(CashFlowTheme.colors.bgCanvas)) {
        NavDisplay(
            entries = state.toDecoratedEntries(provider),
            onBack = { navigator.goBack() },
            sceneStrategies = listOf(sheet),
        )
        // Status-bar protection: lists scroll behind the status bar, so keep the tab's accent band
        // there; the light status-bar icons stay legible over any content.
        val band = when {
            current is ProfileRoute || current is AccountsRoute || current is AccountRoute || current is MortgageRoute -> Accent.Settings
            state.topLevelRoute == FlowRoute -> Accent.Cashflow
            else -> Accent.Events
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(band.band())
                .align(Alignment.TopCenter),
        )
        if (showNav) {
            BottomNavPill(
                items = items,
                selectedKey = state.topLevelRoute.toString(),
                onSelect = { key -> navigator.selectTab(TopLevel.first { it.toString() == key }) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
