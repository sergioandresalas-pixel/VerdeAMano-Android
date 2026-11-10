package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.data.AccountRepository
import com.verdeamano.core.session.SessionStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AccountStateTest {
    private val user=User("a","Persona","a@verdeamano.test",Role.BUYER)
    private fun session()=SessionStore().apply{set(Session("test",user,"future",false),"uid-a")}
    @Test fun `profile validation permits optional phone but rejects invalid required name`() {assertNull(AccountValidation.profile("Persona",null));assertNull(AccountValidation.profile("Persona","+573001234567"));assertNotNull(AccountValidation.profile(" ",null));assertNotNull(AccountValidation.profile("Persona","abc"))}
    @Test fun `address requires complete coordinates valid fields and bounded text`() {val a=AddressInput("Persona","3001234567","Antioquia","Medellín","Calle 10 # 20",requestId="key");assertNull(AccountValidation.address(a));assertNotNull(AccountValidation.address(a.copy(longitude=1.0)));assertNotNull(AccountValidation.address(a.copy(latitude=Double.NaN,longitude=1.0)));assertNotNull(AccountValidation.address(a.copy(phone="")));assertNotNull(AccountValidation.address(a.copy(instructions="x".repeat(1001))))}
    @Test fun `stale profile response cannot restore previous account after logout`()=runTest{val pending=CompletableDeferred<ProfileDetails>();val api=object:TestApi(){override suspend fun profile()=pending.await()};val store=session();val task=async{AccountRepository(api,store).profile()};yield();store.clear();pending.complete(ProfileDetails(user,null,"now","now",1));try{task.await();fail("Old account response must be discarded")}catch(_:CancellationException){};assertNull(store.session.value)}
    @Test fun `profile updates refresh name while preserving identity token and role`()=runTest{val updated=user.copy(name="Nombre nuevo");val api=object:TestApi(){override suspend fun profile()=ProfileDetails(updated,"3001234567","now","now",2)};val store=session();AccountRepository(api,store).profile();assertEquals(updated,store.session.value?.user);assertEquals("uid-a",store.firebaseUid);assertEquals("test",store.session.value?.token)}
    @Test fun `role change invalidates private responses and connection errors retain session`()=runTest{val store=session();val before=store.version;store.updateProfile(user.copy(role=Role.SELLER));assertTrue(store.version>before);val api=object:TestApi(){override suspend fun preferences():Preferences=throw java.io.IOException("offline")};try{AccountRepository(api,store).preferences();fail("Expected connection error")}catch(_:java.io.IOException){};assertEquals(Role.SELLER,store.session.value?.user?.role)}
}
