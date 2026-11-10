package com.verdeamano.feature.admin

import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import com.verdeamano.core.design.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SupervisionData(val page:ProductPage,val categories:List<Category>,val sellers:List<UserSummary>)
@HiltViewModel class ProductSupervisionViewModel @Inject constructor(private val repository:PublicationRepository):ViewModel(){
    private val mutable=MutableStateFlow(UiState<SupervisionData>());val state=mutable.asStateFlow()
    private val selection=MutableStateFlow(UiState<Product>());val selected=selection.asStateFlow()
    private val notice=MutableStateFlow<String?>(null);val message=notice.asStateFlow()
    private var q:String?=null;private var category:String?=null;private var seller:String?=null;private var publication:PublicationState?=null;private var page=1
    private var retry:Pair<String,String>?=null
    init{load()}
    fun filter(query:String,category:String?,seller:String?,publication:PublicationState?){q=query;this.category=category;this.seller=seller;this.publication=publication;page=1;load()}
    fun next(delta:Int){page=maxOf(1,page+delta);load()}
    fun load(){if(mutable.value.loading)return;val before=mutable.value.data;mutable.value=UiState(before,loading=true);viewModelScope.launch{try{mutable.value=UiState(SupervisionData(repository.supervision(q,category,seller,publication,page),repository.categories(),repository.sellers()))}catch(e:Exception){mutable.value=UiState(before,error=e.userMessage())}}}
    fun select(id:String){selection.value=UiState(loading=true);viewModelScope.launch{try{selection.value=UiState(repository.managed(id,true))}catch(e:Exception){selection.value=UiState(error=e.userMessage())}}}
    fun moderate(state:PublicationState,reason:String,featured:Boolean){
        val p=selection.value.data?:return;if(selection.value.loading)return
        if(reason.trim().length !in 5..2000){selection.value=UiState(p,error="Registra un motivo de 5 a 2000 caracteres.");return}
        val intent="${p.id}:${p.version}:$state:$reason:$featured";val key=retry?.takeIf{it.first==intent}?.second?:UUID.randomUUID().toString().also{retry=intent to it}
        selection.value=UiState(p,loading=true);notice.value=null
        viewModelScope.launch{try{selection.value=UiState(repository.publication(p,PublicationInput(state,p.version,key,reason,featured),true));retry=null;notice.value="Revisión registrada."}catch(e:Exception){selection.value=UiState(p,error=e.userMessage())}}
    }
}
@Composable fun ProductSupervisionScreen(viewModel:ProductSupervisionViewModel,detail:(String)->Unit){
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle();var q by remember{mutableStateOf("")};var category by remember{mutableStateOf<String?>(null)};var seller by remember{mutableStateOf<String?>(null)};var publication by remember{mutableStateOf<PublicationState?>(null)}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item(key="header"){BrandHeader("Supervisar productos","Solo las publicaciones aprobadas se muestran al comprador")}
        if(state.loading)item(key="loading"){LoadingState()};state.error?.let{item(key="error"){ErrorState(it,viewModel::load)}}
        item(key="search"){OutlinedTextField(q,{q=it.take(120)},label={Text("Buscar producto")},modifier=Modifier.fillMaxWidth())}
        item(key="filters"){
            Text("Categoría");FlowRow{FilterChip(category==null,{category=null},label={Text("Todas")});state.data?.categories.orEmpty().forEach{c->FilterChip(category==c.id,{category=c.id},label={Text(c.name)})}}
            Text("Vendedor");FlowRow{FilterChip(seller==null,{seller=null},label={Text("Todos")});state.data?.sellers.orEmpty().forEach{u->FilterChip(seller==u.user.id,{seller=u.user.id},label={Text(u.user.name)})}}
            Text("Estado");FlowRow{FilterChip(publication==null,{publication=null},label={Text("Todos")});PublicationState.entries.forEach{s->FilterChip(publication==s,{publication=s},label={Text(s.label())})}}
            Button({viewModel.filter(q,category,seller,publication)},enabled=!state.loading){Text("Aplicar filtros")}
        }
        if(!state.loading&&state.data?.page?.items.isNullOrEmpty())item{EmptyState("No hay publicaciones con estos filtros.")}
        items(state.data?.page?.items.orEmpty(),key={it.id}){p->ProductCard(p,"Revisar publicación"){detail(p.id)}}
        state.data?.page?.let{page->item{Text("Página ${page.page} · ${page.total} productos");Row{TextButton({viewModel.next(-1)},enabled=!state.loading&&page.page>1){Text("Anterior")};TextButton({viewModel.next(1)},enabled=!state.loading&&page.page*page.pageSize<page.total){Text("Siguiente")}}}}
    }
}
@Composable fun ProductReviewScreen(viewModel:ProductSupervisionViewModel,id:String,inventory:(String)->Unit){
    val state by viewModel.selected.collectAsStateWithLifecycle();val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(id){viewModel.select(id)};var reason by remember{mutableStateOf("")};var action by remember{mutableStateOf<PublicationState?>(null)}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        BrandHeader("Revisión de publicación","Cada decisión exige motivo y queda auditada")
        if(state.loading)LoadingState();state.error?.let{ErrorState(it){viewModel.select(id)}};message?.let{Text(it)}
        state.data?.let{p->
            var featured by remember(p.version){mutableStateOf(p.featured)}
            Surface(color=MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.medium){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(p.name,style=MaterialTheme.typography.titleLarge)
                Text("Vendedor: ${p.sellerName}")
                VerdeStatusChip("${p.sku} · ${p.publicationState.label()}",p.publicationState==PublicationState.APPROVED)
                Text(cop(p.priceCop),style=MaterialTheme.typography.titleMedium)
                Text("Existencias: ${p.stock}")
                Text(p.description)
            }}
            Text("Ficha técnica",style=MaterialTheme.typography.titleMedium)
            Text("Material: ${p.material}\nMedidas: ${p.dimensions}\nInstrucciones: ${p.instructions}\nEntrega: ${p.deliveryDetails}\nGarantía: ${p.warranty}\nTérminos: ${p.terms}\nCreado: ${p.createdAt.ifBlank{"No registrado en el catálogo anterior"}}\nActualizado: ${p.updatedAt}")
            if(p.images.isEmpty())EmptyState("No hay fotografías para revisar.")
            p.images.forEachIndexed{i,image->AsyncImage(productPhotoModel(image.url),"Fotografía ${i+1}",Modifier.fillMaxWidth().height(200.dp),contentScale=ContentScale.Fit)}
            OutlinedButton({inventory(p.id)}){Text("Consultar y ajustar inventario")}
            OutlinedTextField(reason,{reason=it.take(2000)},label={Text("Motivo de la revisión")},modifier=Modifier.fillMaxWidth(),enabled=!state.loading)
            Row{Checkbox(featured,{featured=it},enabled=!state.loading);Text("Producto destacado al aprobar")}
            Button({action=PublicationState.APPROVED},enabled=!state.loading&&p.images.isNotEmpty()&&p.publicationState in listOf(PublicationState.PENDING,PublicationState.REJECTED,PublicationState.SUSPENDED,PublicationState.APPROVED)){Text("Aprobar publicación")}
            OutlinedButton({action=PublicationState.REJECTED},enabled=!state.loading){Text("Rechazar publicación")}
            OutlinedButton({action=PublicationState.SUSPENDED},enabled=!state.loading){Text("Suspender publicación")}
            if (p.active && p.publicationState != PublicationState.ARCHIVED) {
                TextButton({action=PublicationState.ARCHIVED},enabled=!state.loading && reason.trim().length >= 5){Text("Archivar publicación")}
            }
            action?.let{s->AlertDialog(onDismissRequest={action=null},title={Text("Confirmar ${s.label()}")},text={Text("${p.name}\nMotivo: $reason" + if (s == PublicationState.ARCHIVED) "\nSe retirará del catálogo y se conservará su historial." else "")},confirmButton={TextButton({action=null;viewModel.moderate(s,reason,featured)}){Text("Confirmar revisión")}},dismissButton={TextButton({action=null}){Text("Cancelar")}})}
        }
    }
}
