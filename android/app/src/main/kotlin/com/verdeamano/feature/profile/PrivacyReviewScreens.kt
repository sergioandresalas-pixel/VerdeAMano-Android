package com.verdeamano.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verdeamano.contracts.*
import com.verdeamano.core.design.*

fun String.privacyStatusLabel()=when(this){"OPEN"->"Pendiente de revisión";"IN_REVIEW"->"En revisión";"RESOLVED"->"Respondida";"CLOSED"->"Cerrada";else->"Estado desconocido"}
@Composable fun PrivacyManagementScreen(vm:PrivacyReviewViewModel,open:(String)->Unit){
    val state by vm.page.collectAsStateWithLifecycle();var status by remember{mutableStateOf<PrivacyStatus?>(null)}
    LaunchedEffect(Unit){vm.list(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{BrandHeader("Solicitudes de privacidad","Revisión humana y conservación del historial")}
        item{FlowRow{FilterChip(status==null,{status=null},label={Text("Todas")});PrivacyStatus.entries.forEach{value->FilterChip(status==value,{status=value},label={Text(value.name.privacyStatusLabel())})}}}
        item{Button({vm.list(status)},enabled=!state.loading){Text("Aplicar filtros")}}
        if(state.loading)item{LoadingState()};state.error?.let{item{ErrorState(it){vm.list(status)}}}
        state.data?.let{data->
            if(data.items.isEmpty())item{EmptyState("No hay solicitudes con estos filtros.")}
            items(data.items,key={it.id}){request->OutlinedCard(onClick={open(request.id)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(request.ownerName.orEmpty(),style=MaterialTheme.typography.titleMedium);VerdeStatusChip(request.status.privacyStatusLabel(),request.status=="RESOLVED"||request.status=="CLOSED");Text(request.note)}}}
            item{Text("Página ${data.page} · ${data.total} solicitudes");Row{TextButton({vm.next(-1)},enabled=data.page>1&&!state.loading){Text("Anterior")};TextButton({vm.next(1)},enabled=data.page*data.pageSize<data.total&&!state.loading){Text("Siguiente")}}}
        }
    }
}
@Composable fun PrivacyDetailScreen(vm:PrivacyReviewViewModel,id:String,admin:Boolean){
    val state by vm.detail.collectAsStateWithLifecycle();val people by vm.responsibles.collectAsStateWithLifecycle()
    val evaluation by vm.retentionEvaluation.collectAsStateWithLifecycle()
    LaunchedEffect(id){vm.select(id,admin)}
    var confirm by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        BrandHeader("Seguimiento de privacidad","La respuesta no elimina ni anonimiza datos automáticamente")
        if(state.loading)LoadingState();state.error?.let{ErrorState(it){vm.select(id,admin)}}
        state.data?.let{request->
            Text(request.status.privacyStatusLabel());Text(request.note);Text(request.createdAt)
            if(request.response.isNotBlank())Text("Respuesta: ${request.response}")
            request.assignedName?.let{Text("Responsable: $it")}
            if(request.policyNote.isNotBlank())Text("Decisión de conservación: ${request.policyNote}")
            Text("Historial de respuestas");request.history.forEach{Text("${it.createdAt} · ${it.status.privacyStatusLabel()}\n${it.response}")}
            if(admin){
                if(evaluation.loading)LoadingState()
                evaluation.error?.let{ErrorState(it,vm::retryEvaluation)}
                evaluation.data?.let{eval->
                    OutlinedCard(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                            Text("Diagnóstico técnico de conservación",style=MaterialTheme.typography.titleSmall)
                            val vLabel=when(eval.verdict){
                                RetentionEvaluationVerdict.REQUIRES_REVISION->"Requiere revisión: operaciones activas en curso"
                                RetentionEvaluationVerdict.RETENCION_PARCIAL->"Historial presente: conservación pendiente de revisión"
                                RetentionEvaluationVerdict.SIN_BLOQUEOS_IDENTIFICADOS->"Sin bloqueos comerciales locales identificados"
                            }
                            Text("Veredicto: $vLabel",color=MaterialTheme.colorScheme.primary)
                            Text(eval.summaryJustification,style=MaterialTheme.typography.bodySmall)
                            eval.categories.forEach{cat->
                                val recText=cat.recordCount?.let{"$it registros consultados"}?:if(cat.status==EvaluationStatus.EXTERNAL_VERIFICATION_REQUIRED)"Requiere verificación externa" else "No evaluado"
                                Text("• ${cat.category.name}: $recText · ${cat.obligationStatus.name}\n  ${cat.observation}",style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    TextButton(vm::retryEvaluation,enabled=!evaluation.loading){Text("Actualizar diagnóstico")}
                }
            }
            if(admin&&request.status!="CLOSED"){
                val transitions=when(request.status){"OPEN"->listOf(PrivacyStatus.IN_REVIEW);"IN_REVIEW"->listOf(PrivacyStatus.IN_REVIEW,PrivacyStatus.RESOLVED);"RESOLVED"->listOf(PrivacyStatus.CLOSED);else->emptyList()}
                var next by remember(request.version){mutableStateOf(transitions.first())};var answer by remember(request.version){mutableStateOf(request.response)};var policy by remember(request.version){mutableStateOf(request.policyNote)};var assigned by remember(request.version){mutableStateOf(request.assignedId)}
                Text("Responder solicitud de ${request.ownerName}")
                FlowRow{transitions.forEach{FilterChip(next==it,{next=it},label={Text(it.name.privacyStatusLabel())},enabled=!state.loading)}}
                OutlinedTextField(answer,{answer=it},label={Text("Respuesta al usuario")},modifier=Modifier.fillMaxWidth(),enabled=!state.loading)
                OutlinedTextField(policy,{policy=it},label={Text("Decisión manual de conservación y su fundamento")},modifier=Modifier.fillMaxWidth(),enabled=!state.loading)
                Text("No hay plazos ni borrado automatizados. Registra únicamente decisiones autorizadas y acciones realmente ejecutadas.")
                FlowRow{FilterChip(assigned==null,{assigned=null},label={Text("Sin asignar")},enabled=!state.loading);people.forEach{person->FilterChip(assigned==person.id,{assigned=person.id},label={Text(person.name)},enabled=!state.loading)}}
                Button({confirm=true},enabled=!state.loading){Text("Registrar respuesta")}
                if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Confirmar respuesta")},text={Text("Se registrará ${next.name.privacyStatusLabel()} y una respuesta permanente. Los datos originales se conservarán.")},confirmButton={TextButton({confirm=false;vm.review(next,answer,policy,assigned)}){Text("Confirmar revisión")}},dismissButton={TextButton({confirm=false}){Text("Cancelar")}})
            }
        }
    }
}
