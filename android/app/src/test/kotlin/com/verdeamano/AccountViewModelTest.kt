package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import com.verdeamano.core.session.SessionStore
import com.verdeamano.feature.admin.AdminViewModel
import com.verdeamano.feature.profile.AccountViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val user=User("a","Persona","a@verdeamano.test",Role.BUYER)
    private val profile=ProfileDetails(user,null,"now","now",1)
    private open inner class AccountsApi:TestApi(){override suspend fun profile()=profile;override suspend fun addresses()=emptyList<Address>();override suspend fun preferences()=Preferences();override suspend fun privacyRequests()=emptyList<PrivacyRequest>();override suspend fun consents()=emptyList<ConsentRecord>()}
    private fun vm(api:TestApi)=AccountViewModel(AccountRepository(api,SessionStore().apply{set(Session("test",user,"future",false),"uid-a")}))
    @Before fun setup(){Dispatchers.setMain(dispatcher)}
    @After fun cleanup(){Dispatchers.resetMain()}
    @Test fun protectedAccountNeverRequestsAnAccessChange() = runTest(dispatcher) {
        val target = profile.copy(user = user.copy(id = "protected", role = Role.ADMIN))
        val api = object : TestApi() {
            override suspend fun userPage(page: Int, size: Int, q: String?, role: String?, active: Boolean?) =
                UserPage(emptyList(), 0, page, size)
            override suspend fun auditPage(page: Int, size: Int, q: String?, result: String?, user: String?) =
                AuditPage(emptyList(), 0, page, size)
            override suspend fun userDetails(id: String) = UserDetails(target, emptyList(), true, true, emptyList())
            override suspend fun userAccess(id: String, input: AccessUpdate): ProfileDetails {
                fail("A protected account must never request a write")
                return target
            }
        }
        val admin = SessionStore().apply { set(Session("test", user.copy(role = Role.ADMIN), "future", false), "admin") }
        val model = AdminViewModel(AccountRepository(api, admin))
        model.select(target.user.id)
        advanceUntilIdle()
        model.access(target.user.id, Role.BUYER, false)
        advanceUntilIdle()
        assertEquals(target, model.selected.value.data?.profile)
        assertNull(model.message.value)
    }
    @Test fun `load exposes loading then an owned profile and an empty address collection`()=runTest(dispatcher){val response=CompletableDeferred<ProfileDetails>();val model=vm(object:AccountsApi(){override suspend fun profile()=response.await()});runCurrent();assertTrue(model.state.value.loading);response.complete(profile);advanceUntilIdle();assertEquals(user,model.state.value.data!!.profile.user);assertTrue(model.state.value.data!!.addresses.isEmpty());assertFalse(model.state.value.loading)}
    @Test fun `connection error is distinct from empty data and safe retry loads`()=runTest(dispatcher){var offline=true;val model=vm(object:AccountsApi(){override suspend fun profile():ProfileDetails{if(offline)throw java.io.IOException("private technical details");return profile}});advanceUntilIdle();assertNull(model.state.value.data);assertNotNull(model.state.value.error);assertFalse(model.state.value.error!!.contains("private"));offline=false;model.load();advanceUntilIdle();assertNull(model.state.value.error);assertNotNull(model.state.value.data)}
    @Test fun `mutation retries retain same request id after an uncertain connection result`()=runTest(dispatcher){val ids=mutableListOf<String>();val model=vm(object:AccountsApi(){override suspend fun updateProfile(input:ProfileUpdate):ProfileDetails{ids.add(input.requestId);if(ids.size==1)throw java.io.IOException("uncertain response");return profile.copy(user=user.copy(name=input.name),version=2)}});advanceUntilIdle();model.profile("Nombre actualizado","");advanceUntilIdle();assertNotNull(model.state.value.error);model.profile("Nombre actualizado","");advanceUntilIdle();assertEquals(2,ids.size);assertEquals(ids[0],ids[1]);assertEquals("Cambios guardados.",model.message.value)}
    @Test fun `invalid phone prevents writes and repeated clicks cannot overlap writes`()=runTest(dispatcher){var calls=0;val finish=CompletableDeferred<ProfileDetails>();val model=vm(object:AccountsApi(){override suspend fun updateProfile(input:ProfileUpdate):ProfileDetails{calls++;return finish.await()}});advanceUntilIdle();model.profile("Persona","invalid");advanceUntilIdle();assertEquals(0,calls);model.profile("Persona nueva","3001234567");model.profile("Persona nueva","3001234567");runCurrent();assertEquals(1,calls);finish.complete(profile);advanceUntilIdle();assertFalse(model.state.value.loading)}
}
