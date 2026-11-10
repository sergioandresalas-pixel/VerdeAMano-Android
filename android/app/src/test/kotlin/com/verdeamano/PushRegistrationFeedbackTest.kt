package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.data.CommercialRepository
import com.verdeamano.core.session.SessionStore
import com.verdeamano.feature.commercial.CommercialViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrationFeedbackTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){Dispatchers.resetMain()}
    private fun session()=SessionStore().apply{
        set(Session("fixture",User("buyer","QA","qa@test.test",Role.BUYER),"future",false),"fixture-uid")
    }
    @Test fun confirmationRequiresBackendAcknowledgementAndRepeatedRegistrationRemainsSafe()=runTest(dispatcher){
        val response=CompletableDeferred<Unit>();var calls=0
        val vm=CommercialViewModel(CommercialRepository(object:TestApi(){override suspend fun registerPush(input:PushRegistration){calls++;response.await()}},session()))
        vm.push("synthetic-registration");runCurrent();assertFalse(vm.state.value.pushRegistered)
        vm.push("synthetic-registration");assertEquals(1,calls)
        response.complete(Unit);advanceUntilIdle();assertTrue(vm.state.value.pushRegistered)
        vm.push("synthetic-registration");advanceUntilIdle();assertEquals(2,calls);assertTrue(vm.state.value.pushRegistered)
    }
    @Test fun readOnlyRejectionNeverClaimsRegistrationOrLeaksServerDiagnostics()=runTest(dispatcher){
        val vm=CommercialViewModel(CommercialRepository(object:TestApi(){override suspend fun registerPush(input:PushRegistration){throw HttpException(Response.error<Unit>(403,"invalid private diagnostic".toResponseBody()))}},session()))
        vm.push("synthetic-registration");advanceUntilIdle()
        assertFalse(vm.state.value.pushRegistered);assertFalse(vm.state.value.loading)
        assertEquals("No tienes permiso para esta operación.",vm.state.value.error)
    }
    @Test fun accountChangeDiscardsLateRegistrationConfirmation()=runTest(dispatcher){
        val session=session();val response=CompletableDeferred<Unit>()
        val vm=CommercialViewModel(CommercialRepository(object:TestApi(){override suspend fun registerPush(input:PushRegistration){withContext(NonCancellable){response.await()}}},session))
        vm.push("synthetic-registration");runCurrent();session.clear();runCurrent()
        response.complete(Unit);advanceUntilIdle()
        assertFalse(vm.state.value.pushRegistered);assertFalse(vm.state.value.loading);assertNull(vm.state.value.error)
    }
}
