package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.auth.*
import com.verdeamano.core.session.SessionStore
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Simulated identity/API responses test orchestration, never count as real Firebase login. */
class AuthRepositoryTest {
    @Test fun restoredUnprotectedSessionCanEnrollOnlyAfterServerProfileVerification()=runTest {
        val identity=FakeIdentity();identity.signIn(seller.email,"Test-only")
        var protected=false;var allowed:String?=null;var reads=0
        val gate=object:LocalUnlockGate{
            override fun enabled(uid:String)=protected;override fun requiresUnlock(uid:String)=protected&&allowed!=uid
            override fun freshLogin(uid:String){allowed=uid};override fun lock(uid:String){allowed=null};override fun disable(uid:String){protected=false}
        }
        val api=object:TestApi(){override suspend fun me():User{assertNull(allowed);reads++;return seller}}
        val r=AuthRepository(identity,api,SessionStore(),localGate=gate);r.restore();assertEquals(seller.email,allowed);assertEquals(1,reads)
        protected=true;r.lockLocalSession();try{r.restore();fail()}catch(e:AuthFailure){assertEquals("local_unlock_required",e.code)}
        assertNull(allowed);assertEquals(1,reads)
    }
    @Test fun biometricLockBlocksRestorationAndFreshLoginRemainsAvailable()=runTest {
        val identity=FakeIdentity();val store=SessionStore();var unlocked:String?=null;var reads=0;var logouts=0
        val gate=object:LocalUnlockGate{
            override fun enabled(uid:String)=true;override fun requiresUnlock(uid:String)=unlocked!=uid
            override fun freshLogin(uid:String){unlocked=uid};override fun lock(uid:String){if(unlocked==uid)unlocked=null};override fun disable(uid:String)=Unit
        }
        val api=object:TestApi(){override suspend fun firebaseProfile(input:ProfileInput)=seller;override suspend fun me():User{reads++;return seller};override suspend fun logout(){logouts++}}
        val r=AuthRepository(identity,api,store,localGate=gate)
        r.login(seller.email,"Test-password-only");r.lockLocalSession();assertNull(store.session.value);assertNotNull(identity.current)
        try{r.restore();fail("Must require local unlock")}catch(e:AuthFailure){assertEquals("local_unlock_required",e.code)}
        assertEquals(0,reads);r.login(seller.email,"Test-password-only");assertEquals(seller,store.session.value!!.user)
        r.lockLocalSession();r.logout();assertEquals(1,logouts);assertNull(identity.current)
    }
    private class FakeIdentity : IdentityClient {
        override var current: IdentityUser? = null
        private var identityChanged: ((String?) -> Unit)? = null
        var tokenFailure: AuthFailure? = null
        override suspend fun signIn(email: String,password: String) { current=IdentityUser(email,email,"Comprador",setOf("password")) }
        override suspend fun register(name: String,email: String,password: String) { current=IdentityUser(email,email,name,setOf("password")) }
        override suspend fun google(token: String) { current=IdentityUser("google-uid","google@verdeamano.test","Google",setOf("google.com")) }
        override suspend fun linkGoogle(token: String) { current=current?.copy(providers=current!!.providers+"google.com") }
        override suspend fun token(forceRefresh: Boolean):IdentityToken? {tokenFailure?.let{throw it};return blockingToken(forceRefresh)}
        override fun blockingToken(forceRefresh: Boolean)=current?.let {IdentityToken(it.uid,"test-token-${it.uid}","2026-10-12T12:00:00Z")}
        override suspend fun resetPassword(email: String) {}
        override suspend fun sendEmailVerification() {}
        override suspend fun reloadIdentity() { current=current?.copy(emailVerified=true) }
        override fun signOut() { current=null;identityChanged?.invoke(null) }
        override fun observeIdentity(listener: (String?) -> Unit): AutoCloseable { identityChanged=listener;return AutoCloseable{} }
        fun changeIdentity(uid:String) { current=current?.copy(uid=uid);identityChanged?.invoke(uid) }
    }
    private val seller=User("seller","Vendedor","vendedor@verdeamano.test",Role.SELLER)
    @Test fun `email verification refresh preserves profile role and never marks the local account verified before Firebase reload`()=runTest {
        val identity=FakeIdentity();val store=SessionStore()
        val api=object:TestApi(){override suspend fun firebaseProfile(input:ProfileInput)=seller;override suspend fun me()=seller}
        val repository=AuthRepository(identity,api,store)
        repository.login(seller.email,"OnlyForAutomatedTests123")
        repository.sendVerification()
        assertFalse(repository.emailVerified)
        val uid=store.firebaseUid
        repository.refreshVerification()
        assertTrue(repository.emailVerified);assertEquals(uid,store.firebaseUid);assertEquals(Role.SELLER,store.session.value?.user?.role)
        identity.changeIdentity("different-uid")
        try {repository.sendVerification();fail()}catch(failure:AuthFailure){assertEquals("session_invalid",failure.code)}
    }
    private fun failure(code: Int,message: String)=HttpException(Response.error<User>(code,message.toResponseBody()))
    @Test fun `backend profile determines role and Firebase token is memory only`()=runTest {
        val identity=FakeIdentity();val store=SessionStore()
        val api=object:TestApi(){override suspend fun firebaseProfile(input: ProfileInput):User {assertFalse(input.acceptedTerms);return seller}}
        AuthRepository(identity,api,store).login(seller.email,"OnlyForAutomatedTests123")
        assertEquals(Role.SELLER,store.session.value?.user?.role)
        assertFalse(store.session.value!!.development)
        assertEquals("test-token-${seller.email}",store.session.value!!.token)
    }
    @Test fun `profile network failure keeps Firebase identity for recovery without duplicate registration`()=runTest {
        val identity=FakeIdentity();val store=SessionStore();var unavailable=true
        val api=object:TestApi(){override suspend fun firebaseProfile(input:ProfileInput):User {if(unavailable)throw failure(503,"{}");return seller};override suspend fun me()=seller}
        val repository=AuthRepository(identity,api,store)
        try {repository.register("Nombre",seller.email,"OnlyForAutomatedTests123");fail()}catch(_:HttpException){}
        assertTrue(repository.hasIdentity);assertNull(store.session.value)
        unavailable=false;repository.restore()
        assertEquals(seller,store.session.value?.user)
    }
    @Test fun `Google first access stops for profile consent while keeping authenticated identity`()=runTest {
        val identity=FakeIdentity();val store=SessionStore()
        val api=object:TestApi(){override suspend fun firebaseProfile(input:ProfileInput):User {
            if(!input.acceptedTerms)throw failure(409,"""{"code":"terms_required","message":"Consent needed"}""")
            return User("buyer","Nombre","google@verdeamano.test",Role.BUYER)
        }}
        val repository=AuthRepository(identity,api,store)
        try {repository.google("google-credential",false);fail()}catch(error:AuthFailure){assertEquals("terms_required",error.code)}
        assertTrue(repository.hasIdentity);assertNull(store.session.value)
        repository.finishProfile("Nombre",true)
        assertEquals(Role.BUYER,store.session.value?.user?.role)
    }
    @Test fun `logout clears identity and profile even when API is offline`()=runTest {
        val identity=FakeIdentity();val store=SessionStore()
        val api=object:TestApi(){override suspend fun firebaseProfile(input:ProfileInput)=seller;override suspend fun logout():Unit=throw IOException("offline")}
        val repository=AuthRepository(identity,api,store);repository.login(seller.email,"OnlyForAutomatedTests123")
        try{repository.logout();fail()}catch(_:IOException){}
        assertNull(store.session.value);assertNull(identity.current)
    }
    @Test fun `account switch replaces previous private profile and token`()=runTest {
        val identity=FakeIdentity();val store=SessionStore()
        val api=object:TestApi(){override suspend fun firebaseProfile(input:ProfileInput)=User(identity.current!!.uid,"Comprador",identity.current!!.email!!,Role.BUYER);override suspend fun logout(){}}
        val repository=AuthRepository(identity,api,store)
        repository.login("uno@verdeamano.test","OnlyForAutomatedTests123");val previousVersion=store.version
        repository.logout();repository.login("dos@verdeamano.test","OnlyForAutomatedTests456")
        assertEquals("dos@verdeamano.test",store.session.value?.user?.email)
        assertFalse(store.session.value!!.token.contains("uno@"));assertTrue(store.version>previousVersion)
    }
    @Test fun `authenticated Google linking preserves backend identity and role`()=runTest {
        val identity=FakeIdentity();val store=SessionStore()
        val api=object:TestApi(){override suspend fun firebaseProfile(input:ProfileInput)=seller;override suspend fun me()=seller}
        val repository=AuthRepository(identity,api,store);repository.login(seller.email,"OnlyForAutomatedTests123")
        val originalUid=identity.current!!.uid
        repository.linkGoogle("fresh-google-token")
        assertEquals(originalUid,identity.current!!.uid);assertEquals(Role.SELLER,store.session.value?.user?.role)
        assertTrue(repository.providers.contains("google.com"))
    }
    @Test fun `registration validates confirmation email length and consent`() {
        assertNull(AuthValidation.error("Nombre","buyer@verdeamano.test","OnlyForAutomatedTests123","OnlyForAutomatedTests123",true))
        assertNotNull(AuthValidation.error("Nombre","buyer@verdeamano.test","short","short",true))
        assertNotNull(AuthValidation.error("Nombre","invalid","OnlyForAutomatedTests123","OnlyForAutomatedTests123",true))
        assertNotNull(AuthValidation.error("Nombre","buyer@verdeamano.test","OnlyForAutomatedTests123","other",true))
        assertNotNull(AuthValidation.error("Nombre","buyer@verdeamano.test","OnlyForAutomatedTests123","OnlyForAutomatedTests123",false))
    }
    @Test fun `invalid persistent Firebase session is cleared before returning to login`()=runTest {
        val identity=FakeIdentity();val store=SessionStore();identity.signIn(seller.email,"OnlyForAutomatedTests123")
        store.set(Session("old",seller,"old",false));identity.tokenFailure=AuthFailure("session_invalid","Disabled")
        val repository=AuthRepository(identity,TestApi(),store)
        try{repository.restore();fail()}catch(_:AuthFailure){}
        assertNull(identity.current);assertNull(store.session.value)
    }
    @Test fun `inactive backend profile cannot remain authenticated`()=runTest {
        val identity=FakeIdentity();val store=SessionStore()
        val api=object:TestApi(){override suspend fun firebaseProfile(input:ProfileInput)=seller.copy(active=false)}
        try{AuthRepository(identity,api,store).login(seller.email,"OnlyForAutomatedTests123");fail()}catch(_:AuthFailure){}
        assertNull(identity.current);assertNull(store.session.value)
    }
    @Test fun `unexpected Firebase uid change immediately discards the previous private profile`()=runTest {
        val identity=FakeIdentity();val store=SessionStore()
        val api=object:TestApi(){override suspend fun firebaseProfile(input:ProfileInput)=seller}
        AuthRepository(identity,api,store).login(seller.email,"OnlyForAutomatedTests123")
        assertNotNull(store.session.value)
        identity.changeIdentity("another-firebase-uid")
        assertNull(store.session.value);assertNull(store.firebaseUid)
    }
}
