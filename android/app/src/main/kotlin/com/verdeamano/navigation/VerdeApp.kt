package com.verdeamano.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.*
import com.verdeamano.contracts.Role
import com.verdeamano.feature.admin.*
import com.verdeamano.feature.auth.*
import com.verdeamano.feature.cart.*
import com.verdeamano.feature.catalog.*
import com.verdeamano.feature.profile.*
import com.verdeamano.feature.seller.*
import com.verdeamano.feature.orders.*
import com.verdeamano.feature.commercial.*

private data class Tab(val route: String, val label: String, val icon: ImageVector)
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VerdeApp(auth: AuthViewModel = hiltViewModel()) {
    val session by auth.session.collectAsStateWithLifecycle()
    val authentication by auth.state.collectAsStateWithLifecycle()
    DisposableEffect(auth){onDispose{auth.cancelBiometricAttempt()}}
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP){auth.lockForBackground()}
    if(session==null&&authentication.localUnlock){LocalUnlockScreen(auth);return}
    if(session==null&&authentication.degradedCart!=null){RecentCartScreen(requireNotNull(authentication.degradedCart),auth::restore,auth::logout);return}
    if(session == null && authentication.needsTerms) { CompleteProfileScreen(auth); return }
    if(session == null && authentication.restoring) { RestoreSessionScreen(auth); return }
    // A fresh navigation graph destroys previous user-scoped ViewModels on logout/401.
    key(session?.user?.let { "${it.id}:${it.role}" } ?: "guest:${authentication.showLogin}") {
        SessionNavigation {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val role = session?.user?.role
        val start = when(role) { Role.BUYER -> "home"; Role.SELLER -> "seller"; Role.ADMIN -> "admin"; null -> if(authentication.showLogin) "login" else "welcome" }
        val tabs = when(role) {
            Role.SELLER -> listOf(Tab("seller","Panel",Icons.Rounded.Dashboard),Tab("seller/products","Productos",Icons.Rounded.Inventory2),Tab("orders","Pedidos",Icons.Rounded.ReceiptLong),Tab("profile","Perfil",Icons.Rounded.Person))
            Role.ADMIN -> listOf(Tab("admin","Panel",Icons.Rounded.Dashboard),Tab("admin/users","Usuarios",Icons.Rounded.People),Tab("orders","Pedidos",Icons.Rounded.ReceiptLong),Tab("admin/audit","Auditoría",Icons.Rounded.History),Tab("profile","Perfil",Icons.Rounded.Person))
            else -> buildList {
                add(Tab("home","Inicio",Icons.Rounded.Home)); add(Tab("catalog","Catálogo",Icons.Rounded.LocalFlorist))
                if(role == Role.BUYER) add(Tab("cart","Carrito",Icons.Rounded.ShoppingCart))
                if(role == Role.BUYER) add(Tab("orders","Pedidos",Icons.Rounded.ReceiptLong))
                add(Tab("profile","Perfil",Icons.Rounded.Person))
            }
        }
        fun navigate(destination: String) { nav.navigate(destination) { launchSingleTop = true } }
        val showChrome = route !in listOf("welcome","login","register",null)
        Scaffold(
            topBar = {
                if(showChrome) TopAppBar(title = { Text("Verde a Mano") }, navigationIcon = {
                    if (route !in tabs.map { it.route }) IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"Volver") }
                },actions={
                    if(role!=null){
                        IconButton({navigate("notifications")}){Icon(Icons.Rounded.Notifications,"Notificaciones")}
                        IconButton({navigate("claims")}){Icon(Icons.Rounded.ContactSupport,"Reclamaciones")}
                    }
                })
            },
            bottomBar = {
                if(showChrome && route in tabs.map { it.route }) NavigationBar {
                    tabs.forEach { tab -> NavigationBarItem(selected = route == tab.route, onClick = {
                        nav.navigate(tab.route) {
                            popUpTo(start) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }, icon = { Icon(tab.icon,tab.label) }, label = { Text(tab.label) }) }
                }
            },
        ) { padding ->
            NavHost(nav,start,Modifier.padding(padding)) {
                if(session == null) {
                    composable("welcome") { WelcomeScreen({ navigate("login") },{ navigate("register") },{ navigate("home") }) }
                    composable("login") { AuthScreen(false,auth) { navigate("register") } }
                    composable("register") { AuthScreen(true,auth) { navigate("login") } }
                }
                composable("home") { CatalogScreen(hiltViewModel(),true) { navigate("product/$it") } }
                composable("catalog") { CatalogScreen(hiltViewModel(),false) { navigate("product/$it") } }
                composable("product/{id}") { DetailScreen(hiltViewModel(),role,{ if(session == null) navigate("login") },{ if(role == Role.BUYER) navigate("cart") }) }
                composable("profile") { ProfileScreen(session?.user,auth::logout,{ navigate("login") },authentication,auth::sendVerification,auth::refreshVerification,{navigate("profile/edit")},{navigate("profile/addresses")},{navigate("profile/preferences")},{navigate("profile/privacy")},{context->auth.biometric(context,true)},auth::disableBiometrics,invitation={navigate("profile/invitation")}) { context -> auth.linkGoogle(context) } }
                if(session != null) {
                    composable("orders"){OrdersScreen(hiltViewModel()){navigate("orders/$it")}}
                    composable("orders/{id}"){OrderDetailScreen(hiltViewModel(),requireNotNull(role),{navigate("payment/$it")},{navigate("claims/new/$it")})}
                    composable("payment/{id}"){PaymentScreen(hiltViewModel(),requireNotNull(it.arguments?.getString("id")))}
                    composable("claims"){ClaimsScreen(hiltViewModel()){navigate("claims/$it")}}
                    composable("claims/new/{order}"){NewClaimScreen(hiltViewModel(),requireNotNull(it.arguments?.getString("order"))){id->nav.navigate("claims/$id"){popUpTo("claims/new/{order}"){inclusive=true}}}}
                    composable("claims/{id}"){ClaimDetailScreen(hiltViewModel(),requireNotNull(it.arguments?.getString("id")),requireNotNull(role))}
                    composable("notifications"){NotificationsScreen(hiltViewModel()){_,id->navigate("notifications/$id")}}
                    composable("notifications/{id}"){NotificationDetailScreen(hiltViewModel(),requireNotNull(it.arguments?.getString("id"))){type,id->navigate(if(type=="claim")"claims/$id"else"orders/$id")}}
                    composable("profile/invitation"){SellerInvitationScreen(hiltViewModel(),false)}
                    composable("profile/edit") { ProfileEditorScreen(hiltViewModel()) }
                    composable("profile/addresses") { AddressesScreen(hiltViewModel()) {navigate("profile/address/$it")} }
                    composable("profile/address/{id}") { AddressEditorScreen(hiltViewModel(),it.arguments?.getString("id")?:"new") {nav.popBackStack()} }
                    composable("profile/preferences") { PreferencesScreen(hiltViewModel()) }
                    composable("profile/privacy") { PrivacyScreen(hiltViewModel()){navigate("profile/privacy/$it")} }
                    composable("profile/privacy/{id}"){PrivacyDetailScreen(hiltViewModel(),requireNotNull(it.arguments?.getString("id")),false)}
                }
                if(role == Role.BUYER) {
                    composable("cart") { val recovery:OrderRecoveryViewModel=hiltViewModel();CartScreen(hiltViewModel(),{navigate("checkout")},recovery.hasPendingConfirmation()) { navigate("product/$it") } }
                    composable("checkout"){CheckoutScreen(hiltViewModel(),{navigate("profile/addresses")},{id->nav.navigate("orders/$id"){popUpTo("checkout"){inclusive=true};launchSingleTop=true}},{navigate("checkout/address/new")})}
                    composable("checkout/address/new"){
                        val checkoutEntry=remember(it){nav.getBackStackEntry("checkout")};val checkout:CheckoutViewModel=hiltViewModel(checkoutEntry);val hint by checkout.state.collectAsStateWithLifecycle()
                        AddressEditorScreen(hiltViewModel(),"new",hint.suggestion){checkout.consumeLocationSuggestion();nav.popBackStack()}
                    }
                }
                if(role == Role.SELLER) {
                    composable("seller") { SellerScreen(hiltViewModel(),true,{ navigate("seller/edit/$it") },{ navigate("seller/products") }) }
                    composable("seller/products") { SellerScreen(hiltViewModel(),false,{ navigate("seller/edit/$it") },{ navigate("seller/products") },{navigate("seller/photos/$it")},{navigate("inventory/seller/$it")}) }
                    composable("seller/edit/{id}") { ProductEditorScreen(hiltViewModel()) { nav.popBackStack() } }
                    composable("seller/photos/{id}") { ProductPhotosScreen(hiltViewModel()) }
                }
                if(role==Role.SELLER||role==Role.ADMIN)composable("inventory/{scope}/{id}"){InventoryScreen(hiltViewModel())}
                if(role == Role.ADMIN) {
                    composable("admin") { AdminScreen(hiltViewModel(),"dashboard",{ navigate("admin/users") },{ navigate("admin/audit") },products={navigate("admin/products")},categories={navigate("admin/categories")},privacy={navigate("admin/privacy")},policies={navigate("admin/policies")},invitations={navigate("admin/invitations")},sales={navigate("admin/sales")}) }
                    composable("admin/sales") { SalesReportScreen(hiltViewModel()) }
                    composable("admin/policies"){CommercialPoliciesScreen(hiltViewModel())}
                    composable("admin/privacy"){PrivacyManagementScreen(hiltViewModel()){navigate("admin/privacy/$it")}}
                    composable("admin/privacy/{id}"){PrivacyDetailScreen(hiltViewModel(),requireNotNull(it.arguments?.getString("id")),true)}
                    composable("admin/categories") { CategoryManagementScreen(hiltViewModel()) }
                    composable("admin/products") { ProductSupervisionScreen(hiltViewModel()){navigate("admin/products/$it")} }
                    composable("admin/products/{id}") { ProductReviewScreen(hiltViewModel(),requireNotNull(it.arguments?.getString("id"))){navigate("inventory/admin/$it")} }
                    composable("admin/invitations"){SellerInvitationScreen(hiltViewModel(),true)}
                    composable("admin/users") { AdminScreen(hiltViewModel(),"users",{},{},{navigate("admin/users/$it")},{navigate("admin/create")}) }
                    composable("admin/users/{id}") { AdminUserScreen(hiltViewModel(),requireNotNull(it.arguments?.getString("id"))) }
                    composable("admin/create") { AdminCreateScreen(hiltViewModel()) }
                    composable("admin/audit") { AdminScreen(hiltViewModel(),"audit",{},{}) }
                }
            }
        }
        }
    }
}

@Composable private fun SessionNavigation(content: @Composable () -> Unit) {
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
