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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import app.cashflow.core.designsystem.component.BottomNavPill
import app.cashflow.core.designsystem.component.NavItem
import app.cashflow.core.designsystem.component.NavRailPill
import app.cashflow.core.designsystem.component.LocalNavRailInset
import app.cashflow.core.designsystem.component.NavRailWidth
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
import app.cashflow.feature.home.ui.HomeRoute
import app.cashflow.feature.insights.ui.InsightsRoute
import app.cashflow.feature.insights.ui.insightsEntries
import app.cashflow.feature.home.ui.homeEntries
import app.cashflow.feature.importer.ui.ImportReceiptRoute
import app.cashflow.feature.importer.ui.ImportRoute
import app.cashflow.feature.importer.ui.ColumnMappingRoute
import app.cashflow.feature.importer.ui.ImportHistoryRoute
import app.cashflow.feature.importer.ui.importEntries
import app.cashflow.feature.profile.ui.AccountRoute
import app.cashflow.feature.profile.ui.AccountsRoute
import app.cashflow.feature.profile.ui.BufferBalanceRoute
import app.cashflow.feature.profile.ui.MortgageRoute
import app.cashflow.feature.profile.ui.ProfileRoute
import app.cashflow.feature.profile.ui.profileEntries
import app.cashflow.feature.rules.ui.RuleEditorRoute
import app.cashflow.feature.rules.ui.RulesRoute
import app.cashflow.feature.rules.ui.rulesEntries
import app.cashflow.navigation.BottomSheetSceneStrategy
import app.cashflow.navigation.Navigator
import app.cashflow.navigation.rememberNavigationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

private val TopLevel = listOf<NavKey>(HomeRoute, FlowRoute, InsightsRoute, EventsRoute)

/**
 * App shell: four tabs (Hem, Flöde, Insikter, Händelser) with their own back stacks, the floating nav pill,
 * and every cross-feature callback turned into a Navigator call.
 */
@Composable
fun CashFlowAppShell(
    sharedUris: Flow<List<String>>,
) {
    val state = rememberNavigationState(startRoute = HomeRoute, topLevelRoutes = TopLevel)
    val navigator = remember(state) { Navigator(state) }
    val sheet = remember { BottomSheetSceneStrategy<NavKey>() }

    LaunchedEffect(sharedUris) {
        sharedUris.collect { uris -> navigator.navigateIn(EventsRoute, ImportRoute(uris)) }
    }

    // Lists scroll behind the floating pill; their last row clears it.
    val navInset = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    // From 600dp (tablets, unfolded foldables, landscape) the pill stands at the start edge as a rail.
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
    val contentPadding = if (wide) PaddingValues(bottom = navInset + 16.dp) else PaddingValues(bottom = Sizes.navHeight + 32.dp + navInset)

    val homeReselect = remember(navigator) { navigator.reselectEvents.filter { it == HomeRoute }.map { } }
    val flowReselect = remember(navigator) { navigator.reselectEvents.filter { it == FlowRoute }.map { } }
    val insightsReselect = remember(navigator) { navigator.reselectEvents.filter { it == InsightsRoute }.map { } }
    val eventsReselect = remember(navigator) { navigator.reselectEvents.filter { it == EventsRoute }.map { } }

    val provider = entryProvider<NavKey> {
        homeEntries(
            contentPadding = contentPadding,
            scrollToTop = homeReselect,
            onOpenFlow = { navigator.selectTab(FlowRoute) },
            onOpenReview = { navigator.navigateIn(EventsRoute, ReviewRoute) },
            onOpenImport = { navigator.navigateIn(EventsRoute, ImportRoute()) },
            onOpenProfile = { navigator.navigate(ProfileRoute) },
            onOpenAccounts = { navigator.navigate(AccountsRoute) },
        )
        flowEntries(
            contentPadding = contentPadding,
            scrollToTop = flowReselect,
            onOpenEvents = { id, period -> navigator.navigateIn(EventsRoute, CategoryEventsRoute(id, period)) },
            onOpenImport = { navigator.navigateIn(EventsRoute, ImportRoute()) },
            onOpenProfile = { navigator.navigate(ProfileRoute) },
            onOpenTransaction = { navigator.navigate(TransactionDetailRoute(it)) },
        )
        insightsEntries(
            contentPadding = contentPadding,
            scrollToTop = insightsReselect,
            onOpenProfile = { navigator.navigate(ProfileRoute) },
            onOpenImport = { navigator.navigateIn(EventsRoute, ImportRoute()) },
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
            onOpenBufferBalance = { navigator.navigate(BufferBalanceRoute) },
            onOpenImportHistory = { navigator.navigate(ImportHistoryRoute) },
            onOpenRules = { navigator.navigate(RulesRoute) },
            onBack = { navigator.goBack() },
        )
        rulesEntries(
            contentPadding = contentPadding,
            sheetMetadata = BottomSheetSceneStrategy.sheet,
            onOpenEditor = { navigator.navigate(it) },
            onBack = { navigator.goBack() },
        )
        importEntries(
            contentPadding = contentPadding,
            sheetMetadata = BottomSheetSceneStrategy.sheet,
            onShowReceipt = { ids -> navigator.replace(ImportReceiptRoute(ids)) },
            onOpenReview = { navigator.replace(ReviewRoute) },
            onOpenMapping = { navigator.replace(ColumnMappingRoute(it)) },
            onBack = { navigator.goBack() },
        )
    }

    val current = state.currentKey
    val showNav = current !is ImportRoute && current !is ColumnMappingRoute
    val items = listOf(
        NavItem(HomeRoute.toString(), stringResource(R.string.nav_home), CfIcons.Home, Accent.Home),
        NavItem(FlowRoute.toString(), stringResource(R.string.nav_flow), CfIcons.Cashflow, Accent.Cashflow),
        NavItem(InsightsRoute.toString(), stringResource(R.string.nav_insights), CfIcons.Bulb, Accent.Insights),
        NavItem(EventsRoute.toString(), stringResource(R.string.nav_events), CfIcons.Transfer, Accent.Events),
    )

    Box(Modifier.fillMaxSize().background(CashFlowTheme.colors.bgCanvas)) {
        // Screens keep their content clear of the rail; their bands still run under it.
        CompositionLocalProvider(LocalNavRailInset provides if (wide && showNav) NavRailWidth else 0.dp) {
            NavDisplay(
                entries = state.toDecoratedEntries(provider),
                onBack = { navigator.goBack() },
                sceneStrategies = listOf(sheet),
            )
        }
        if (showNav) {
            val onSelect = { key: String -> navigator.selectTab(TopLevel.first { it.toString() == key }) }
            if (wide) {
                NavRailPill(items, state.topLevelRoute.toString(), onSelect, Modifier.align(Alignment.CenterStart))
            } else {
                BottomNavPill(items, state.topLevelRoute.toString(), onSelect, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}
