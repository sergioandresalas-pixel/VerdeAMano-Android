package com.verdeamano.feature.cart

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verdeamano.core.design.*

@Composable fun CartScreen(viewModel: CartViewModel, checkout: () -> Unit = {}, pendingConfirmation:Boolean=false, product: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    if(recent!=null){RecentCartScreen(requireNotNull(recent),viewModel::load);return}
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.load() }
    LazyColumn(Modifier.fillMaxSize().testTag("cart-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BrandHeader("Carrito", "Revisa cantidades y disponibilidad") }
        if(pendingConfirmation)item{Button(checkout,enabled=!state.loading&&state.error==null,modifier=Modifier.testTag("cart-recover-order")){Text("Recuperar confirmación pendiente")}}
        if (state.loading) item { LoadingState() }
        state.error?.let { item { ErrorState(it, viewModel::load) } }
        if (!state.loading && state.error == null && state.data?.items.isNullOrEmpty()) item { EmptyState("Tu carrito está vacío. Encuentra tu próximo proyecto verde en el catálogo.") }
        items(state.data?.items.orEmpty(), key = { it.product.id }) { line ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProductCard(line.product) { product(line.product.id) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton({ viewModel.quantity(line.product.id, line.quantity - 1) }, enabled = !state.loading) { Icon(Icons.Rounded.Remove, "Reducir cantidad") }
                    Text("${line.quantity}", Modifier.padding(vertical = 12.dp))
                    IconButton({ viewModel.quantity(line.product.id, line.quantity + 1) }, enabled = !state.loading && line.quantity < minOf(line.product.stock,100) && line.available) { Icon(Icons.Rounded.Add, "Aumentar cantidad") }
                    IconButton({ viewModel.quantity(line.product.id, 0) }, enabled = !state.loading) { Icon(Icons.Rounded.DeleteOutline, "Quitar ${line.product.name}") }
                }
                Text("Subtotal: ${cop(line.subtotalCop)}")
                line.notice?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            }
        }
        if (!state.data?.items.isNullOrEmpty()) {
            item { Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) { Column(Modifier.fillMaxWidth().padding(18.dp)) { Text("Subtotal de productos", style = MaterialTheme.typography.labelLarge); Text(cop(state.data?.subtotalCop ?: 0),Modifier.testTag("cart-total"), style = MaterialTheme.typography.headlineSmall) } } }
            item { Text("Puedes preparar un pedido de prueba. El checkout revalida precios e inventario; no realiza cobros.") }
            item { Button(checkout,enabled=!state.loading&&state.error==null&&state.data?.items?.all{it.available}==true,modifier=Modifier.testTag("cart-checkout")){Text("Preparar pedido")}}
            item { OutlinedButton({ confirmClear = true }, enabled = !state.loading) { Text("Vaciar carrito") } }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("¿Vaciar tu carrito?") }, text = { Text("Se quitarán todos los productos de tu carrito.") }, confirmButton = { TextButton({ confirmClear = false; viewModel.clear() }) { Text("Vaciar") } }, dismissButton = { TextButton({ confirmClear = false }) { Text("Cancelar") } })
}

@Composable fun RecentCartScreen(snapshot:com.verdeamano.core.data.RecentCartSnapshot,retry:()->Unit,logout:(()->Unit)?=null){
    LazyColumn(Modifier.fillMaxSize().testTag("degraded-cart"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{BrandHeader("Carrito reciente","Consulta sin conexión");Text("Estos datos corresponden a la última consulta guardada. Precios y existencias pueden haber cambiado. No puedes modificar el carrito ni crear pedidos o pagos sin consultar al servidor.");Text("Última consulta: "+java.time.Instant.ofEpochMilli(snapshot.savedAtEpochMillis).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))}
        items(snapshot.rows,key={it.productId}){row->OutlinedCard{Column(Modifier.padding(16.dp)){Text(row.name);Text("SKU: ${row.sku}");Text("Cantidad guardada: ${row.quantity}");Text("Precio en la última consulta: ${cop(row.unitPriceCop)}")}}}
        item{Text("Subtotal de la última consulta: ${cop(snapshot.subtotalCop)}");Button(retry){Text("Reconectar y consultar al servidor")};logout?.let{TextButton(it){Text("Cerrar sesión")}}}
    }
}
