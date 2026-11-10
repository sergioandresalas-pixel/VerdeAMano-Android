package com.verdeamano.feature.orders

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verdeamano.contracts.*
import com.verdeamano.core.design.*
import java.io.File
import java.util.UUID

fun OrderStatus.label():String=when(this){OrderStatus.PENDING->"Pendiente";OrderStatus.CONFIRMED->"Confirmado";OrderStatus.PREPARING->"En preparación";OrderStatus.SHIPPED->"Enviado";OrderStatus.DELIVERED->"Entregado";OrderStatus.CANCELLED->"Cancelado"}
private fun next(status:OrderStatus)=when(status){OrderStatus.PENDING->OrderStatus.CONFIRMED;OrderStatus.CONFIRMED->OrderStatus.PREPARING;OrderStatus.PREPARING->OrderStatus.SHIPPED;OrderStatus.SHIPPED->OrderStatus.DELIVERED;else->null}

@Composable fun CheckoutScreen(viewModel:CheckoutViewModel,addresses:()->Unit,result:(String)->Unit,suggestedAddress:()->Unit={}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissions=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()){grants->if(grants.values.any{it})viewModel.locate()else viewModel.locationDenied()}
    DisposableEffect(Unit){onDispose{viewModel.leaveLocation()}}
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME){viewModel.load()}
    LaunchedEffect(state.orderId){state.orderId?.let(result)}
    CheckoutContent(state,viewModel::load,viewModel::select,viewModel::preview,viewModel::confirm,addresses,{
        permissions.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION))
    },suggestedAddress)
}
@Composable fun CheckoutContent(state:CheckoutState,retry:()->Unit,select:(String)->Unit,preview:()->Unit,confirm:()->Unit,addresses:()->Unit,locate:()->Unit={},suggestedAddress:()->Unit={}) {
    var accepted by remember(state.quote?.id){mutableStateOf(false)}
    var confirmDialog by remember{mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("checkout-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Preparar pedido","Revisa la dirección y el resumen antes de confirmar")}
        item{Text("Modo de prueba sin cobros. Crear un pedido reserva existencias; el pago permanece pendiente. No es una transacción bancaria.")}
        if(state.loading)item{LoadingState()}
        state.error?.let{item{ErrorState(it,retry)}}
        if(!state.loading&&state.addresses.isEmpty())item{EmptyState("Necesitas una dirección completa para preparar el pedido.")}
        if(!state.uncertain)item{OutlinedButton(addresses,enabled=!state.loading){Text("Gestionar direcciones")}}
        if(!state.uncertain)item{OutlinedButton(locate,enabled=!state.loading&&!state.locating,modifier=Modifier.testTag("checkout-location")){Text(if(state.locating)"Consultando ubicación…"else"Usar mi ubicación para sugerir dirección")};Text("Ubicación puntual solo en checkout. No guardamos coordenadas ni seguimos tu ubicación. Puedes usar la dirección manual sin permiso.");state.locationMessage?.let{Text(it)};state.suggestion?.let{hint->Text("${hint.department} · ${hint.city}");if(hint.line.isNotBlank())Text(hint.line);OutlinedButton(suggestedAddress,enabled=!state.loading&&!state.locating){Text("Revisar sugerencia en una dirección nueva")}}}
        items(state.addresses,key={it.id}){address->
            OutlinedCard(onClick={select(address.id)},enabled=!state.loading&&!state.uncertain,modifier=Modifier.testTag("checkout-address-${address.id}")){
                Row(Modifier.padding(12.dp)){
                    RadioButton(state.selected==address.id,{select(address.id)},enabled=!state.loading&&!state.uncertain)
                    Column{Text(address.recipient);Text("${address.line}, ${address.city}");if(address.isDefault)Text("Predeterminada")}
                }
            }
        }
        if(!state.uncertain)item{Button(preview,enabled=!state.loading&&state.selected!=null,modifier=Modifier.testTag("checkout-preview")){Text("Revisar resumen del servidor")}}
        state.quote?.let{quote->
            item{Text("Resumen ${quote.id}");Text("Válido hasta ${quote.expiresAt}")}
            item{Text("Entregar a ${quote.delivery.recipient}: ${quote.delivery.line}, ${quote.delivery.city}")}
            items(quote.items,key={it.item.productId}){line->Column{Text(line.item.name);Text("Vendedor: ${line.item.sellerName}");Text("${line.item.quantity} × ${cop(line.item.unitPriceCop)} = ${cop(line.item.subtotalCop)}")}}
            item{Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Subtotal: ${cop(quote.subtotalCop)}");Text("Envío de prueba: ${cop(quote.shippingCop)}");Text("Impuestos adicionales de prueba: ${cop(quote.taxCop)}. No es una determinación fiscal.");HorizontalDivider();Text("Total del servidor: ${cop(quote.totalCop)}",Modifier.testTag("checkout-total"),style=MaterialTheme.typography.titleLarge)}}}
            if(state.uncertain)item{Text("Hay una confirmación pendiente de respuesta. Reintenta la misma solicitud para recuperar el resultado sin duplicar el pedido.")}
            else item{Row{Checkbox(accepted,{accepted=it},enabled=!state.loading,modifier=Modifier.testTag("checkout-accept"));Text("Acepto este resumen, la dirección y las condiciones del pedido de prueba.",Modifier.weight(1f))}}
            item{Button({if(state.uncertain)confirm() else confirmDialog=true},enabled=!state.loading&&(accepted||state.uncertain),modifier=Modifier.testTag("checkout-confirm")){Text(if(state.uncertain)"Reintentar misma confirmación" else "Confirmar pedido sin cobro")}}
        }
    }
    if(confirmDialog)AlertDialog(onDismissRequest={confirmDialog=false},title={Text("¿Crear este pedido?")},text={Text("Se reservarán las unidades y se conservará el resumen mostrado. No se realizará ningún cobro.")},confirmButton={TextButton({confirmDialog=false;confirm()},modifier=Modifier.testTag("checkout-final-confirm")){Text("Crear pedido")}},dismissButton={TextButton({confirmDialog=false}){Text("Volver al resumen")}})
}
@Composable fun OrdersScreen(viewModel:OrdersViewModel,open:(String)->Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit){viewModel.load()}
    LazyColumn(Modifier.fillMaxSize().testTag("orders-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Pedidos","Historial y seguimiento autorizado")}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilterChip(viewModel.filter==null,{viewModel.filter(null)},label={Text("Todos")},enabled=!state.loading)
            OrderStatus.entries.forEach{status->FilterChip(viewModel.filter==status,{viewModel.filter(status)},label={Text(status.label())},enabled=!state.loading)}
        }}
        if(state.loading)item{LoadingState()}
        state.error?.let{item{ErrorState(it,viewModel::load)}}
        if(!state.loading&&state.error==null&&state.data?.items.isNullOrEmpty())item{EmptyState("No hay pedidos con estos filtros.")}
        items(state.data?.items.orEmpty(),key={it.id}){order->OutlinedCard(onClick={open(order.id)},enabled=!state.loading,modifier=Modifier.testTag("order-${order.id}")){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Pedido ${order.id}",style=MaterialTheme.typography.titleMedium);VerdeStatusChip(order.status.label(),order.status==OrderStatus.CONFIRMED||order.status==OrderStatus.DELIVERED);Text("${order.itemCount} unidades · ${cop(order.totalCop)}");Text(order.createdAt)}
        }}
        state.data?.let{page->item{Text("Página ${page.page} · ${page.total} pedidos");Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedButton({viewModel.page(page.page-1)},enabled=!state.loading&&page.page>1){Text("Anterior")}
            OutlinedButton({viewModel.page(page.page+1)},enabled=!state.loading&&page.page*page.pageSize<page.total){Text("Siguiente")}
        }}}
    }
}
@Composable fun OrderDetailScreen(viewModel:OrdersViewModel,role:Role,payment:(String)->Unit={},claim:(String)->Unit={}) {
    val state by viewModel.detail.collectAsStateWithLifecycle();val order=state.data;val context=LocalContext.current
    var reason by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var tracking by remember{mutableStateOf("")}
    var cancellation by remember{mutableStateOf(false)};var transition by remember{mutableStateOf<Pair<OrderFulfillment,OrderStatus>?>(null)}
    var receiptFile by remember{mutableStateOf<File?>(null)};var receiptError by remember{mutableStateOf<String?>(null)}
    DisposableEffect(Unit){onDispose{receiptFile?.delete()}}
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("order-detail-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Detalle del pedido","Resumen histórico y seguimiento")}
        if(state.loading)item{LoadingState()}
        state.error?.let{item{ErrorState(it,{order?.id?.let(viewModel::select)})}}
        order?.let{o->
            item{Text("Pedido ${o.id}",Modifier.testTag("order-identifier"));Text("Estado: ${o.status.label()}",Modifier.testTag("order-status"));Text("Creado: ${o.createdAt}");Text("Actualizado: ${o.updatedAt}")}
            item{Text("Ambiente de prueba: no se procesa dinero real.");o.payment?.let{Text("Pago: ${runCatching{com.verdeamano.feature.commercial.paymentLabel(PaymentStatus.valueOf(it.status))}.getOrDefault("Resultado pendiente de revisión")}");Text("Referencia de prueba: ${it.reference}");if(it.mode=="WOMPI_SANDBOX")OutlinedButton({payment(o.id)}){Text("Consultar pago / Wompi Sandbox")}};if(role==Role.BUYER)OutlinedButton({claim(o.id)}){Text("Nueva reclamación")};if(!o.canFulfill)Text("El pago aún no permite continuar con la preparación de este pedido.")}
            items(o.items,key={it.productId}){line->Column{Text(line.name);Text("SKU: ${line.sku} · Vendedor: ${line.sellerName}");Text("${line.quantity} × ${cop(line.unitPriceCop)} = ${cop(line.subtotalCop)}")}}
            item{Text(if(role==Role.SELLER)"Total de tus productos: ${cop(o.totalCop)}" else "Total histórico: ${cop(o.totalCop)}",Modifier.testTag("order-total"),style=MaterialTheme.typography.titleLarge)}
            o.delivery?.let{a->item{Text("Dirección histórica de entrega");Text("${a.recipient} · ${a.phone}");Text("${a.line}, ${a.city}, ${a.department}");if(a.instructions.isNotBlank())Text(a.instructions)}}
            items(o.fulfillments,key={it.sellerId}){f->Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("${f.sellerName}: ${f.status.label()}");if(f.tracking.isNotBlank())Text("Seguimiento: ${f.tracking}")
                if(role in listOf(Role.SELLER,Role.ADMIN))next(f.status)?.let{target->
                    OutlinedTextField(note,{note=it},label={Text("Nota de seguimiento")},enabled=!state.loading,modifier=Modifier.fillMaxWidth())
                    if(target==OrderStatus.SHIPPED)OutlinedTextField(tracking,{tracking=it},label={Text("Referencia logística opcional")},enabled=!state.loading,modifier=Modifier.fillMaxWidth())
                    Button({transition=f to target},enabled=!state.loading&&o.canFulfill,modifier=Modifier.testTag("order-transition-${f.sellerId}")){Text("Marcar ${target.label()}")}
                }
            }}
            if(role in listOf(Role.BUYER,Role.ADMIN)&&OrderRules.canCancel(o.fulfillments.map{it.status}))item{
                OutlinedTextField(reason,{reason=it},label={Text("Motivo de cancelación")},enabled=!state.loading,modifier=Modifier.fillMaxWidth().testTag("order-cancel-reason"))
                OutlinedButton({cancellation=true},enabled=!state.loading&&reason.trim().length in 5..400,modifier=Modifier.testTag("order-cancel")){Text("Cancelar pedido y liberar unidades")}
            }
            item{Text("Historial",style=MaterialTheme.typography.titleMedium)}
            items(o.history,key={it.id}){event->Column{Text("${event.status.label()} · ${event.createdAt}");Text(event.note.ifBlank{"Estado registrado: ${event.status.label()}"})}}
            item{OutlinedButton({
                val directory=File(context.cacheDir,"exports").apply{mkdirs()};val file=File(directory,"pedido-${UUID.randomUUID()}.txt")
                try{file.writeText(receipt(o));receiptFile?.delete();receiptFile=file;receiptError=null}catch(_:Exception){file.delete();receiptError="No se pudo generar el comprobante del pedido."}
            },enabled=!state.loading,modifier=Modifier.testTag("order-receipt")){Text("Generar comprobante del pedido")}}
            receiptFile?.let{file->item{OutlinedButton({
                try{val uri=FileProvider.getUriForFile(context,"${context.packageName}.exports",file);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Compartir comprobante del pedido"))}catch(_:Exception){receiptError="No hay una aplicación disponible para compartir el comprobante."}
            }){Text("Compartir comprobante; no es factura ni pago")}}}
            receiptError?.let{message->item{Text(message,color=MaterialTheme.colorScheme.error)}}
        }
    }
    if(cancellation)AlertDialog(onDismissRequest={cancellation=false},title={Text("¿Cancelar el pedido completo?")},text={Text("Se liberarán sus unidades una sola vez. Un pago sandbox aprobado requerirá conciliación; la cancelación no envía un reembolso automático.")},confirmButton={TextButton({cancellation=false;viewModel.cancel(reason)},modifier=Modifier.testTag("order-cancel-confirm")){Text("Confirmar cancelación")}},dismissButton={TextButton({cancellation=false}){Text("Conservar pedido")}})
    transition?.let{(f,status)->AlertDialog(onDismissRequest={transition=null},title={Text("¿Marcar ${status.label()}?")},text={Text("Se registrará el cambio de ${f.sellerName} en el historial. El pago seguirá siendo una prueba sin cobro.")},confirmButton={TextButton({transition=null;viewModel.transition(f,status,note,tracking)},modifier=Modifier.testTag("order-transition-confirm")){Text("Confirmar estado")}},dismissButton={TextButton({transition=null}){Text("Volver")}})}
}
private fun receipt(order:Order)=buildString{
    appendLine("VERDE A MANO — COMPROBANTE DE PEDIDO DE PRUEBA")
    appendLine("No es factura ni comprobante de pago. No hubo transacción bancaria.")
    appendLine("Pedido: ${order.id} — ${order.status.label()} — ${order.createdAt}")
    order.items.forEach{appendLine("${it.sku} ${it.name}: ${it.quantity} x ${it.unitPriceCop} COP = ${it.subtotalCop} COP (${it.sellerName})")}
    appendLine("Total histórico: ${order.totalCop} ${order.currency}")
    order.delivery?.let{appendLine("Entrega: ${it.recipient}, ${it.line}, ${it.city}, ${it.department}")}
}
