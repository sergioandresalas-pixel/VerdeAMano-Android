package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import com.verdeamano.core.session.SessionStore
import com.verdeamano.feature.profile.SellerInvitationViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SelectiveInvitationAcceptanceTest {
    private val dispatcher=StandardTestDispatcher()
    private val buyer=User("synthetic","Synthetic QA","synthetic@example.invalid",Role.BUYER)
    private fun session()=SessionStore().apply{set(Session("synthetic-session",buyer,"future",false),"synthetic-uid")}
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){Dispatchers.resetMain()}

    @Test fun revokedInvitationShowsSafeErrorWithoutChangingBuyerOrRepeatingTheRequest()=runTest(dispatcher){
        val store=session();val previousVersion=store.version;var calls=0
        val api=object:TestApi(){override suspend fun acceptInvitation(id:String,input:InvitationAcceptance):User{
            calls++;throw HttpException(Response.error<User>(400,"{\"code\":\"invitation_invalid\",\"message\":\"La invitación no es válida o no está disponible para esta identidad.\"}".toResponseBody()))
        }}
        val vm=SellerInvitationViewModel(AccountRepository(api,store));vm.accept(UUID.randomUUID().toString(),"S".repeat(43));advanceUntilIdle()
        assertEquals(1,calls);assertEquals(buyer,store.session.value!!.user);assertEquals(previousVersion,store.version);assertEquals("synthetic-uid",store.firebaseUid)
        assertNotNull(vm.state.value.error);assertFalse(vm.state.value.error!!.contains("S".repeat(43)));assertNull(vm.message.value);assertFalse(vm.state.value.loading)
        advanceUntilIdle();assertEquals(1,calls)
    }

    @Test fun acceptedServerRoleInvalidatesAnOlderBuyerProfileResponseWithoutSigningOut()=runTest(dispatcher){
        val store=session();val gate=CompletableDeferred<Unit>();val previousVersion=store.version
        val api=object:TestApi(){
            override suspend fun profile():ProfileDetails{withContext(NonCancellable){gate.await()};return ProfileDetails(buyer,null,"now","now",1)}
            override suspend fun acceptInvitation(id:String,input:InvitationAcceptance)=buyer.copy(role=Role.SELLER)
        }
        val repo=AccountRepository(api,store);val oldRead=async{try{repo.profile();false}catch(_:CancellationException){true}};runCurrent()
        val vm=SellerInvitationViewModel(repo);vm.accept(UUID.randomUUID().toString(),"S".repeat(43));advanceUntilIdle()
        assertEquals(Role.SELLER,store.session.value!!.user.role);assertTrue(store.version>previousVersion);assertEquals("synthetic-uid",store.firebaseUid);assertNotNull(vm.message.value)
        gate.complete(Unit);advanceUntilIdle();assertTrue(oldRead.await());assertEquals(Role.SELLER,store.session.value!!.user.role);assertEquals("synthetic-session",store.session.value!!.token)
    }
}
