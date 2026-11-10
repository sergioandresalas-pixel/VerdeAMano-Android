package com.verdeamano.feature.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.verdeamano.contracts.*
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verdeamano.core.design.*

@Composable fun CatalogScreen(viewModel: CatalogViewModel, home: Boolean, open: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    var minimum by remember{mutableStateOf("")};var maximum by remember{mutableStateOf("")};var available by remember{mutableStateOf(false)};var sort by remember{mutableStateOf(CatalogSort.NAME)}
    LaunchedEffect(home){viewModel.home(home)}
    LazyColumn(Modifier.fillMaxSize().testTag("catalog-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { if(home) BotanicalHero("Hola, ¿qué cultivamos hoy?", "Ideas para tus espacios pequeños") else BrandHeader("Catálogo", "Artículos para jardinería urbana") }
        item(key="search") { OutlinedTextField(query, { viewModel.filter(query = it.take(120)) }, Modifier.fillMaxWidth(), label = { Text("Buscar macetas, herramientas…") }, singleLine = true) }
        if(!home)item(key="filters"){
            OutlinedTextField(minimum,{minimum=it},label={Text("Precio mínimo COP")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(maximum,{maximum=it},label={Text("Precio máximo COP")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row{Checkbox(available,{available=it});Text("Solo disponibles")}
            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){CatalogSort.entries.forEach{option->FilterChip(sort==option,{sort=option},label={Text(when(option){CatalogSort.NAME->"Nombre";CatalogSort.PRICE_ASC->"Menor precio";CatalogSort.PRICE_DESC->"Mayor precio";CatalogSort.RECENT->"Más recientes"})})}}
            Button({viewModel.filters(minimum,maximum,available,sort)},enabled=!state.loading){Text("Aplicar filtros")}
        }
        item { Text("Categorías", style = MaterialTheme.typography.titleMedium) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(category == null, { viewModel.filter(category = null) }, label = { Text("Todos") }) }
                items(state.data?.categories.orEmpty(), key = { it.id }) { value -> FilterChip(category == value.id, { viewModel.filter(category = value.id) }, label = { Text(value.name) }) }
            }
        }
        item { Text(if (home) "Productos destacados" else "${state.data?.total ?: 0} productos", style = MaterialTheme.typography.titleLarge) }
        state.error?.let { item { ErrorState(it) { viewModel.load() } } }
        if (state.loading) item { LoadingState() }
        if (!state.loading && state.error == null && state.data?.products.isNullOrEmpty()) item { EmptyState(if(home)"Todavía no hay productos destacados. Consulta el catálogo." else "No encontramos productos con estos filtros.") }
        items(state.data?.products.orEmpty(), key = { it.id }) { product -> ProductCard(product) { open(product.id) } }
        if ((state.data?.products?.size ?: 0) < (state.data?.total ?: 0)) item { OutlinedButton({ viewModel.load(next = true) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Cargar más") } }
        item { Text("Catálogo de desarrollo", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable fun DetailScreen(viewModel: DetailViewModel, role: Role?, login: () -> Unit, cart: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.loading) item { LoadingState() }
        state.error?.let { item { ErrorState(it, viewModel::load) } }
        state.data?.let { product ->
            item { ProductImage(product, Modifier.fillMaxWidth().height(220.dp)) }
            if(product.images.isNotEmpty())item{LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(product.images,key={it.id}){image->AsyncImage(productPhotoModel(image.url),if(image.primary)"Imagen principal" else "Fotografía del producto",Modifier.size(200.dp),contentScale=ContentScale.Fit)}}}
            item { BrandHeader(product.name, "Vendido por ${product.sellerName}") }
            item { Text(cop(product.priceCop), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Sobre este producto", style = MaterialTheme.typography.titleMedium); Text(product.description) } }
            item { HorizontalDivider(); Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Detalles", style = MaterialTheme.typography.titleMedium); Text("Material: ${product.material}\nMedidas: ${product.dimensions}\nSKU: ${product.sku}\nExistencias: ${product.stock}") } }
            item { HorizontalDivider(); Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Entrega y cuidados", style = MaterialTheme.typography.titleMedium); Text("Instrucciones: ${product.instructions.ifBlank{"No informadas"}}\nPeso: ${product.weightGrams?.let{"$it g"}?:"No informado"}\nEntrega: ${product.deliveryDetails.ifBlank{"No informada"}}\nGarantía: ${product.warranty.ifBlank{"No informada"}}\nTérminos: ${product.terms.ifBlank{"No informados"}}\nActualizado: ${product.updatedAt.ifBlank{"No registrado"}}") } }
            val available = product.stock > 0
            item {
                Text("Categoría: ${category ?: "No disponible"}")
                VerdeStatusChip(if (available) "Disponible" else "Agotado", available)
                when (role) {
                    Role.BUYER -> Button(viewModel::add, Modifier.fillMaxWidth(), enabled = available && !action.loading) {
                        Text("Agregar al carrito")
                    }
                    null -> Button(login, Modifier.fillMaxWidth(), enabled = available) { Text("Inicia sesión como comprador") }
                    else -> Text("El carrito está disponible para cuentas compradoras.")
                }
            }
            if (action.loading) item { LoadingState() }
            action.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            action.data?.let { item { Text(it); TextButton(cart) { Text("Ver carrito") } } }
        }
    }
}
