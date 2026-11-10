package com.verdeamano.feature.seller

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verdeamano.contracts.ProductInput
import com.verdeamano.contracts.PublicationState
import com.verdeamano.core.design.*

@Composable fun ProductEditorScreen(viewModel: ProductEditorViewModel, saved: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.data?.saved) { if (state.data?.saved == true) saved() }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandHeader(if (state.data?.product == null) "Crear producto" else "Editar producto", "Completa la ficha de tu producto")
        if (state.loading) LoadingState()
        state.error?.let { ErrorState(it, viewModel::load) }
        state.data?.let { data ->
            val product = data.product
            var name by rememberSaveable(product?.id) { mutableStateOf(product?.name.orEmpty()) }
            var sku by rememberSaveable(product?.id) { mutableStateOf(product?.sku.orEmpty()) }
            var description by rememberSaveable(product?.id) { mutableStateOf(product?.description.orEmpty()) }
            var material by rememberSaveable(product?.id) { mutableStateOf(product?.material.orEmpty()) }
            var dimensions by rememberSaveable(product?.id) { mutableStateOf(product?.dimensions.orEmpty()) }
            var price by rememberSaveable(product?.id) { mutableStateOf(product?.priceCop?.toString().orEmpty()) }
            var stock by rememberSaveable(product?.id) { mutableStateOf(product?.stock?.toString() ?: "0") }
            var instructions by rememberSaveable(product?.id){mutableStateOf(product?.instructions.orEmpty())}
            var weight by rememberSaveable(product?.id){mutableStateOf(product?.weightGrams?.toString().orEmpty())}
            var delivery by rememberSaveable(product?.id){mutableStateOf(product?.deliveryDetails.orEmpty())}
            var warranty by rememberSaveable(product?.id){mutableStateOf(product?.warranty.orEmpty())}
            var terms by rememberSaveable(product?.id){mutableStateOf(product?.terms.orEmpty())}
            var category by rememberSaveable(product?.id) { mutableStateOf(product?.categoryId ?: data.categories.firstOrNull()?.id.orEmpty()) }
            val enabled = !state.loading && product?.active != false && product?.publicationState!=PublicationState.SUSPENDED
            @Composable fun field(label: String, value: String, set: (String) -> Unit, numeric: Boolean = false, single: Boolean = true) {
                OutlinedTextField(value, set, Modifier.fillMaxWidth(), label = { Text(label) }, enabled = enabled, singleLine = single, keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text))
            }
            Text("Información básica", style = MaterialTheme.typography.titleLarge)
            field("Nombre", name, { name = it.take(120) })
            field("SKU", sku, { sku = it.take(40) })
            Text("Categoría", style = MaterialTheme.typography.titleSmall)
            data.categories.forEach { value -> FilterChip(category == value.id, { category = value.id }, enabled = enabled, label = { Text(value.name) }) }
            field("Descripción", description, { description = it.take(2000) }, single = false)
            field("Material", material, { material = it.take(120) })
            field("Medidas", dimensions, { dimensions = it.take(120) })
            HorizontalDivider()
            Text("Precio e inventario", style = MaterialTheme.typography.titleLarge)
            field("Precio COP (entero)", price, { price = it.take(10) }, numeric = true)
            field("Existencias", stock, { stock = it.take(6) }, numeric = true)
            HorizontalDivider()
            Text("Cuidados y condiciones", style = MaterialTheme.typography.titleLarge)
            field("Instrucciones",instructions,{instructions=it.take(2000)},single=false)
            field("Peso en gramos (opcional)",weight,{weight=it.take(7)},numeric=true)
            field("Detalles de entrega",delivery,{delivery=it.take(2000)},single=false)
            field("Garantía",warranty,{warranty=it.take(2000)},single=false)
            field("Términos",terms,{terms=it.take(2000)},single=false)
            Text("Guarda primero el borrador y gestiona sus fotografías desde Mis productos. Las publicaciones nuevas y los cambios de contenido requieren revisión.", style = MaterialTheme.typography.bodySmall)
            if (product?.active == false) Text("Reactiva este producto desde Mis productos antes de editarlo.")
            Button({ viewModel.save(ProductInput(category, sku, name, description, material, dimensions, price.toLongOrNull() ?: 0, stock.toIntOrNull() ?: -1,null,
                instructions=instructions,weightGrams=if(weight.isBlank())null else weight.toIntOrNull() ?: -1,deliveryDetails=delivery,warranty=warranty,terms=terms)) }, Modifier.fillMaxWidth(), enabled = enabled && category.isNotBlank()) { Text("Guardar producto") }
        }
    }
}
