package com.verdeamano.feature.seller

import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import com.verdeamano.core.design.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class InventoryData(val product:Product,val history:List<InventoryRecord>)
@HiltViewModel class InventoryViewModel @Inject constructor(private val repository:PublicationRepository,handle:SavedStateHandle):ViewModel(){
    private val id:String=requireNotNull(handle["id"]);private val admin=handle.get<String>("scope")=="admin"
    private val mutable=MutableStateFlow(UiState<InventoryData>());val state=mutable.asStateFlow();private var retry:Pair<String,String>?=null;private var retryVersion:Int?=null
    init{load()}
    fun load()=operate{InventoryData(repository.managed(id,admin),repository.history(id,admin))}
    fun adjust(delta:String,reason:String){
        val before=mutable.value.data?:return;val units=delta.toIntOrNull()
        if(units==null||units==0||reason.trim().length !in 5..500){mutable.value=UiState(before,error="Indica un ajuste entero distinto de cero y un motivo de 5 a 500 caracteres.");return}
        // A read refresh after an uncertain write must not turn its retry into a second stock adjustment.
        val intent="$id:$units:$reason";val key=retry?.takeIf{it.first==intent}?.second?:UUID.randomUUID().toString().also{retry=intent to it;retryVersion=before.product.version}
        operate{repository.inventory(before.product,InventoryAdjustment(units,reason,requireNotNull(retryVersion),key),admin);InventoryData(repository.managed(id,admin),repository.history(id,admin)).also{retry=null;retryVersion=null}}
    }
    private fun operate(action:suspend()->InventoryData){if(mutable.value.loading)return;val before=mutable.value.data;mutable.value=UiState(before,loading=true);viewModelScope.launch{try{mutable.value=UiState(action())}catch(e:Exception){mutable.value=UiState(before,error=e.userMessage())}}}
}
@Composable fun InventoryScreen(viewModel:InventoryViewModel){
    val state by viewModel.state.collectAsStateWithLifecycle();var delta by remember{mutableStateOf("")};var reason by remember{mutableStateOf("")}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        BrandHeader("Inventario","Registra cambios y consulta su historial")
        if(state.loading)LoadingState();state.error?.let{ErrorState(it,viewModel::load)}
        state.data?.let{data->Text(data.product.name,style=MaterialTheme.typography.titleLarge);MetricCard("${data.product.stock}","Unidades disponibles",Modifier.fillMaxWidth())
            OutlinedTextField(delta,{delta=it},label={Text("Ajuste de unidades (+entrada / -salida)")},singleLine=true,enabled=!state.loading)
            OutlinedTextField(reason,{reason=it.take(500)},label={Text("Motivo del ajuste")},enabled=!state.loading)
            Button({viewModel.adjust(delta,reason)},enabled=!state.loading&&data.product.active&&data.product.publicationState!=PublicationState.SUSPENDED){Text("Guardar ajuste")}
            Text("Historial de inventario");if(data.history.isEmpty())EmptyState("Todavía no hay ajustes registrados.")
            data.history.forEach{record->OutlinedCard{Column(Modifier.padding(12.dp)){Text("${record.before} → ${record.after} unidades");Text(record.reason);Text(record.createdAt)}}}
        }
    }
}
