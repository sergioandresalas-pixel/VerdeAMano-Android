package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import com.verdeamano.core.session.SessionStore
import com.verdeamano.feature.commercial.CommercialViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class Phase6gCommunicationsTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){Dispatchers.resetMain()}
    private fun session()=SessionStore().apply{set(Session("fixture",User("qa","QA","qa@example.invalid",Role.BUYER),"future",false),"uid-qa")}
    @Test fun sameUidReauthenticationClearsInboxEvenWhenSessionSnapshotsAreEqual()=runTest(dispatcher){
        val s=session();val original=s.session.value!!
        val vm=CommercialViewModel(CommercialRepository(object:TestApi(){override suspend fun notifications()=listOf(NotificationItem("fixture","qa.notification","qa","fixture","now",false))},s))
        vm.notifications();advanceUntilIdle();assertEquals(1,vm.state.value.notifications.size)
        s.clear();s.set(original,"uid-qa");runCurrent();assertTrue(vm.state.value.notifications.isEmpty());assertFalse(vm.state.value.loading)
    }
    @Test fun cancelledLateReceiptRemainsPendingForTheOriginalUid()=runTest(dispatcher){
        val s=session();val gate=CompletableDeferred<Unit>();val pending=mutableSetOf("event")
        val queue=object:DeviceReceiptStore{override fun pending(uid:String)=pending.toList();override fun record(uid:String,id:String)=Unit;override fun acknowledged(uid:String,id:String){pending.remove(id)}}
        val repo=CommercialRepository(object:TestApi(){override suspend fun deviceReceipt(id:String){withContext(NonCancellable){gate.await()}}},s,receipts=queue)
        val job=launch{repo.reconcileDeviceReceipts()};runCurrent();job.cancel();gate.complete(Unit);advanceUntilIdle()
        assertTrue(job.isCancelled);assertEquals(setOf("event"),pending)
    }
    @Test fun newSessionCanLoadWhileThePreviousSameUidRequestIsStillReturning()=runTest(dispatcher){
        val s=session();val original=s.session.value!!;val gate=CompletableDeferred<Unit>();var calls=0
        val vm=CommercialViewModel(CommercialRepository(object:TestApi(){override suspend fun notifications():List<NotificationItem>{calls++;if(calls==1)withContext(NonCancellable){gate.await()};return emptyList()}},s))
        vm.notifications();runCurrent();s.clear();s.set(original,"uid-qa");runCurrent();vm.notifications();runCurrent()
        assertEquals(2,calls);assertFalse(vm.state.value.loading);gate.complete(Unit);advanceUntilIdle();assertNull(vm.state.value.error)
    }
}
