package app.cashflow.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Multiple back stacks, one per tab, saved across process death (Nav3 recipe). */
@Composable
fun rememberNavigationState(startRoute: NavKey, topLevelRoutes: List<NavKey>): NavigationState {
    val topLevelRoute = rememberSerializable(startRoute, topLevelRoutes, serializer = MutableStateSerializer(NavKeySerializer())) {
        mutableStateOf(startRoute)
    }
    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }
    return remember(startRoute, topLevelRoutes) { NavigationState(startRoute, topLevelRoute, backStacks) }
}

class NavigationState(
    val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    var topLevelRoute: NavKey by topLevelRoute

    val currentKey: NavKey get() = backStacks.getValue(topLevelRoute).last()

    @Composable
    fun toDecoratedEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decorated = backStacks.mapValues { (_, stack) ->
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider,
            )
        }
        // "Exit through the start tab": its entries are always underneath.
        val inUse = if (topLevelRoute == startRoute) listOf(startRoute) else listOf(startRoute, topLevelRoute)
        return inUse.flatMap { decorated[it].orEmpty() }
    }
}

/** The only place that changes the back stacks. Features call lambdas that :app maps here. */
class Navigator(private val state: NavigationState) {
    private val _reselect = MutableSharedFlow<NavKey>(extraBufferCapacity = 1)
    val reselectEvents = _reselect.asSharedFlow()

    fun selectTab(route: NavKey) {
        if (state.topLevelRoute == route) _reselect.tryEmit(route)
        state.topLevelRoute = route
    }

    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
        } else {
            state.backStacks.getValue(state.topLevelRoute).add(route)
        }
    }

    /** Opens [route] on top of [tab]'s stack, switching to that tab first. */
    fun navigateIn(tab: NavKey, route: NavKey) {
        state.topLevelRoute = tab
        state.backStacks.getValue(tab).add(route)
    }

    /** Replaces the current destination, e.g. the import screen with its receipt. */
    fun replace(route: NavKey) {
        val stack = state.backStacks.getValue(state.topLevelRoute)
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    fun goBack() {
        val stack = state.backStacks.getValue(state.topLevelRoute)
        if (stack.last() == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute
        } else {
            stack.removeAt(stack.lastIndex)
        }
    }
}
