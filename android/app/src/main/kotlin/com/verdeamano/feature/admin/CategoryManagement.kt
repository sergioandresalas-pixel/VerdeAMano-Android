package com.verdeamano.feature.admin

import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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

@HiltViewModel class CategoryManagementViewModel @Inject constructor(private val repository:PublicationRepository):ViewModel(){
    private val mutable=MutableStateFlow(UiState<List<Category>>());val state=mutable.asStateFlow()
    private val notice=MutableStateFlow<String?>(null);val message=notice.asStateFlow();private var retry:Pair<String,String>?=null
    init{load()}
    fun load()=operate{repository.categories()}
    fun save(category:Category?,name:String,active:Boolean,order:String){
        val position=order.toIntOrNull()
        if(name.trim().length !in 2..80||position==null||position !in 0..10000){mutable.value=UiState(mutable.value.data,error="Indica un nombre de 2 a 80 caracteres y un orden de 0 a 10000.");return}
        val intent="${category?.id}:${category?.version}:$name:$active:$position"
        val key=retry?.takeIf{it.first==intent}?.second?:UUID.randomUUID().toString().also{retry=intent to it}
        operate(true){repository.category(category?.id,CategoryInput(name,active,position,category?.version,key));repository.categories().also{retry=null}}
    }
    private fun operate(saved:Boolean=false,action:suspend()->List<Category>){if(mutable.value.loading)return;val before=mutable.value.data;mutable.value=UiState(before,loading=true);notice.value=null;viewModelScope.launch{try{mutable.value=UiState(action());if(saved)notice.value="Categoría guardada."}catch(e:Exception){mutable.value=UiState(before,error=e.userMessage())}}}
}
@Composable fun CategoryManagementScreen(viewModel:CategoryManagementViewModel){
    val state by viewModel.state.collectAsStateWithLifecycle();val message by viewModel.message.collectAsStateWithLifecycle()
    var selected by remember{mutableStateOf<Category?>(null)};var name by remember{mutableStateOf("")};var active by remember{mutableStateOf(true)};var order by remember{mutableStateOf("0")};var changing by remember{mutableStateOf<Category?>(null)}
    LaunchedEffect(message){if(message=="Categoría guardada."){selected=null;name="";active=true;order="0"}}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item(key="header"){BrandHeader("Categorías","Administración global; los productos e históricos se conservan")}
        if(state.loading)item(key="loading"){LoadingState()};state.error?.let{item(key="error"){ErrorState(it,viewModel::load)}};message?.let{item(key="notice"){Text(it)}}
        item(key="form"){Surface(color=MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(if(selected==null)"Crear categoría" else "Editar categoría",style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(name,{name=it.take(80)},label={Text("Nombre de categoría")},modifier=Modifier.fillMaxWidth(),enabled=!state.loading)
            OutlinedTextField(order,{order=it.take(5)},label={Text("Orden")},enabled=!state.loading,singleLine=true)
            Row{Checkbox(active,{active=it},enabled=!state.loading);Text("Categoría activa")}
            Button({viewModel.save(selected,name,active,order)},enabled=!state.loading){Text("Guardar categoría")}
            if(selected!=null)TextButton({selected=null;name="";active=true;order="0"}){Text("Cancelar edición")}
        }}}
        if(!state.loading&&state.data.isNullOrEmpty())item{EmptyState("Todavía no hay categorías.")}
        items(state.data.orEmpty(),key={it.id}){category->OutlinedCard{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(category.name,style=MaterialTheme.typography.titleMedium);VerdeStatusChip("${if(category.active)"Activa" else "Inactiva"} · orden ${category.position}",category.active)
            OutlinedButton({selected=category;name=category.name;active=category.active;order=category.position.toString()},enabled=!state.loading){Text("Editar ${category.name}")}
            TextButton({changing=category},enabled=!state.loading){Text(if(category.active)"Desactivar ${category.name}" else "Reactivar ${category.name}")}
        }}}
    }
    changing?.let{category->AlertDialog(onDismissRequest={changing=null},title={Text(if(category.active)"Desactivar categoría" else "Reactivar categoría")},text={Text("No se eliminarán productos. Una categoría inactiva oculta sus artículos del catálogo.")},confirmButton={TextButton({changing=null;viewModel.save(category,category.name,!category.active,category.position.toString())}){Text("Confirmar")}},dismissButton={TextButton({changing=null}){Text("Cancelar")}})}
}
