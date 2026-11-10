package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import com.verdeamano.core.session.SessionStore
import com.verdeamano.feature.profile.SellerInvitationViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class Phase6fCommunicationsTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){Dispatchers.resetMain()}
    private fun session()=SessionStore().apply{set(Session("fixture",User("a","QA","a@verdeamano.test",Role.BUYER),"future",false),"uid-a")}
    private fun switch(s:SessionStore){s.clear();s.set(Session("fixture-b",User("b","QA B","b@verdeamano.test",Role.BUYER),"future",false),"uid-b")}
    private val invitation=SellerInvitation("id-b","b@verdeamano.test","INVITATION_PREPARED_LOCAL","future")

    @Test fun oldInvitationCompletionCannotEraseTheNewIdentityResult()=runTest(dispatcher){
        val s=session();val old=CompletableDeferred<Unit>();var calls=0
        val vm=SellerInvitationViewModel(AccountRepository(object:TestApi(){override suspend fun sellerInvitations():List<SellerInvitation>{
            calls++;if(calls==1)withContext(NonCancellable){old.await()}
            return listOf(invitation)
        }},s))
        vm.load();runCurrent();switch(s);runCurrent();vm.load();runCurrent()
        assertEquals(listOf(invitation),vm.state.value.data)
        old.complete(Unit);advanceUntilIdle()
        assertEquals(listOf(invitation),vm.state.value.data);assertNull(vm.state.value.error);assertFalse(vm.state.value.loading)
    }
    @Test fun oldInvitationFailureCannotOverwriteTheNewIdentityResult()=runTest(dispatcher){
        val s=session();val old=CompletableDeferred<Unit>();var calls=0
        val vm=SellerInvitationViewModel(AccountRepository(object:TestApi(){override suspend fun sellerInvitations():List<SellerInvitation>{
            calls++;if(calls==1){withContext(NonCancellable){old.await()};throw IOException("old failure")};return listOf(invitation)
        }},s))
        vm.load();runCurrent();switch(s);runCurrent();vm.load();runCurrent();old.complete(Unit);advanceUntilIdle()
        assertEquals(listOf(invitation),vm.state.value.data);assertNull(vm.state.value.error)
    }
    @Test fun acceptedInvitationAppliesOnlyTheRoleReturnedByTheServer()=runTest(dispatcher){
        val s=session();var calls=0
        val vm=SellerInvitationViewModel(AccountRepository(object:TestApi(){override suspend fun acceptInvitation(id:String,input:InvitationAcceptance):User{calls++;return s.session.value!!.user.copy(role=Role.SELLER)}},s))
        vm.accept(UUID.randomUUID().toString(),"A".repeat(43));advanceUntilIdle()
        assertEquals(1,calls);assertEquals(Role.SELLER,s.session.value!!.user.role);assertNotNull(vm.message.value);assertNull(vm.state.value.error)
    }
    @Test fun failedAcceptanceKeepsBuyerAndAllowsAnExplicitRetry()=runTest(dispatcher){
        val s=session();var calls=0;val id=UUID.randomUUID().toString()
        val vm=SellerInvitationViewModel(AccountRepository(object:TestApi(){override suspend fun acceptInvitation(id:String,input:InvitationAcceptance):User{calls++;if(calls==1)throw IOException("offline");return s.session.value!!.user.copy(role=Role.SELLER)}},s))
        vm.accept(id,"A".repeat(43));advanceUntilIdle();assertEquals(Role.BUYER,s.session.value!!.user.role);assertNotNull(vm.state.value.error);assertNull(vm.message.value)
        vm.accept(id,"A".repeat(43));advanceUntilIdle();assertEquals(2,calls);assertEquals(Role.SELLER,s.session.value!!.user.role);assertNotNull(vm.message.value)
    }
    @Test fun repeatedTapWhileAcceptingDoesNotCreateAnotherRequest()=runTest(dispatcher){
        val s=session();val gate=CompletableDeferred<User>();var calls=0;val id=UUID.randomUUID().toString()
        val vm=SellerInvitationViewModel(AccountRepository(object:TestApi(){override suspend fun acceptInvitation(id:String,input:InvitationAcceptance):User{calls++;return gate.await()}},s))
        vm.accept(id,"A".repeat(43));runCurrent();vm.accept(id,"invalid");assertTrue(vm.state.value.loading)
        gate.complete(s.session.value!!.user.copy(role=Role.SELLER));advanceUntilIdle();assertEquals(1,calls);assertNotNull(vm.message.value)
    }
    @Test fun receiptInFlightOnLogoutIsRetainedAndNeverAssignedToTheNextUser()=runTest(dispatcher){
        val s=session();val gate=CompletableDeferred<Unit>();var calls=0;val pending=mutableSetOf("notice")
        val receipts=object:DeviceReceiptStore{override fun pending(uid:String)=if(uid=="uid-a")pending.toList()else emptyList();override fun record(uid:String,id:String)=Unit;override fun acknowledged(uid:String,id:String){pending.remove(id)}}
        val repo=CommercialRepository(object:TestApi(){override suspend fun deviceReceipt(id:String){calls++;gate.await()}},s,receipts=receipts)
        val job=launch{try{repo.reconcileDeviceReceipts()}catch(_:CancellationException){}}
        runCurrent();switch(s);gate.complete(Unit);job.join();repo.reconcileDeviceReceipts()
        assertEquals(1,calls);assertEquals(setOf("notice"),pending)
    }
    @Test fun sameUidNewSessionInvalidatesLoadedInvitationsEvenWhenSessionFlowIsConflated()=runTest(dispatcher){
        val s=session();val original=s.session.value!!
        val vm=SellerInvitationViewModel(AccountRepository(object:TestApi(){override suspend fun sellerInvitations()=listOf(invitation)},s))
        vm.load();advanceUntilIdle();assertEquals(listOf(invitation),vm.state.value.data)
        s.clear();s.set(original,"uid-a");runCurrent()
        assertNull(vm.state.value.data);assertNull(vm.state.value.error);assertNull(vm.message.value)
    }
    @Test fun sameUidNewSessionCanLoadWhileAnOldNonCancellableRequestIsPending()=runTest(dispatcher){
        val s=session();val original=s.session.value!!;val gate=CompletableDeferred<Unit>();var calls=0
        val vm=SellerInvitationViewModel(AccountRepository(object:TestApi(){override suspend fun sellerInvitations():List<SellerInvitation>{
            calls++;if(calls==1)withContext(NonCancellable){gate.await()};return listOf(invitation)
        }},s))
        vm.load();runCurrent();s.clear();s.set(original,"uid-a");runCurrent();vm.load();runCurrent()
        assertEquals(2,calls);assertEquals(listOf(invitation),vm.state.value.data)
        gate.complete(Unit);advanceUntilIdle();assertEquals(listOf(invitation),vm.state.value.data)
    }
    @Test fun repositoryPropagatesCancellationAndDoesNotPromoteFromACancelledAcceptance()=runTest(dispatcher){
        val s=session();val gate=CompletableDeferred<Unit>()
        val repo=AccountRepository(object:TestApi(){override suspend fun acceptInvitation(id:String,input:InvitationAcceptance):User{
            withContext(NonCancellable){gate.await()};return s.session.value!!.user.copy(role=Role.SELLER)
        }},s)
        val job=async{repo.acceptInvitation(UUID.randomUUID().toString(),"A".repeat(43))}
        runCurrent();job.cancel();gate.complete(Unit);advanceUntilIdle()
        assertTrue(job.isCancelled);assertEquals(Role.BUYER,s.session.value!!.user.role)
    }
    @Test fun cancelledListClearsLoadingWithoutTurningCancellationIntoAnError()=runTest(dispatcher){
        val vm=SellerInvitationViewModel(AccountRepository(object:TestApi(){override suspend fun sellerInvitations():List<SellerInvitation>{throw CancellationException("fixture")}},session()))
        vm.load();advanceUntilIdle();assertFalse(vm.state.value.loading);assertNull(vm.state.value.error);assertNull(vm.state.value.data)
    }
    @Test fun lateAcceptanceCannotPromoteTheNextIdentityOrPublishSuccess()=runTest(dispatcher){
        val s=session();val original=s.session.value!!.user;val gate=CompletableDeferred<Unit>()
        val vm=SellerInvitationViewModel(AccountRepository(object:TestApi(){override suspend fun acceptInvitation(id:String,input:InvitationAcceptance):User{
            withContext(NonCancellable){gate.await()};return original.copy(role=Role.SELLER)
        }},s))
        vm.accept(UUID.randomUUID().toString(),"A".repeat(43));runCurrent();switch(s);runCurrent();gate.complete(Unit);advanceUntilIdle()
        assertEquals("b",s.session.value!!.user.id);assertEquals(Role.BUYER,s.session.value!!.user.role);assertNull(vm.message.value);assertNull(vm.state.value.error)
    }
    @Test fun retryAfterIdentityChangeStartsWithEmptyStateAndUsesTheCurrentAccount()=runTest(dispatcher){
        val s=session();val callers=mutableListOf<String>();var calls=0
        val vm=SellerInvitationViewModel(AccountRepository(object:TestApi(){override suspend fun sellerInvitations():List<SellerInvitation>{
            callers+=s.firebaseUid!!;calls++;if(calls==1)throw IOException("fixture offline");return listOf(invitation)
        }},s))
        vm.load();advanceUntilIdle();assertNotNull(vm.state.value.error)
        switch(s);runCurrent();assertNull(vm.state.value.error);assertNull(vm.state.value.data)
        vm.load();advanceUntilIdle();assertEquals(listOf("uid-a","uid-b"),callers);assertEquals(listOf(invitation),vm.state.value.data)
    }
}
