package com.verdeamano

import android.content.Context
import com.verdeamano.contracts.*
import com.verdeamano.core.auth.*
import com.verdeamano.core.session.SessionStore
import com.verdeamano.feature.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setDispatcher(){Dispatchers.setMain(dispatcher)}
    @After fun clearDispatcher(){Dispatchers.resetMain()}
    private class Identity : IdentityClient {
        override var current:IdentityUser?=null
        var recoveryRequests=0
        override suspend fun signIn(email:String,password:String){current=IdentityUser("uid",email,"Nombre",setOf("password"))}
        override suspend fun register(name:String,email:String,password:String){}
        override suspend fun google(token:String){}
        override suspend fun linkGoogle(token:String){}
        override suspend fun token(forceRefresh:Boolean)=blockingToken(forceRefresh)
        override fun blockingToken(forceRefresh:Boolean)=current?.let{IdentityToken(it.uid,"test-token","future")}
        override suspend fun resetPassword(email:String){recoveryRequests++}
        override suspend fun sendEmailVerification(){}
        override suspend fun reloadIdentity(){}
        override fun signOut(){current=null}
        override fun observeIdentity(listener:(String?)->Unit)=AutoCloseable{}
    }
    private class Google(private val failCleanup:Boolean=false) : GoogleCredentialClient {
        override suspend fun select(activityContext:Context):String=error("Not part of this test")
        override suspend fun clear(){if(failCleanup)throw IOException("provider unavailable")}
    }
    private val buyer=User("profile","Nombre","buyer@verdeamano.test",Role.BUYER)
    @Test fun `identity lost during restoration returns to login instead of leaving an infinite restoration screen`()=runTest(dispatcher) {
        val identity=Identity();val store=SessionStore()
        identity.signIn(buyer.email,"OnlyForAutomatedTests123")
        val repository=AuthRepository(identity,object:TestApi(){override suspend fun me():User{identity.signOut();return buyer}},store)
        val viewModel=AuthViewModel(repository,Google(),store)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.restoring);assertFalse(viewModel.state.value.loading)
        assertTrue(viewModel.state.value.showLogin);assertNull(store.session.value)
    }
    @Test fun `successful recovery after logout preserves login navigation and exposes its result`()=runTest(dispatcher) {
        val identity=Identity();val store=SessionStore()
        val repository=AuthRepository(identity,object:TestApi(){override suspend fun logout(){}},store)
        val viewModel=AuthViewModel(repository,Google(),store)
        advanceUntilIdle()
        store.set(Session("token",buyer,"future",false),"uid");runCurrent()
        viewModel.logout();advanceUntilIdle()
        assertTrue(viewModel.state.value.showLogin)
        viewModel.reset(buyer.email);advanceUntilIdle()
        assertEquals(1,identity.recoveryRequests)
        assertTrue(viewModel.state.value.showLogin)
        assertNotNull(viewModel.state.value.message)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
    }
    @Test fun `logout clears private identity and profile even if Google cleanup fails`()=runTest(dispatcher) {
        val identity=Identity();val store=SessionStore()
        val repository=AuthRepository(identity,object:TestApi(){override suspend fun logout(){}},store)
        val viewModel=AuthViewModel(repository,Google(true),store)
        advanceUntilIdle()
        identity.signIn(buyer.email,"OnlyForAutomatedTests123")
        store.set(Session("token",buyer,"future",false),"uid");runCurrent()
        viewModel.logout();advanceUntilIdle()
        assertNull(identity.current);assertNull(store.session.value);assertNull(store.firebaseUid)
        assertTrue(viewModel.state.value.showLogin);assertNotNull(viewModel.state.value.message)
        assertFalse(viewModel.state.value.loading)
    }
}
