package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import com.verdeamano.core.session.SessionStore
import com.verdeamano.feature.orders.CheckoutViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class MemoryOrderPendingStore:OrderPendingStore {
    private val data=mutableMapOf<String,PendingOrderConfirmation>()
    override fun read(uid:String)=data[uid]
    override fun save(uid:String,value:PendingOrderConfirmation){data[uid]=value}
    override fun clear(uid:String){data.remove(uid)}
}
@OptIn(ExperimentalCoroutinesApi::class) class OrderStateTest {
    private val dispatcher=StandardTestDispatcher()
    private val user=User("buyer","Comprador QA","qa@verdeamano.test",Role.BUYER)
    private val address=Address("address",user.id,"QA","Medellín","Calle QA 10",isDefault=true)
    private val quote=CheckoutQuote("quote",address.id,DeliverySnapshot("QA","3001234567","Antioquia","Medellín","Calle QA 10"),listOf(CheckoutLine(OrderLine("product","seller","Maceta QA",2,27000),1)),54000,0,0,54000,expiresAt="future")
    private val order=Order("order",user.id,address.id,quote.items.map{it.item},54000,OrderStatus.PENDING,quote.delivery,54000,0,0,"COP",1,"now","now",emptyList(),emptyList(),null)
    private fun session()=SessionStore().apply{set(Session("private-test-token",user,"future",false),"uid-buyer")}
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){Dispatchers.resetMain()}
    @Test fun networkLossAndNewRepositoryReuseThePersistedLogicalConfirmation()=runTest(dispatcher){
        val calls=mutableListOf<OrderConfirmation>();val memory=MemoryOrderPendingStore();val session=session()
        val api=object:TestApi(){override suspend fun confirmOrder(input:OrderConfirmation):Order{calls+=input;if(calls.size==1)throw IOException("lost response");return order}}
        val first=OrderRepository(api,session,memory);first.stage(quote)
        try{first.confirm();fail("Expected connection failure")}catch(_:IOException){}
        val reopened=OrderRepository(api,session,memory);assertNotNull(reopened.pending());reopened.stage(quote)
        assertEquals(order,reopened.confirm());assertEquals(calls[0],calls[1]);assertNull(reopened.pending())
    }
    @Test fun differentQuoteCannotReplaceAnUncertainConfirmation()=runTest(dispatcher){
        val repository=OrderRepository(TestApi(),session(),MemoryOrderPendingStore());val original=repository.stage(quote)
        try{repository.stage(quote.copy(id="another-quote"));fail("Must preserve original request")}catch(_:IllegalStateException){}
        assertEquals(original,repository.pending())
    }
    @Test fun accountSwitchRejectsOldResponseAndHidesItsPendingState()=runTest(dispatcher){
        val response=CompletableDeferred<Order>();val api=object:TestApi(){override suspend fun confirmOrder(input:OrderConfirmation)=response.await()}
        val store=session();val memory=MemoryOrderPendingStore();val repository=OrderRepository(api,store,memory);repository.stage(quote)
        val request=async{repository.confirm()};runCurrent();store.set(Session("new-test",user.copy(id="other"),"future",false),"uid-other");response.complete(order)
        try{request.await();fail("Old private response must be rejected")}catch(_:CancellationException){}
        assertNull(repository.pending());assertNotNull(memory.read("uid-buyer"))
    }
    @Test fun checkoutLoadingGuardsDoublePressAndKeepsServerTotal()=runTest(dispatcher){
        val response=CompletableDeferred<Order>();var calls=0
        val api=object:TestApi(){override suspend fun addresses()=listOf(address);override suspend fun checkout(input:CheckoutPreviewInput)=quote;override suspend fun confirmOrder(input:OrderConfirmation):Order{calls++;return response.await()}}
        val vm=CheckoutViewModel(OrderRepository(api,session(),MemoryOrderPendingStore()));advanceUntilIdle();vm.preview();advanceUntilIdle()
        assertEquals(54000L,vm.state.value.quote!!.totalCop);vm.confirm();vm.confirm();runCurrent();assertEquals(1,calls);assertTrue(vm.state.value.loading)
        response.complete(order);advanceUntilIdle();assertEquals(order.id,vm.state.value.orderId);assertFalse(vm.state.value.loading)
    }
    @Test fun conflictDiscardsRejectedQuoteAndRequiresNewReview()=runTest(dispatcher){
        val memory=MemoryOrderPendingStore();val api=object:TestApi(){override suspend fun addresses()=listOf(address);override suspend fun checkout(input:CheckoutPreviewInput)=quote;override suspend fun confirmOrder(input:OrderConfirmation):Order=throw HttpException(Response.error<Order>(409,"{\"code\":\"conflict\",\"message\":\"El precio cambió.\"}".toResponseBody()))}
        val vm=CheckoutViewModel(OrderRepository(api,session(),memory));advanceUntilIdle();vm.preview();advanceUntilIdle();vm.confirm();advanceUntilIdle()
        assertNull(vm.state.value.quote);assertFalse(vm.state.value.uncertain);assertEquals("El precio cambió.",vm.state.value.error);assertNull(memory.read("uid-buyer"))
    }
    @Test fun reopeningCheckoutShowsPendingRequestWithoutSendingItAutomatically()=runTest(dispatcher){
        val memory=MemoryOrderPendingStore();var calls=0;val api=object:TestApi(){override suspend fun addresses()=listOf(address);override suspend fun confirmOrder(input:OrderConfirmation):Order{calls++;return order}}
        val repository=OrderRepository(api,session(),memory);repository.stage(quote);val vm=CheckoutViewModel(repository);advanceUntilIdle()
        assertTrue(vm.state.value.uncertain);assertEquals(quote,vm.state.value.quote);assertEquals(0,calls)
    }
    @Test fun retryableConflictRetainsExactRequestInsteadOfCreatingANewConfirmation()=runTest(dispatcher){
        val memory=MemoryOrderPendingStore();val requests=mutableListOf<OrderConfirmation>()
        val api=object:TestApi(){override suspend fun addresses()=listOf(address);override suspend fun checkout(input:CheckoutPreviewInput)=quote
            override suspend fun confirmOrder(input:OrderConfirmation):Order{requests+=input;if(requests.size==1)throw HttpException(Response.error<Order>(409,"{\"code\":\"order_in_progress\",\"message\":\"Reintenta la misma confirmación.\"}".toResponseBody()));return order}}
        val vm=CheckoutViewModel(OrderRepository(api,session(),memory));advanceUntilIdle();vm.preview();advanceUntilIdle();vm.confirm();advanceUntilIdle()
        assertTrue(vm.state.value.uncertain);assertEquals(quote,vm.state.value.quote);assertEquals(requests.single(),memory.read("uid-buyer")!!.input)
        vm.confirm();advanceUntilIdle();assertEquals(requests[0],requests[1]);assertEquals(order.id,vm.state.value.orderId)
    }
}
