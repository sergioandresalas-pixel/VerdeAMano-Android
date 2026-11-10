package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import com.verdeamano.core.session.SessionStore
import com.verdeamano.feature.commercial.*
import com.verdeamano.feature.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class) class Phase6bCommunicationsTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){Dispatchers.resetMain()}
    private fun session()=SessionStore().apply{set(Session("token",User("qa","QA","qa@test.test",Role.BUYER),"future",false),"uid")}
    private val notice=NotificationItem("notice","payment.updated","order","order","now",false)
    @Test fun accountSwitchClearsAlreadyLoadedInboxWithoutWaitingForAnotherRequest()=runTest(dispatcher){
        val s=session();val vm=CommercialViewModel(CommercialRepository(object:TestApi(){override suspend fun notifications()=listOf(notice)},s));vm.notifications();advanceUntilIdle();assertEquals(1,vm.state.value.notifications.size)
        s.clear();advanceUntilIdle();assertTrue(vm.state.value.notifications.isEmpty());assertFalse(vm.state.value.loading)
    }
    @Test fun cancelledNotificationRequestEndsLoadingAndDropsPreviousIdentity()=runTest(dispatcher){
        val s=session();val response=CompletableDeferred<List<NotificationItem>>();val vm=CommercialViewModel(CommercialRepository(object:TestApi(){override suspend fun notifications()=response.await()},s))
        vm.notifications();runCurrent();s.clear();runCurrent();response.complete(listOf(notice));advanceUntilIdle();assertFalse(vm.state.value.loading);assertTrue(vm.state.value.notifications.isEmpty());assertNull(vm.state.value.error)
    }
    @Test fun failureAndRetryDoNotInventADeviceReceipt()=runTest(dispatcher){
        var attempts=0;val vm=CommercialViewModel(CommercialRepository(object:TestApi(){override suspend fun notifications():List<NotificationItem>{attempts++;if(attempts==1)throw IOException("offline");return listOf(notice)}},session()))
        vm.notifications();advanceUntilIdle();assertNotNull(vm.state.value.error);vm.notifications();advanceUntilIdle();assertNull(vm.state.value.error);assertNull(vm.state.value.notifications.single().receivedOnDeviceAt);assertEquals(2,attempts)
    }
    @Test fun delayedPushRegistrationCannotFollowAChangedIdentity()=runTest(dispatcher){
        val s=session();var calls=0;val vm=CommercialViewModel(CommercialRepository(object:TestApi(){override suspend fun registerPush(input:PushRegistration){calls++}},s));val original=vm.currentUid();s.clear();s.set(Session("new",User("new","New","new@test.test",Role.BUYER),"future",false),"new-uid");vm.push("old-token",original);advanceUntilIdle();assertEquals(0,calls)
    }
    @Test fun queuedSdkReceiptRetriesIdempotentlyAndOnlyForItsIdentity()=runTest(dispatcher){
        val queue=mutableMapOf("uid" to mutableListOf("notice"));val store=object:DeviceReceiptStore{override fun pending(uid:String)=queue[uid]?.toList().orEmpty();override fun record(uid:String,id:String){queue.getOrPut(uid){mutableListOf()}.add(id)};override fun acknowledged(uid:String,id:String){queue[uid]?.remove(id)}}
        val s=session();var calls=0;val api=object:TestApi(){override suspend fun deviceReceipt(id:String){calls++;if(calls==1)throw IOException("offline")}}
        val r=CommercialRepository(api,s,receipts=store);try{r.reconcileDeviceReceipts();fail("Must retain receipt")}catch(_:IOException){};assertEquals(listOf("notice"),queue["uid"])
        s.clear();r.reconcileDeviceReceipts();assertEquals(1,calls);s.set(Session("token",User("qa","QA","qa@test.test",Role.BUYER),"future",false),"uid");r.reconcileDeviceReceipts();assertTrue(queue["uid"]!!.isEmpty());assertEquals(2,calls)
    }
    @Test fun providerAcceptanceLabelsDoNotClaimDelivery(){assertTrue(deliveryLabel("ACCEPTED_PROVIDER").contains("recepción no confirmada"));assertTrue(deliveryLabel("ACCEPTED_PROVIDER_SMTP").contains("entrega externa por confirmar"));assertTrue(deliveryLabel("ACCEPTED_LOCAL_SMTP").contains("entrega externa no comprobada"));assertTrue(deliveryLabel("RECONCILIATION_REQUIRED").contains("sin reenvío"))}
    @Test fun malformedInvitationNeverCallsTheServer()=runTest(dispatcher){var calls=0;val vm=SellerInvitationViewModel(AccountRepository(object:TestApi(){override suspend fun acceptInvitation(id:String,input:InvitationAcceptance):User{calls++;error("Should not be called")}},session()));vm.accept("bad","bad");advanceUntilIdle();assertEquals(0,calls);assertNotNull(vm.state.value.error)}
    @Test fun invitationListFromPreviousIdentityIsClearedOnAccountSwitch()=runTest(dispatcher){
        val s=session();val inv=SellerInvitation("qa","private@test.test","INVITATION_PREPARED_LOCAL","future")
        val vm=SellerInvitationViewModel(AccountRepository(object:TestApi(){override suspend fun sellerInvitations()=listOf(inv)},s));vm.load();advanceUntilIdle();assertEquals(1,vm.state.value.data?.size)
        s.clear();advanceUntilIdle();assertNull(vm.state.value.data);assertNull(vm.message.value)
    }
}
