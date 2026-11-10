package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import com.verdeamano.core.session.SessionStore
import com.verdeamano.feature.cart.CartViewModel
import com.verdeamano.feature.orders.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class) class Phase6LocalFeaturesTest {
    private val dispatcher=StandardTestDispatcher()
    private val product=Product("product","seller","Public seller","category","SKU","Maceta","Description","Barro","10cm",27000,5)
    private val cart=Cart(listOf(CartItem(product,2,54000)),54000)
    private class Cache:RecentCartStore {
        val snapshots=mutableMapOf<String,RecentCartSnapshot>()
        override fun read(uid:String)=snapshots[uid]
        override fun save(uid:String,cart:Cart){snapshots[uid]=RecentCartSnapshot.from(cart,123456L)}
        override fun clear(uid:String){snapshots.remove(uid)}
    }
    private fun session()=SessionStore().apply{set(Session("private-token",User("business-buyer","Private name","private@example.test",Role.BUYER),"future",false),"firebase-uid")}
    @Test fun privacyAnswerRetryKeepsLogicalRequestAndDoesNotAdvanceStateOnLostResponse()=runTest(dispatcher){
        val initial=PrivacyRequest("request",PrivacyRequestType.DELETION,"Revisar conservación","OPEN","now")
        val inputs=mutableListOf<PrivacyReview>();var failing=true
        val api=object:TestApi(){override suspend fun privacyDetail(id:String)=initial;override suspend fun privacyResponsibles()=emptyList<User>();override suspend fun reviewPrivacy(id:String,input:PrivacyReview):PrivacyRequest{inputs+=input;if(failing)throw IOException("lost response");return initial.copy(status=input.status.name,version=2,response=input.response)}}
        val vm=com.verdeamano.feature.profile.PrivacyReviewViewModel(AccountRepository(api,session()));vm.select(initial.id,true);advanceUntilIdle()
        vm.review(PrivacyStatus.IN_REVIEW,"Revisión humana QA","Conservar hasta decisión formal",null);advanceUntilIdle();assertNotNull(vm.detail.value.error);assertEquals("OPEN",vm.detail.value.data!!.status)
        failing=false;vm.review(PrivacyStatus.IN_REVIEW,"Revisión humana QA","Conservar hasta decisión formal",null);advanceUntilIdle()
        assertEquals(inputs[0].requestId,inputs[1].requestId);assertEquals("IN_REVIEW",vm.detail.value.data!!.status);assertFalse(vm.detail.value.loading)
    }
    @Test fun privacyValidationDoesNotSubmitAnEmptyConservationDecision()=runTest(dispatcher){
        var writes=0;val api=object:TestApi(){override suspend fun privacyDetail(id:String)=PrivacyRequest(id,PrivacyRequestType.ACCESS,"Acceso QA","OPEN","now");override suspend fun reviewPrivacy(id:String,input:PrivacyReview):PrivacyRequest{writes++;error("Unexpected")}}
        val vm=com.verdeamano.feature.profile.PrivacyReviewViewModel(AccountRepository(api,session()));vm.select("request",false);advanceUntilIdle();vm.review(PrivacyStatus.IN_REVIEW,"Respuesta válida QA","",null);advanceUntilIdle();assertEquals(0,writes);assertNotNull(vm.detail.value.error)
    }
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun cleanup(){Dispatchers.resetMain()}
    @Test fun snapshotContainsOnlyRecentPublicRowsAndNoIdentityOrCredentials(){
        val value=RecentCartSnapshot.from(cart,123456L);val serialized=RecentCartCodec.encode(value)
        assertEquals(value,RecentCartCodec.decode(serialized))
        assertEquals(54000L,value.subtotalCop);assertEquals(2,value.rows.single().quantity)
        for(privateValue in listOf("Private name","private@example.test","firebase-uid","private-token","business-buyer","Public seller"))assertFalse(serialized.contains(privateValue))
        assertFalse(value.valid(123456L+49*60*60*1000));assertFalse(value.copy(savedAtEpochMillis=999999L).valid(123456L))
    }
    @Test fun restartOfflineCacheIsReadOnlyThenGetReconcilesWithoutReplayingAnyMutation()=runTest(dispatcher){
        val session=session();val cache=Cache();var offline=false;var writes=0;var reads=0;var actual=cart
        val api=object:TestApi(){override suspend fun cart():Cart{reads++;if(offline)throw IOException("network");return actual};override suspend fun quantity(id:String,input:QuantityInput):Cart{writes++;error("Must not mutate offline")};override suspend fun clearCart():Cart{writes++;error("Must not clear offline")}}
        CommerceRepository(api,session,cache).cart();offline=true
        val restarted=CartViewModel(CommerceRepository(api,session,cache));advanceUntilIdle();assertNotNull(restarted.recent.value);assertFalse(restarted.state.value.loading)
        restarted.quantity("product",3);restarted.clear();advanceUntilIdle();assertEquals(0,writes)
        offline=false;actual=Cart(listOf(CartItem(product.copy(priceCop=29000),1,29000)),29000);restarted.load();advanceUntilIdle()
        assertNull(restarted.recent.value);assertEquals(29000L,restarted.state.value.data!!.subtotalCop);assertEquals(1,cache.read("firebase-uid")!!.rows.single().quantity);assertEquals(0,writes);assertEquals(3,reads)
    }
    @Test fun lateResponseFromOldUidCannotPopulateTheNewAccountOrWriteItsCache()=runTest(dispatcher){
        val session=session();val cache=Cache();val deferred=CompletableDeferred<Cart>();val api=object:TestApi(){override suspend fun cart()=deferred.await()}
        val result=async{CommerceRepository(api,session,cache).cart()};runCurrent();session.clear();session.set(Session("new",User("other","Other","other@test.test",Role.BUYER),"future",false),"another-uid");deferred.complete(cart)
        try{result.await();fail("Must cancel old response")}catch(_:CancellationException){}
        assertTrue(cache.snapshots.isEmpty())
    }
    @Test fun authorizationErrorsDoNotOfferDegradedData()=runTest(dispatcher){
        val session=session();val cache=Cache().apply{save("firebase-uid",cart)}
        for(status in listOf(401,403)){val api=object:TestApi(){override suspend fun cart():Cart=throw HttpException(Response.error<Cart>(status,"{}".toResponseBody()))};val vm=CartViewModel(CommerceRepository(api,session,cache));advanceUntilIdle();assertNull(vm.recent.value);assertNotNull(vm.state.value.error)}
    }
    @Test fun cachesAreSeparatedByUidAndExpiredSnapshotsAreRejected(){
        val cache=Cache();cache.save("first",cart);assertNull(cache.read("second"));cache.clear("first");assertNull(cache.read("first"))
        val cached=RecentCartSnapshot.from(cart,123456L);assertTrue(cached.valid(123456L));assertFalse(cached.copy(rows=listOf(cached.rows.single().copy(quantity=-1))).valid(123456L))
    }
    private fun checkout():CheckoutViewModel {
        val api=object:TestApi(){override suspend fun addresses()=emptyList<Address>()}
        val pending=object:OrderPendingStore{override fun read(uid:String):PendingOrderConfirmation?=null;override fun save(uid:String,value:PendingOrderConfirmation)=Unit;override fun clear(uid:String)=Unit}
        return CheckoutViewModel(OrderRepository(api,session(),pending))
    }
    @Test fun rejectedLocationPermissionKeepsCheckoutAndManualAddressAvailable()=runTest(dispatcher){val vm=checkout();advanceUntilIdle();vm.locationDenied();assertFalse(vm.state.value.locating);assertNull(vm.state.value.suggestion);assertTrue(vm.state.value.locationMessage!!.contains("manualmente"));assertNull(vm.state.value.orderId)}
    @Test fun addressReloadAfterPermissionDialogMustNotDiscardACompletedLocationSuggestion()=runTest(dispatcher){
        val addresses=CompletableDeferred<List<Address>>();var calls=0
        val api=object:TestApi(){override suspend fun addresses():List<Address>{calls++;return if(calls==1)emptyList()else addresses.await()}}
        val pending=object:OrderPendingStore{override fun read(uid:String):PendingOrderConfirmation?=null;override fun save(uid:String,value:PendingOrderConfirmation)=Unit;override fun clear(uid:String)=Unit}
        val vm=CheckoutViewModel(OrderRepository(api,session(),pending));advanceUntilIdle()
        vm.locate(object:CheckoutLocationClient{override suspend fun suggest()=AddressSuggestion("Antioquia","Medellín","",true)});advanceUntilIdle()
        vm.load();runCurrent();addresses.complete(emptyList());advanceUntilIdle()
        assertNotNull("A foreground address refresh must preserve the optional suggestion",vm.state.value.suggestion);assertFalse(vm.state.value.locating);assertNull(vm.state.value.orderId)
    }
    @Test fun locationFailureAndTimeoutEndLoadingWithoutCreatingAnAddressOrOrder()=runTest(dispatcher){
        val vm=checkout();advanceUntilIdle();vm.locate(object:CheckoutLocationClient{override suspend fun suggest():AddressSuggestion=throw IOException("unavailable")});advanceUntilIdle();assertFalse(vm.state.value.locating);assertNull(vm.state.value.suggestion)
        vm.locate(object:CheckoutLocationClient{override suspend fun suggest():AddressSuggestion=withTimeout(10){delay(100);error("unreachable")}});advanceUntilIdle();assertFalse(vm.state.value.locating);assertTrue(vm.state.value.locationMessage!!.contains("demasiado"));assertNull(vm.state.value.orderId)
    }
    @Test fun approximateLocationSuggestionDoesNotSaveCoordinatesOrAutoConfirmCheckout()=runTest(dispatcher){val vm=checkout();advanceUntilIdle();vm.locate(object:CheckoutLocationClient{override suspend fun suggest()=AddressSuggestion("Antioquia","Medellín","",true)});advanceUntilIdle();assertEquals("Medellín",vm.state.value.suggestion!!.city);assertTrue(vm.state.value.suggestion!!.approximate);assertNull(vm.state.value.quote);assertNull(vm.state.value.orderId)}
}
