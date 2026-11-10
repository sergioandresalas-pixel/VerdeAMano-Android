package com.verdeamano

import com.sun.net.httpserver.HttpServer
import com.verdeamano.contracts.*
import com.verdeamano.core.auth.*
import com.verdeamano.core.network.FirebaseHttpSession
import com.verdeamano.core.network.FirebaseCallFactory
import com.verdeamano.core.session.SessionStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.io.IOException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/** Real local HTTP, simulated Firebase token source. Exercises refresh and account-switch races. */
class FirebaseHttpSessionTest {
    @Test fun localBiometricGatePreventsPrivateHttpWhileAllowingOnlyExplicitLogoutCleanup(){
        val identity=Identity();val store=store();val attempts=AtomicInteger()
        val gate=object:com.verdeamano.core.auth.LocalUnlockGate{
            override fun enabled(uid:String)=true;override fun requiresUnlock(uid:String)=true
            override fun freshLogin(uid:String)=Unit;override fun lock(uid:String)=Unit;override fun disable(uid:String)=Unit
        }
        server({_,_->attempts.incrementAndGet();200 to "{}"}){url->
            val authentication=FirebaseHttpSession(identity,store,url,gate)
            val client=OkHttpClient.Builder().addInterceptor(authentication).callTimeout(5,TimeUnit.SECONDS).build()
            assertThrows(IOException::class.java){client.newCall(Request.Builder().url(url+"api/privacy/requests").build()).execute().close()}
            assertEquals(0,attempts.get())
            client.newCall(Request.Builder().url(url+"api/logout").post("{}".toRequestBody()).build()).execute().close()
            assertEquals(1,attempts.get())
            assertThrows(IOException::class.java){client.newCall(Request.Builder().url(url+"api/logout").build()).execute().close()}
            assertEquals(1,attempts.get())
        }
    }
    private class Identity : IdentityClient {
        @Volatile override var current:IdentityUser?=IdentityUser("uid-a","a@verdeamano.test","A",setOf("password"))
        val refreshes=AtomicInteger()
        override suspend fun signIn(email:String,password:String){}
        override suspend fun register(name:String,email:String,password:String){}
        override suspend fun google(token:String){}
        override suspend fun linkGoogle(token:String){}
        override suspend fun token(forceRefresh:Boolean)=blockingToken(forceRefresh)
        override fun blockingToken(forceRefresh:Boolean):IdentityToken? {if(forceRefresh)refreshes.incrementAndGet();return current?.let{IdentityToken(it.uid,if(forceRefresh) "fresh" else "old","future")}}
        override suspend fun resetPassword(email:String){}
        override suspend fun sendEmailVerification(){}
        override suspend fun reloadIdentity(){}
        override fun signOut(){current=null}
        override fun observeIdentity(listener:(String?)->Unit)=AutoCloseable{}
    }
    private fun store()=SessionStore().also{it.set(Session("unused",User("profile-a","A","a@verdeamano.test",Role.BUYER),"future",false))}
    private fun server(reply:(Int,String?)->Pair<Int,String>,test:(String)->Unit) {
        val attempts=AtomicInteger();val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/"){exchange->val(status,body)=reply(attempts.incrementAndGet(),exchange.requestHeaders.getFirst("Authorization"));val bytes=body.toByteArray();exchange.sendResponseHeaders(status,bytes.size.toLong());exchange.responseBody.use{it.write(bytes)}}
        server.start()
        try{test("http://127.0.0.1:${server.address.port}/")}finally{server.stop(0)}
    }
    private fun client(identity:Identity,store:SessionStore,origin:String):OkHttpClient {
        val authentication=FirebaseHttpSession(identity,store,origin)
        return OkHttpClient.Builder().addInterceptor(authentication).authenticator(authentication).callTimeout(10,TimeUnit.SECONDS).build()
    }
    @Test fun `401 refreshes Firebase token once and keeps profile after success`() {
        val identity=Identity();val store=store()
        server({attempt,header->if(attempt==1){assertEquals("Bearer old",header);401 to "{}"}else{assertEquals("Bearer fresh",header);200 to "{}"}}){url->
            client(identity,store,url).newCall(Request.Builder().url(url).build()).execute().use{assertEquals(200,it.code)}
        }
        assertEquals(1,identity.refreshes.get());assertNotNull(store.session.value)
    }
    @Test fun `repeated 401 stops after one refresh and clears current session`() {
        val identity=Identity();val store=store();val attempts=AtomicInteger()
        server({_,_->attempts.incrementAndGet();401 to "{}"}){url->client(identity,store,url).newCall(Request.Builder().url(url).build()).execute().close()}
        assertEquals(2,attempts.get());assertEquals(1,identity.refreshes.get());assertNull(store.session.value);assertNull(identity.current)
    }
    @Test fun `late 401 from previous user cannot sign out the new account`() {
        val identity=Identity();val store=store();val received=CountDownLatch(1);val release=CountDownLatch(1)
        val executor=Executors.newSingleThreadExecutor()
        try {
            server({_,_->received.countDown();check(release.await(5,TimeUnit.SECONDS));401 to "{}"}){url->
                val request=executor.submit{client(identity,store,url).newCall(Request.Builder().url(url).build()).execute().close()}
                check(received.await(5,TimeUnit.SECONDS))
                store.clear();identity.current=IdentityUser("uid-b","b@verdeamano.test","B",setOf("password"));store.set(Session("b",User("profile-b","B","b@verdeamano.test",Role.BUYER),"future",false))
                release.countDown();request.get(5,TimeUnit.SECONDS)
            }
            assertEquals("uid-b",identity.current?.uid);assertEquals("profile-b",store.session.value?.user?.id);assertEquals(0,identity.refreshes.get())
        } finally {release.countDown();executor.shutdownNow()}
    }
    @Test fun `forbidden operation keeps session but disabled account clears it`() {
        val identity=Identity();val store=store()
        server({_,_->403 to """{"code":"forbidden","message":"No permission"}"""}){url->client(identity,store,url).newCall(Request.Builder().url(url).build()).execute().close()}
        assertNotNull(store.session.value)
        server({_,_->403 to """{"code":"account_disabled","message":"Disabled"}"""}){url->client(identity,store,url).newCall(Request.Builder().url(url).build()).execute().close()}
        assertNull(store.session.value);assertNull(identity.current)
    }
    @Test fun `queued request from previous account cannot execute with the new Firebase token`() {
        val identity=Identity();val store=store();val attempts=AtomicInteger()
        server({_,_->attempts.incrementAndGet();200 to "{}"}){url->
            val factory=FirebaseCallFactory(client(identity,store,url),identity,store)
            val oldRequest=factory.newCall(Request.Builder().url(url).build())
            store.clear();identity.current=IdentityUser("uid-b","b@verdeamano.test","B",setOf("password"));store.set(Session("b",User("profile-b","B","b@verdeamano.test",Role.BUYER),"future",false))
            try{oldRequest.execute().close();fail()}catch(_:IOException){}
            assertEquals(0,attempts.get());assertEquals("profile-b",store.session.value?.user?.id)
        }
    }
    @Test fun `profile owner mismatch blocks HTTP before a delayed Firebase listener can clear it`() {
        val identity=Identity();val store=store();val attempts=AtomicInteger()
        store.set(store.session.value!!,"uid-a")
        identity.current=IdentityUser("uid-b","b@verdeamano.test","B",setOf("password"))
        server({_,_->attempts.incrementAndGet();200 to "{}"}){url->
            val call=FirebaseCallFactory(client(identity,store,url),identity,store).newCall(Request.Builder().url(url).build())
            try{call.execute().close();fail()}catch(_:IOException){}
            assertEquals(0,attempts.get())
        }
    }
    @Test fun `external image never receives Firebase token and cannot refresh or clear session`() {
        val identity=Identity();val store=store();val attempts=AtomicInteger()
        server({_,header->assertNull(header);attempts.incrementAndGet();401 to "{}"}){url->
            client(identity,store,"http://127.0.0.1:1/").newCall(Request.Builder().url(url).header("Authorization","Bearer must-not-leave").build()).execute().use{assertEquals(401,it.code)}
        }
        assertEquals(1,attempts.get());assertEquals(0,identity.refreshes.get());assertNotNull(identity.current);assertNotNull(store.session.value)
    }
}
