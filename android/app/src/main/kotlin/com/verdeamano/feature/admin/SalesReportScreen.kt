package com.verdeamano.feature.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verdeamano.contracts.OrderStatus
import com.verdeamano.contracts.OrderSummary
import com.verdeamano.core.design.*
import com.verdeamano.feature.orders.OrdersViewModel
import com.verdeamano.feature.orders.label
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal fun ordersInPeriod(orders: List<OrderSummary>, days: Int, now: Instant): List<OrderSummary> {
    if (days == 0) return orders
    val start = now.minus(days.toLong(), ChronoUnit.DAYS)
    return orders.filter { order ->
        runCatching { Instant.parse(order.createdAt) }.getOrNull()?.let { it in start..now } == true
    }
}

@Composable
fun SalesReportScreen(viewModel: OrdersViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableIntStateOf(0) }
    val page = state.data?.takeIf { !state.loading && state.error == null }
    val visibleOrders = remember(page, days) { ordersInPeriod(page?.items.orEmpty(), days, Instant.now()) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            BotanicalHero("Reporte de ventas", "Consulta administrativa de pedidos")
            Text("Los valores corresponden a pedidos. Los ingresos netos no están disponibles en este listado.")
        }
        item {
            Text("Estado del pedido", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf(null) + OrderStatus.entries).forEach { status ->
                    FilterChip(viewModel.filter == status, { viewModel.filter(status) },
                        label = { Text(status?.label() ?: "Todos") }, enabled = !state.loading)
                }
            }
        }
        if (state.loading) item { LoadingState() }
        state.error?.let { item { ErrorState(it, viewModel::load) } }
        if (page != null) {
            item {
                MetricCard(page.total.toString(), "Pedidos para el estado seleccionado", Modifier.fillMaxWidth())
                Text("Periodo de la página actual", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Todas las fechas", 7 to "Últimos 7 días", 30 to "Últimos 30 días").forEach { (value, label) ->
                        FilterChip(days == value, { days = value }, label = { Text(label) })
                    }
                }
            }
            if (visibleOrders.isEmpty()) item {
                EmptyState(if (page.total == 0) "No hay pedidos para el estado seleccionado."
                    else "No hay pedidos de esta página en el periodo seleccionado.")
            }
            items(visibleOrders, key = { it.id }) { order ->
                val date = runCatching { Instant.parse(order.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() }
                    .getOrNull()?.toString() ?: "Fecha no disponible"
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pedido ${order.id.take(8)}", style = MaterialTheme.typography.titleMedium)
                        Text(date)
                        VerdeStatusChip(order.status.label(), order.status == OrderStatus.DELIVERED)
                        Text("Valor del pedido: ${cop(order.totalCop)}")
                    }
                }
            }
            item {
                Text("Página ${page.page}")
                Row {
                    TextButton({ viewModel.page(page.page - 1) }, enabled = page.page > 1) { Text("Anterior") }
                    TextButton({ viewModel.page(page.page + 1) }, enabled = page.page * page.pageSize < page.total) { Text("Siguiente") }
                }
            }
        }
    }
}
