package com.verdeamano.feature.seller

import androidx.lifecycle.*
import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel class ProductPhotosViewModel @Inject constructor(private val repository:PublicationRepository,handle:SavedStateHandle):ViewModel(){
    private val id:String=requireNotNull(handle["id"])
    private val mutable=MutableStateFlow(UiState<Product>());val state=mutable.asStateFlow()
    private val notice=MutableStateFlow<String?>(null);val message=notice.asStateFlow()
    private var retry:Pair<String,String>?=null
    private var uploadVersion:Int?=null
    private fun request(intent:String):String=retry?.takeIf{it.first==intent}?.second ?: UUID.randomUUID().toString().also{retry=intent to it}
    init{load()}
    fun load()=operate{repository.managed(id)}
    fun upload(file:File,replace:String?,saved:()->Unit){val p=mutable.value.data?:return;val intent="upload:$replace:${file.name}";if(retry?.first!=intent)uploadVersion=p.version;val key=request(intent)
        operate("Fotografía guardada."){repository.upload(p.copy(version=requireNotNull(uploadVersion)),file,key,replace);repository.managed(id).also{saved();uploadVersion=null}}}
    fun remove(image:String){val p=mutable.value.data?:return;val key=request("remove:${p.version}:$image");operate("Fotografía retirada."){repository.remove(p,image,key)}}
    fun order(ids:List<String>){val p=mutable.value.data?:return;val key=request("order:${p.version}:$ids");operate("Orden guardado."){repository.order(p,ids,key)}}
    private fun operate(text:String?=null,action:suspend()->Product){
        if(mutable.value.loading)return
        val before=mutable.value.data;mutable.value=UiState(before,loading=true);notice.value=null
        viewModelScope.launch{try{mutable.value=UiState(action());if(text!=null){retry=null;notice.value=text}}catch(e:Exception){mutable.value=UiState(before,error=e.userMessage())}}
    }
}
