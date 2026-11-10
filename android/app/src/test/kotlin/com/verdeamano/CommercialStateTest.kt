package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import com.verdeamano.core.session.SessionStore
import com.verdeamano.feature.commercial.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class) class CommercialStateTest {
    @Test fun delayedTokenRotationCannotRegisterForAnotherIdentity()=runTest(dispatcher){
        val session=session();var writes=0;val api=object:TestApi(){override suspend fun registerPush(input:PushRegistration){writes++}}
        val r=CommercialRepository(api,session);val original=session.firebaseUid
        session.clear();session.set(Session("other",User("other","Other","other@verdeamano.test",Role.BUYER),"future",false),"other-uid")
        try{r.push("test-token",original);fail("Old UID must not register a push token for another identity")}catch(_:CancellationException){}
        assertEquals(0,writes)
    }
    private val dispatcher=StandardTestDispatcher()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){Dispatchers.resetMain()}
    private fun session()=SessionStore().apply{set(Session("test",User("buyer","QA","qa@verdeamano.test",Role.BUYER),"future",false),"uid")}
    @Test fun notificationResponseFromPreviousIdentityIsDiscarded()=runTest(dispatcher){
        val session=session();val response=CompletableDeferred<List<NotificationItem>>();val api=object:TestApi(){override suspend fun notifications()=response.await()}
        val request=async{CommercialRepository(api,session).notifications()};runCurrent();session.clear()
        response.complete(listOf(NotificationItem("old-private","payment.updated","order","private-order","time",false)))
        try{request.await();fail("Old account inbox must never become visible")}catch(_:CancellationException){}
    }
    @Test fun failedMarkReadKeepsUnreadInboxAndEndsLoading()=runTest(dispatcher){
        val notification=NotificationItem("notice","claim.updated","claim","case","time",false)
        val api=object:TestApi(){override suspend fun notifications()=listOf(notification);override suspend fun readNotification(id:String):Unit=throw IOException("offline")}
        val vm=CommercialViewModel(CommercialRepository(api,session()));vm.notifications();advanceUntilIdle();vm.read("notice");advanceUntilIdle()
        assertFalse(vm.state.value.loading);assertNotNull(vm.state.value.error);assertEquals(listOf(notification),vm.state.value.notifications)
    }
    @Test fun officialCheckoutHostAndSandboxPublicKeyAreRequired(){assertTrue(safeSandboxCheckout("https://checkout.wompi.co/p/?public-key=pub_test_fixture"));assertFalse(safeSandboxCheckout("http://checkout.wompi.co/p/?public-key=pub_test_fixture"));assertFalse(safeSandboxCheckout("https://evil.test/p/?public-key=pub_test_fixture"));assertFalse(safeSandboxCheckout("https://checkout.wompi.co/p/?public-key=pub_prod_fixture"));assertFalse(safeSandboxCheckout("https://checkout.wompi.co/p/?public-key=pub_prod_fixture&public-key=pub_test_fixture"))}
    @Test fun serverPendingRemainsPendingAfterCheckoutPreparation()=runTest(dispatcher){val p=PaymentCheckout("order","ref",10000,PaymentStatus.PENDING,"future",checkoutUrl="https://checkout.wompi.co/p/?public-key=pub_test_fixture");val api=object:TestApi(){override suspend fun paymentCheckout(id:String)=p};val vm=CommercialViewModel(CommercialRepository(api,session()));vm.checkout("order");assertTrue(vm.state.value.loading);advanceUntilIdle();assertEquals(PaymentStatus.PENDING,vm.state.value.payment!!.status)}
    @Test fun connectionErrorEndsLoadingWithoutInventingApproval()=runTest(dispatcher){val api=object:TestApi(){override suspend fun payment(id:String):PaymentCheckout=throw IOException("offline")};val vm=CommercialViewModel(CommercialRepository(api,session()));vm.payment("order");advanceUntilIdle();assertFalse(vm.state.value.loading);assertNotNull(vm.state.value.error);assertNull(vm.state.value.payment)}
    @Test fun changedIdentityDiscardsPrivateLatePaymentResponse()=runTest(dispatcher){val session=session();val response=CompletableDeferred<PaymentCheckout>();val api=object:TestApi(){override suspend fun payment(id:String)=response.await()};val repo=CommercialRepository(api,session);val job=async{repo.payment("order")};runCurrent();session.clear();response.complete(PaymentCheckout("order","private",1,PaymentStatus.APPROVED,"future"));try{job.await();fail("Must discard old identity")}catch(_:CancellationException){}}
    @Test fun repeatedTapDoesNotIssueTwoQueries()=runTest(dispatcher){var calls=0;val api=object:TestApi(){override suspend fun payment(id:String):PaymentCheckout{calls++;delay(10);return PaymentCheckout(id,"ref",1,PaymentStatus.UNKNOWN,"future")}};val vm=CommercialViewModel(CommercialRepository(api,session()));vm.payment("order");vm.payment("order");advanceUntilIdle();assertEquals(1,calls);assertEquals(PaymentStatus.UNKNOWN,vm.state.value.payment!!.status)}
    @Test fun uncertainClaimSurvivesRepositoryRestartWithSameRequestAndDifferentAccountsStayIsolated()=runTest(dispatcher){
        val stored=mutableMapOf<String,ClaimInput>();val persistence=object:ClaimPendingStore{override fun read(uid:String)=stored[uid];override fun save(uid:String,input:ClaimInput){stored[uid]=input};override fun clear(uid:String){stored.remove(uid)}}
        val session=session();val api=object:TestApi(){override suspend fun createClaim(input:ClaimInput):ClaimCase=throw IOException("uncertain")};val input=ClaimInput("order",ClaimKind.PQR,"Motivo QA","Descripción detallada QA","logical-request")
        try{CommercialRepository(api,session,persistence).create(input);fail("Must retain request")}catch(_:IOException){}
        val reopened=CommercialRepository(api,session,persistence);assertEquals(input,reopened.pendingClaim());session.clear();assertNull(reopened.pendingClaim());assertEquals(input,stored["uid"])
    }
    @Test fun refundReconciliationFailureNeverTriggersAnotherRefundAndEndsLoading()=runTest(dispatcher){
        val claim=ClaimCase("case","order","ref",ClaimKind.PQR,"Motivo QA","Descripción QA",CaseStatus.RESOLVED,3,null,"time","time",null,"manual",emptyList(),emptyList());var queries=0
        val api=object:TestApi(){override suspend fun claim(id:String)=claim;override suspend fun reconcileRefund(id:String):SandboxRefund{queries++;delay(10);throw IOException("provider unavailable")};override suspend fun authorizeRefund(id:String,input:RefundAuthorization):SandboxRefund=error("Must never send another refund")}
        val vm=CommercialViewModel(CommercialRepository(api,session()));vm.claim("case");advanceUntilIdle();vm.reconcileRefund();vm.reconcileRefund();advanceUntilIdle();assertEquals(1,queries);assertFalse(vm.state.value.loading);assertNotNull(vm.state.value.error);assertNull(vm.state.value.refund)
    }
}
