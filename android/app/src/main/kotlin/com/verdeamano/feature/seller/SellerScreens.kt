package com.verdeamano.feature.seller

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verdeamano.contracts.Product
import com.verdeamano.contracts.PublicationState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.verdeamano.core.design.*

@Composable fun SellerScreen(viewModel: SellerViewModel, dashboard: Boolean, edit: (String) -> Unit, products: () -> Unit,photos:(String)->Unit={},inventory:(String)->Unit={}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var archive by remember { mutableStateOf<Product?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    LazyColumn(Modifier.fillMaxSize().testTag("seller-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BrandHeader(if (dashboard) "Mi negocio" else "Mis productos", "Gestiona tu catálogo de jardinería") }
        if (state.loading) item { LoadingState() }
        state.error?.let { item { ErrorState(it, viewModel::load) } }
        if (dashboard) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("${state.data?.count { it.active } ?: 0}", "Productos activos", Modifier.weight(1f))
                MetricCard("${state.data?.sumOf { it.stock } ?: 0}", "Unidades en inventario", Modifier.weight(1f))
            } }
            item { Button(products, Modifier.fillMaxWidth()) { Text("Gestionar productos") } }
            item { Text("Las publicaciones nuevas requieren revisión administrativa. Consulta los pedidos recibidos desde la pestaña Pedidos.") }
        } else {
            item { Button({ edit("new") }, Modifier.fillMaxWidth(), enabled = !state.loading) { Text("Crear producto") } }
            if (!state.loading && state.error == null && state.data.isNullOrEmpty()) item { EmptyState("Aún no tienes productos. Publica tu primer artículo.") }
            items(state.data.orEmpty(), key = { it.id }) { product ->
                Column {
                    ProductCard(product, if (product.active) "Editar producto" else "Consultar archivado") { edit(product.id) }
                    if(product.active&&product.publicationState!=PublicationState.SUSPENDED){
                        OutlinedButton({photos(product.id)},enabled=!state.loading){Text("Fotografías ${product.name}")}
                        OutlinedButton({inventory(product.id)},enabled=!state.loading){Text("Inventario ${product.name}")}
                    }
                    if(product.publicationState in listOf(PublicationState.DRAFT,PublicationState.REJECTED))Button({viewModel.publication(product,PublicationState.PENDING)},enabled=!state.loading&&product.images.isNotEmpty()){Text("Solicitar publicación")}
                    if(product.publicationState==PublicationState.ARCHIVED)OutlinedButton({viewModel.publication(product,PublicationState.DRAFT)},enabled=!state.loading){Text("Reactivar como borrador")}
                    if(product.moderationReason.isNotBlank())Text("Revisión: ${product.moderationReason}")
                    if (product.active && product.publicationState != PublicationState.SUSPENDED) TextButton({ archive = product },Modifier.semantics { contentDescription="Desactivar ${product.name}" }, enabled = !state.loading) { Text("Desactivar producto") }
                }
            }
        }
    }
    archive?.let { product -> AlertDialog(onDismissRequest = { archive = null }, title = { Text("Desactivar ${product.name}") }, text = { Text("Se archivará el producto y se retirará del catálogo. Su historial se conservará.") }, confirmButton = { TextButton({ archive = null; viewModel.archive(product) }) { Text("Desactivar") } }, dismissButton = { TextButton({ archive = null }) { Text("Cancelar") } }) }
}
