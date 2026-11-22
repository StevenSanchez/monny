package cr.monny.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cr.monny.data.Expense
import cr.monny.data.ThemeMode
import cr.monny.ui.screens.CardsScreen
import cr.monny.ui.screens.CategoriesScreen
import cr.monny.ui.screens.CycleDetailScreen
import cr.monny.ui.screens.EnvelopeDetailScreen
import cr.monny.ui.screens.EnvelopesScreen
import cr.monny.ui.screens.HistoryScreen
import cr.monny.ui.screens.HomeScreen
import cr.monny.ui.screens.ImportExportScreen
import cr.monny.ui.screens.MovementsScreen
import cr.monny.ui.screens.PaymentScreen
import cr.monny.ui.screens.PlansScreen
import cr.monny.ui.screens.ReportsScreen
import cr.monny.ui.screens.RecurringScreen
import cr.monny.ui.screens.SettingsScreen
import cr.monny.ui.screens.SharedScreen
import cr.monny.ui.sheets.AddExpenseSheet
import java.time.LocalDate

/*
 * Navegación principal.
 *
 * - Barra inferior con 4 pestañas: Inicio, Gastos, Sobres y Ajustes.
 * - Rutas secundarias (historial, tarjetas, categorías, datos, tasa 0, reportes,
 *   gastos fijos, detalle de ciclo, sobre, pago) sin barra inferior.
 * - La hoja de "nuevo/editar gasto" vive aquí para poder abrirse desde cualquier
 *   pantalla (por ejemplo al tocar un gasto dentro de un ciclo).
 */

/** Rutas de navegación. */
object Routes {
    const val HOME = "inicio"
    const val MOVEMENTS = "movimientos"
    const val ENVELOPES = "sobres"
    const val SETTINGS = "ajustes"
    const val CARDS = "tarjetas"
    const val CATEGORIES = "categorias"
    const val HISTORY = "historial"
    const val DATA = "datos"
    const val PLANS = "planes"
    const val REPORTS = "reportes"
    const val RECURRING = "fijos"
    const val SHARED = "compartido"
    const val ENVELOPE = "sobre/{id}"
    const val CYCLE = "ciclo/{cardId}/{cycleKey}"
    const val PAYMENT = "pago/{cardId}/{cycleKey}"

    fun envelope(id: Long) = "sobre/$id"
    fun cycle(cardId: Long, cycleKey: LocalDate) = "ciclo/$cardId/${cycleKey.toEpochDay()}"
    fun payment(cardId: Long, cycleKey: LocalDate) = "pago/$cardId/${cycleKey.toEpochDay()}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Inicio", Icons.Rounded.Home),
    Tab(Routes.MOVEMENTS, "Gastos", Icons.Rounded.ReceiptLong),
    Tab(Routes.ENVELOPES, "Sobres", Icons.Rounded.Savings),
    Tab(Routes.SETTINGS, "Ajustes", Icons.Rounded.Settings),
)

/** Punto de entrada de la UI: Scaffold, barra inferior, FAB y NavHost. */
@Composable
fun MonnyRoot(
    vm: MonnyViewModel,
    theme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHost = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.HOME
    val cards by vm.cards.collectAsStateWithLifecycle()

    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHost.showSnackbar(it) }
    }

    val showBottomBar = tabs.any { it.route == route }
    val showFab = (route == Routes.HOME || route == Routes.MOVEMENTS) && cards.isNotEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            AnimatedVisibility(visible = showBottomBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f),
                        ),
                        shadowElevation = 10.dp,
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0),
                            modifier = Modifier.height(66.dp),
                        ) {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = route == tab.route,
                                    onClick = {
                                        if (route != tab.route) {
                                            navController.navigate(tab.route) {
                                                popUpTo(Routes.HOME)
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    icon = {
                                        Icon(
                                            tab.icon,
                                            contentDescription = tab.label,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            tab.label,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editing = null
                        sheetOpen = true
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Gasto", style = MaterialTheme.typography.labelLarge) },
                    shape = RoundedCornerShape(50),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm,
                    onAddExpense = { editing = null; sheetOpen = true },
                    onSeeAll = { navController.navigate(Routes.MOVEMENTS) },
                    onHistory = { navController.navigate(Routes.HISTORY) },
                    onManageCards = { navController.navigate(Routes.CARDS) },
                    onPay = { cardId, cycleKey -> navController.navigate(Routes.payment(cardId, cycleKey)) },
                    onAddEnvelope = { navController.navigate(Routes.ENVELOPES) },
                    onImport = { navController.navigate(Routes.DATA) },
                    onShared = { navController.navigate(Routes.SHARED) },
                    onEditExpense = { expense ->
                        editing = expense
                        sheetOpen = true
                    },
                )
            }
            composable(Routes.MOVEMENTS) {
                MovementsScreen(
                    vm = vm,
                    onEdit = { expense ->
                        editing = expense
                        sheetOpen = true
                    },
                    onHistory = { navController.navigate(Routes.HISTORY) },
                )
            }
            composable(Routes.ENVELOPES) {
                EnvelopesScreen(
                    vm = vm,
                    onOpen = { id -> navController.navigate(Routes.envelope(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    vm = vm,
                    theme = theme,
                    onThemeChange = onThemeChange,
                    onCards = { navController.navigate(Routes.CARDS) },
                    onCategories = { navController.navigate(Routes.CATEGORIES) },
                    onHistory = { navController.navigate(Routes.HISTORY) },
                    onData = { navController.navigate(Routes.DATA) },
                    onPlans = { navController.navigate(Routes.PLANS) },
                    onReports = { navController.navigate(Routes.REPORTS) },
                    onRecurring = { navController.navigate(Routes.RECURRING) },
                )
            }
            composable(Routes.CARDS) {
                CardsScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onOpen = { cardId, cycleKey ->
                        navController.navigate(Routes.cycle(cardId, cycleKey))
                    },
                )
            }
            composable(Routes.DATA) {
                ImportExportScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.PLANS) {
                PlansScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.REPORTS) {
                ReportsScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.RECURRING) {
                RecurringScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.SHARED) {
                SharedScreen(vm = vm, onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.CYCLE,
                arguments = listOf(
                    navArgument("cardId") { type = NavType.LongType },
                    navArgument("cycleKey") { type = NavType.LongType },
                ),
            ) { entry ->
                val cardId = entry.arguments?.getLong("cardId") ?: 0L
                val cycleKey = entry.arguments?.getLong("cycleKey") ?: 0L
                CycleDetailScreen(
                    vm = vm,
                    cardId = cardId,
                    cycleKeyEpoch = cycleKey,
                    onBack = { navController.popBackStack() },
                    onPay = { navController.navigate(Routes.payment(cardId, LocalDate.ofEpochDay(cycleKey))) },
                    onEditExpense = { expense ->
                        editing = expense
                        sheetOpen = true
                    },
                )
            }
            composable(
                route = Routes.ENVELOPE,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                EnvelopeDetailScreen(
                    vm = vm,
                    envelopeId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.PAYMENT,
                arguments = listOf(
                    navArgument("cardId") { type = NavType.LongType },
                    navArgument("cycleKey") { type = NavType.LongType },
                ),
            ) { entry ->
                PaymentScreen(
                    vm = vm,
                    cardId = entry.arguments?.getLong("cardId") ?: 0L,
                    cycleKeyEpoch = entry.arguments?.getLong("cycleKey") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    if (sheetOpen) {
        AddExpenseSheet(
            vm = vm,
            editing = editing,
            onDismiss = {
                sheetOpen = false
                editing = null
            },
        )
    }
}
