package com.verdeamano.feature.commercial

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verdeamano.contracts.*
import com.verdeamano.core.design.*
import com.google.firebase.messaging.FirebaseMessaging
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap

fun paymentLabel(status:PaymentStatus)=when(status){PaymentStatus.PENDING->"Pendiente";PaymentStatus.APPROVED->"Aprobado en sandbox";PaymentStatus.REJECTED->"Rechazado";PaymentStatus.EXPIRED->"Vencido";PaymentStatus.COMMUNICATION_ERROR->"Error de conexión";PaymentStatus.UNKNOWN->"Resultado desconocido";PaymentStatus.RECONCILIATION_REQUIRED->"Requiere conciliación; no se ejecutará el pedido";PaymentStatus.CANCELLED->"Cancelado";PaymentStatus.REFUNDED->"Reembolsado en sandbox"}
fun caseLabel(status:CaseStatus)=when(status){CaseStatus.RECEIVED->"Recibida";CaseStatus.IN_REVIEW->"En revisión";CaseStatus.RESOLVED->"Resuelta";CaseStatus.CLOSED->"Cerrada"}
fun eventLabel(event:String)=when(event){"order.created"->"Pedido creado";"order.confirmed"->"Pedido confirmado";"order.preparing"->"Pedido en preparación";"order.shipped"->"Pedido enviado";"order.delivered"->"Pedido entregado";"order.cancelled"->"Pedido cancelado";"payment.updated"->"Estado del pago actualizado";"claim.received"->"Reclamación recibida";"claim.updated"->"Reclamación actualizada";else->"Actualización disponible"}
fun safeSandboxCheckout(value:String):Boolean {
    return runCatching{
        val uri=java.net.URI(value)
        val fields=uri.rawQuery.split('&').map{val pair=it.split('=',limit=2);require(pair.size==2);java.net.URLDecoder.decode(pair[0],"UTF-8") to java.net.URLDecoder.decode(pair[1],"UTF-8")}
        val keys=fields.filter{it.first=="public-key"}
        uri.scheme=="https"&&uri.host=="checkout.wompi.co"&&uri.path=="/p/"&&uri.userInfo==null&&uri.port==-1&&uri.fragment==null&&keys.size==1&&keys.single().second.startsWith("pub_test_")&&fields.map{it.first}.distinct().size==fields.size
    }.getOrDefault(false)
}
@Composable fun PaymentScreen(vm:CommercialViewModel,id:String) {
    val state by vm.state.collectAsStateWithLifecycle();val context=LocalContext.current
    var transaction by rememberSaveable(id){mutableStateOf("")};var browserError by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(id){vm.payment(id)}
    LaunchedEffect(state.payment?.transactionId){if(transaction.isBlank())transaction=state.payment?.transactionId.orEmpty()}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Pago Wompi Sandbox","Solo pruebas; nunca dinero real")}
        if(state.loading)item{LoadingState()};state.error?.let{item{ErrorState(it,{vm.payment(id)})}}
        state.payment?.let{p->
            item{Text(paymentLabel(p.status));Text("${cop(p.amountCop)} COP · ${p.reference}");Text("Vencimiento: ${p.expiresAt}");Text("Una redirección o pantalla del navegador no confirma el pago.")}
            if(p.status==PaymentStatus.PENDING)item{Button({vm.checkout(id)},enabled=!state.loading){Text("Preparar checkout sandbox")}}
            p.checkoutUrl?.let{url->item{Button({
                if(!safeSandboxCheckout(url))browserError="El enlace no corresponde al checkout sandbox autorizado."
                else try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))}catch(_:Exception){browserError="No hay un navegador disponible para abrir Wompi."}
            },enabled=!state.loading&&p.status==PaymentStatus.PENDING){Text("Abrir Wompi Sandbox")}}}
            item{OutlinedTextField(transaction,{transaction=it},label={Text("ID de transacción de Wompi")},modifier=Modifier.fillMaxWidth(),enabled=!state.loading);Text("Copia únicamente el ID de la transacción del checkout. Ktor comprobará importe, moneda y referencia.")}
            item{Button({vm.reconcile(id,transaction)},enabled=!state.loading&&transaction.matches(Regex("[A-Za-z0-9_-]{1,128}"))){Text("Verificar resultado con el servidor")}}
            p.transactionId?.let{item{Text("Transacción verificada: $it")}}
            item{OutlinedButton({vm.payment(id)},enabled=!state.loading){Text("Actualizar estado")}}
        }
        browserError?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
    }
}
@Composable fun ClaimsScreen(vm:CommercialViewModel,open:(String)->Unit) {
    val state by vm.state.collectAsStateWithLifecycle();LaunchedEffect(Unit){vm.claims()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Reclamaciones y PQR","Casos propios o asignados; revisión humana")}
        item{Text("Para radicar, abre un pedido y pulsa «Nueva reclamación». Retracto, devolución, reversión y PQR se revisan por separado. No se realizan reembolsos bancarios.")}
        if(state.loading)item{LoadingState()};state.error?.let{item{ErrorState(it,vm::claims)}}
        if(!state.loading&&state.error==null&&state.claims.isEmpty())item{EmptyState("No hay reclamaciones autorizadas.")}
        items(state.claims,key={it.id}){c->OutlinedCard(onClick={open(c.id)}){Column(Modifier.padding(16.dp)){Text(c.reference);Text("${c.kind} · ${caseLabel(c.status)}");Text(c.reason);Text(c.createdAt)}}}
        item{OutlinedButton(vm::claims,enabled=!state.loading){Text("Actualizar reclamaciones")}}
    }
}
@Composable fun NewClaimScreen(vm:CommercialViewModel,order:String,done:(String)->Unit) {
    val state by vm.state.collectAsStateWithLifecycle();val pending=vm.pendingClaim();var kind by remember{mutableStateOf(pending?.kind?:ClaimKind.PQR)};var reason by remember{mutableStateOf(pending?.reason.orEmpty())};var description by remember{mutableStateOf(pending?.description.orEmpty())}
    LaunchedEffect(state.created){state.created?.let(done)}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Nueva reclamación","Pedido $order")}
        item{Text("La radicación conserva fecha y trazabilidad. La elegibilidad y los plazos requieren revisión humana; no implica una devolución bancaria.")}
        pending?.let{item{Text("Hay una radicación pendiente de respuesta para el pedido ${it.orderId}. Reintenta los mismos datos para recuperar el resultado sin duplicar el caso.")}}
        items(ClaimKind.entries){k->FilterChip(kind==k,{kind=k},label={Text(k.name)},enabled=!state.loading)}
        item{
            OutlinedCard(Modifier.fillMaxWidth()){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text("Orientación legal informativa",style=MaterialTheme.typography.titleSmall)
                    val guidance=ClaimGuidance.text(kind)
                    Text(guidance,style=MaterialTheme.typography.bodySmall)
                    Text(ClaimGuidance.notice,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.outline)
                }
            }
        }
        item{OutlinedTextField(reason,{reason=it},label={Text("Motivo")},modifier=Modifier.fillMaxWidth(),enabled=!state.loading)}
        item{OutlinedTextField(description,{description=it},label={Text("Descripción detallada")},modifier=Modifier.fillMaxWidth(),enabled=!state.loading)}
        if(state.loading)item{LoadingState()};state.error?.let{item{ErrorState(it,{})}}
        item{Button({vm.create(order,kind,reason,description)},enabled=!state.loading&&reason.trim().length in 5..200&&description.trim().length in 10..4000){Text("Radicar reclamación")}}
    }
}
@Composable fun ClaimDetailScreen(vm:CommercialViewModel,id:String,role:Role) {
    val state by vm.state.collectAsStateWithLifecycle();val context=LocalContext.current;var note by remember{mutableStateOf("")};var assigned by remember{mutableStateOf("")};var due by remember{mutableStateOf("")};var localError by remember{mutableStateOf<String?>(null)}
    var refundDialog by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)try{val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytesLimited(8388608)}?:error("missing");vm.attach(bytes)}catch(_:Exception){localError="No pudimos leer la fotografía; debe ser JPEG/PNG y hasta 8 MB."}}
    LaunchedEffect(id){vm.claim(id,role==Role.ADMIN)};LaunchedEffect(state.claim?.version){assigned=state.claim?.assignedId.orEmpty();due=state.claim?.responseDueAt.orEmpty()}
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("claim-detail-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Detalle de reclamación","Radicado e historial persistentes")}
        if(state.loading)item{LoadingState()};state.error?.let{item{ErrorState(it,{vm.claim(id,role==Role.ADMIN)})}};localError?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
        state.claim?.let{c->
            item{Text(c.reference);Text("${c.kind} · ${caseLabel(c.status)}");Text(c.reason);Text(c.description);Text("Recibida: ${c.createdAt}");Text("Responsable: ${c.assignedName?:"Pendiente de asignación"}");Text("Plazo de respuesta: ${c.responseDueAt?:"Pendiente de revisión de política"}")}
            item{Text("Política: revisión humana pendiente de aprobación. No se decide elegibilidad jurídica automáticamente.");Text("Adjuntos: ${c.attachments.size}/6")}
            items(c.attachments){attachment->OutlinedButton({vm.photo(attachment)},enabled=!state.loading){Text("Consultar adjunto privado ${c.attachments.indexOf(attachment)+1}")}}
            state.attachment?.let{bytes->item{val bitmap=remember(bytes){android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)};bitmap?.let{Image(it.asImageBitmap(),"Adjunto de la reclamación",Modifier.fillMaxWidth().heightIn(max=300.dp))}}}
            if(role==Role.BUYER&&c.status in listOf(CaseStatus.RECEIVED,CaseStatus.IN_REVIEW))item{OutlinedButton({picker.launch("image/*")},enabled=!state.loading&&c.attachments.size<6){Text("Adjuntar fotografía privada")}}
            if(role!=Role.BUYER&&c.status!=CaseStatus.CLOSED){
                item{OutlinedTextField(note,{note=it},label={Text("Respuesta o resolución")},modifier=Modifier.fillMaxWidth(),enabled=!state.loading)}
                if(role==Role.ADMIN)item{
                    Text("Responsable autorizado")
                    FilterChip(assigned.isBlank(),{assigned=""},label={Text("Sin asignar")},enabled=!state.loading)
                    state.responsibles.forEach{user->FilterChip(assigned==user.id,{assigned=user.id},label={Text("${user.name} · ${if(user.role==Role.ADMIN)"Administrador"else"Vendedor del pedido"}")},enabled=!state.loading)}
                    OutlinedTextField(due,{due=it},label={Text("Plazo revisado ISO UTC (opcional)")},modifier=Modifier.fillMaxWidth());Text("El plazo requiere revisión humana de la política aplicable; no se calcula automáticamente.")
                }
                val targets=when(c.status){CaseStatus.RECEIVED->listOf(CaseStatus.IN_REVIEW);CaseStatus.IN_REVIEW->if(role==Role.ADMIN)listOf(CaseStatus.IN_REVIEW,CaseStatus.RESOLVED)else listOf(CaseStatus.IN_REVIEW);CaseStatus.RESOLVED->if(role==Role.ADMIN)listOf(CaseStatus.CLOSED)else emptyList();else->emptyList()}
                items(targets){target->Button({vm.update(target,note,assigned,due)},enabled=!state.loading&&note.trim().length in 5..4000){Text("Registrar: ${caseLabel(target)}")}}
            }
            if(role in listOf(Role.BUYER,Role.ADMIN))item{OutlinedButton(vm::refund,enabled=!state.loading){Text("Consultar reembolso sandbox")};state.refund?.let{Text("Reembolso de prueba: ${it.status} · ${cop(it.amountCop)} · ${it.reference}");if(it.status=="APPROVED"&&it.providerId==null)Text("Verificado mediante la transacción; Wompi no devolvió identificador de reembolso.")}}
            if(role==Role.ADMIN&&state.refund?.status=="RECONCILIATION_REQUIRED")item{OutlinedButton(vm::reconcileRefund,enabled=!state.loading){Text("Conciliar reembolso con Wompi")};Text("Consulta el resultado del envío anterior; no solicita otro reembolso.")}
            if(role==Role.ADMIN&&c.status==CaseStatus.RESOLVED)item{Button({refundDialog=true},enabled=!state.loading&&note.trim().length in 10..1000){Text("Autorizar reembolso sandbox del caso")}}
            item{Text("Historial inmutable")};items(c.history,key={it.id}){h->Text("${caseLabel(h.status)} · ${h.createdAt}\n${h.note}")}
        }
        item{OutlinedButton({vm.claim(id,role==Role.ADMIN)},enabled=!state.loading){Text("Actualizar caso")}}
    }
    if(refundDialog)AlertDialog(onDismissRequest={refundDialog=false},title={Text("¿Autorizar reembolso de prueba?")},text={Text("Se solicitará una sola vez el importe completo al sandbox para este caso resuelto. Si el resultado es incierto, quedará para conciliación sin reenviar. No se procesa dinero real.")},confirmButton={TextButton({refundDialog=false;vm.authorizeRefund(note)}){Text("Confirmar autorización sandbox")}},dismissButton={TextButton({refundDialog=false}){Text("Volver")}})
}
private fun java.io.InputStream.readBytesLimited(max:Int):ByteArray {val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val count=read(buffer);if(count<0)break;if(out.size()+count>max)error("too large");out.write(buffer,0,count)};return out.toByteArray()}
@Composable fun NotificationsScreen(vm:CommercialViewModel,open:(String,String)->Unit) {
    val state by vm.state.collectAsStateWithLifecycle();var pushError by remember{mutableStateOf<String?>(null)}
    fun register(){pushError=null;val expected=vm.currentUid();val messaging=FirebaseMessaging.getInstance();messaging.isAutoInitEnabled=true;messaging.token.addOnSuccessListener{vm.push(it,expected)}.addOnFailureListener{pushError="No se pudo registrar push. La bandeja interna permanece disponible."}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)register()else pushError="Push sin permiso; puedes consultar la bandeja interna."}
    LaunchedEffect(Unit){vm.notifications()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Notificaciones","Historial privado y persistente")}
        item{OutlinedButton({if(Build.VERSION.SDK_INT>=33)permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)else register()},enabled=!state.loading){Text("Activar avisos push para esta cuenta")};Text("FCM es gratuito; los avisos externos no sustituyen el historial del servidor.")}
        if(state.pushRegistered&&pushError==null)item{Text("Registro de avisos confirmado para esta cuenta. La recepción de una notificación todavía no se ha comprobado.")}
        pushError?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
        if(state.loading)item{LoadingState()};state.error?.let{item{ErrorState(it,vm::notifications)}}
        if(!state.loading&&state.error==null&&state.notifications.isEmpty())item{EmptyState("No hay notificaciones.")}
        items(state.notifications,key={it.id}){n->OutlinedCard(onClick={open("notification",n.id)}){Column(Modifier.padding(16.dp)){Text(if(n.read)"Leída"else"Nueva");Text(eventLabel(n.event));Text(n.createdAt);Text("Consultar detalle")}}}
        item{OutlinedButton(vm::notifications,enabled=!state.loading){Text("Actualizar bandeja")}}
        item{OutlinedButton(vm::reconcileReceipts,enabled=!state.loading){Text("Sincronizar acuses del dispositivo")};Text("Solo informa avisos recibidos por este dispositivo; no vuelve a enviarlos.")}
    }
}
@Composable fun NotificationDetailScreen(vm:CommercialViewModel,id:String,open:(String,String)->Unit){
    val state by vm.state.collectAsStateWithLifecycle();LaunchedEffect(id){vm.read(id)}
    val notification=state.notifications.firstOrNull{it.id==id}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Detalle de notificación","Información confirmada por el servidor")}
        if(state.loading)item{LoadingState()};state.error?.let{item{ErrorState(it,{vm.read(id)})}}
        notification?.let{n->item{Text(eventLabel(n.event));Text(n.createdAt);Text(if(n.read)"Leída"else"Sin leer");Button({open(n.resourceType,n.resourceId)},enabled=!state.loading){Text("Consultar recurso autorizado")}}}
        notification?.let{n->item{Text(if(n.receivedOnDeviceAt!=null)"Recepción reportada por el dispositivo" else "Sin acuse de recepción del dispositivo");n.delivery.forEach{d->Text("${d.channel}: ${deliveryLabel(d.status)} · intentos ${d.attempts}")}}}
        if(!state.loading&&state.error==null&&notification==null)item{EmptyState("La notificación no está disponible en esta cuenta.")}
    }
}
fun deliveryLabel(status:String)=when(status){"ACCEPTED_PROVIDER"->"Aceptado por el proveedor; recepción no confirmada";"ACCEPTED_PROVIDER_SMTP"->"Aceptado por SMTP del proveedor; entrega externa por confirmar";"ACCEPTED_LOCAL_SMTP"->"Aceptado por SMTP local; entrega externa no comprobada";"RECONCILIATION_REQUIRED"->"Resultado incierto; sin reenvío automático";"FAILED"->"No se completó el envío";"GENERATED_LOCAL"->"Mensaje preparado localmente";"SUBMITTING"->"Envío en curso";else->"Pendiente"}


@Composable fun CommercialPoliciesScreen(vm:CommercialViewModel){
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit){vm.adminPolicies()}
    LazyColumn(Modifier.fillMaxSize().testTag("admin-policies"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Propuestas comerciales","Consulta administrativa; ninguna propuesta está vigente")}
        item{Text("Pendientes de aprobación formal. Esta pantalla no activa reglas, plazos ni reembolsos y no modifica casos históricos.")}
        if(state.loading)item{LoadingState()}
        state.error?.let{item{ErrorState(it,vm::adminPolicies)}}
        if(!state.loading&&state.error==null&&state.adminPolicies.isEmpty())item{EmptyState("No hay propuestas disponibles.")}
        items(state.adminPolicies,key={it.policyId}){policy->
            OutlinedCard(Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(policy.title,style=MaterialTheme.typography.titleMedium)
                    Text("${policy.version} · Pendiente de aprobación")
                    Text(policy.termsSummary)
                    Text("Fundamento propuesto: ${policy.legalBasis}")
                    Text("Condiciones propuestas: ${policy.eligibilityConditions.joinToString("; ")}")
                    Text("Evidencia propuesta: ${policy.mandatoryEvidence.joinToString("; ")}")
                    Text("Ejercicio: ${policy.exerciseTermDescription}")
                    Text("Trámite: ${policy.executionTermDescription}")
                    Text("Causales propuestas: ${policy.rejectionCauses.joinToString("; ")}")
                }
            }
        }
    }
}
