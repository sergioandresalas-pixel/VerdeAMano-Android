package com.verdeamano.feature.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

data class CheckoutState(val addresses:List<Address> = emptyList(),val selected:String?=null,val quote:CheckoutQuote?=null,
    val loading:Boolean=false,val error:String?=null,val uncertain:Boolean=false,val orderId:String?=null,val locating:Boolean=false,val locationMessage:String?=null,val suggestion:AddressSuggestion?=null)
@HiltViewModel class OrderRecoveryViewModel @Inject constructor(private val repository:OrderRepository):ViewModel(){
    fun hasPendingConfirmation()=repository.pending()!=null
}
@HiltViewModel class CheckoutViewModel @Inject constructor(private val repository:OrderRepository,private val location:DeviceCheckoutLocation?=null):ViewModel() {
    private val mutable=MutableStateFlow(CheckoutState());val state=mutable.asStateFlow()
    init{load()}
    private var locationJob:kotlinx.coroutines.Job?=null
    fun locationDenied(){locationJob?.cancel();mutable.value=mutable.value.copy(locating=false,suggestion=null,locationMessage="No se concedió ubicación. Puedes escribir tu dirección manualmente.")}
    fun locate(client:CheckoutLocationClient?=location){
        if(state.value.uncertain||state.value.locating)return
        mutable.value=mutable.value.copy(locating=true,locationMessage=null,suggestion=null)
        locationJob=viewModelScope.launch{try{
            val hint=requireNotNull(client).suggest();mutable.value=mutable.value.copy(locating=false,suggestion=hint,locationMessage=if(hint.approximate)"Ubicación aproximada: revisa municipio y escribe la dirección exacta."else"Revisa la dirección sugerida antes de guardarla.")
        }catch(_:kotlinx.coroutines.TimeoutCancellationException){mutable.value=mutable.value.copy(locating=false,locationMessage="La ubicación tardó demasiado. Puedes escribir la dirección manualmente.")}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){mutable.value=mutable.value.copy(locating=false,locationMessage="No pudimos sugerir una dirección. Puedes escribirla manualmente.")}}
    }
    fun leaveLocation(){locationJob?.cancel();mutable.value=mutable.value.copy(locating=false)}
    fun consumeLocationSuggestion(){leaveLocation();mutable.value=mutable.value.copy(suggestion=null,locationMessage=null)}
    fun load(){
        if(mutable.value.loading)return
        mutable.value=mutable.value.copy(loading=true,error=null)
        viewModelScope.launch{try{
            val pending=repository.pending();val addresses=repository.addresses()
            val current=mutable.value
            mutable.value=CheckoutState(addresses,selected=pending?.quote?.addressId?:current.selected?.takeIf{value->addresses.any{it.id==value}}?:addresses.firstOrNull{it.isDefault}?.id?:addresses.firstOrNull()?.id,quote=pending?.quote,uncertain=pending!=null,locating=current.locating,locationMessage=current.locationMessage,suggestion=current.suggestion)
        }catch(e:Exception){mutable.value=mutable.value.copy(loading=false,error=e.userMessage())}}
    }
    fun select(id:String){if(!state.value.loading&&!state.value.uncertain)mutable.value=mutable.value.copy(selected=id,quote=null,error=null)}
    fun preview(){val address=state.value.selected?:return;if(state.value.loading||state.value.uncertain)return;mutable.value=mutable.value.copy(loading=true,error=null)
        viewModelScope.launch{try{mutable.value=mutable.value.copy(quote=repository.preview(address),loading=false)}catch(e:Exception){mutable.value=mutable.value.copy(loading=false,error=e.userMessage())}}
    }
    fun confirm(){val quote=state.value.quote?:return;if(state.value.loading||state.value.orderId!=null)return
        mutable.value=mutable.value.copy(loading=true,error=null)
        viewModelScope.launch{try{repository.stage(quote);val order=repository.confirm();mutable.value=mutable.value.copy(loading=false,uncertain=false,orderId=order.id)}catch(e:Exception){
            val apiError=if(e is HttpException)runCatching{Json.decodeFromString<ApiError>(e.response()?.errorBody()?.string().orEmpty())}.getOrNull() else null
            // A lock timeout may belong to an earlier in-flight confirmation with this key.
            // Only an explicit validation conflict is definitive; retain unknown/retryable 409s.
            val definitive=e is HttpException && (e.code() in listOf(400,403,404,429) || (e.code()==409&&apiError?.code=="conflict"))
            if(definitive)repository.discardRejected()
            mutable.value=mutable.value.copy(loading=false,quote=if(definitive)null else quote,uncertain=!definitive,error=apiError?.message?:e.userMessage())
        }}
    }
}
@HiltViewModel class OrdersViewModel @Inject constructor(private val repository:OrderRepository,private val saved:SavedStateHandle):ViewModel() {
    private val mutable=MutableStateFlow(UiState<OrderPage>());val state=mutable.asStateFlow()
    private val selected=MutableStateFlow(UiState<Order>());val detail=selected.asStateFlow()
    var page:Int=1;private set
    var filter:OrderStatus?=null;private set
    private var pendingOperation:String?=null;private var pendingKey:String?=null
    init{saved.get<String>("id")?.let{select(it)}?:load()}
    fun load(){if(mutable.value.loading)return;mutable.value=mutable.value.copy(loading=true,error=null);viewModelScope.launch{try{mutable.value=UiState(repository.list(page,filter))}catch(e:Exception){mutable.value=mutable.value.copy(loading=false,error=e.userMessage())}}}
    fun filter(value:OrderStatus?){if(state.value.loading)return;filter=value;page=1;load()}
    fun page(value:Int){if(state.value.loading||value<1)return;page=value;load()}
    fun select(id:String){if(selected.value.loading)return;selected.value=selected.value.copy(loading=true,error=null);viewModelScope.launch{try{selected.value=UiState(repository.detail(id))}catch(e:Exception){selected.value=selected.value.copy(loading=false,error=e.userMessage())}}}
    private fun key(operation:String):String {if(pendingOperation!=operation){pendingOperation=operation;pendingKey=UUID.randomUUID().toString()};return requireNotNull(pendingKey)}
    private fun mutate(action:suspend()->Order){if(detail.value.loading)return;selected.value=selected.value.copy(loading=true,error=null);viewModelScope.launch{try{selected.value=UiState(action());pendingOperation=null;pendingKey=null}catch(e:Exception){selected.value=selected.value.copy(loading=false,error=e.userMessage())}}}
    fun cancel(reason:String){val order=detail.value.data?:return;val input=OrderCancellation(order.version,key("cancel:${order.id}:${order.version}:$reason"),reason);mutate{repository.cancel(order.id,input)}}
    fun transition(fulfillment:OrderFulfillment,status:OrderStatus,note:String,tracking:String){val order=detail.value.data?:return;val input=OrderTransitionInput(status,fulfillment.version,key("transition:${order.id}:${fulfillment.sellerId}:${fulfillment.version}:$status:$note:$tracking"),note,tracking);mutate{repository.transition(order.id,fulfillment.sellerId,input)}}
}
