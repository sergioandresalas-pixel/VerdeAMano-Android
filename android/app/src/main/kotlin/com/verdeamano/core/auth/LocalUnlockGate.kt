package com.verdeamano.core.auth

interface LocalUnlockGate {
    /** A verified server profile authorizes opting in only while this identity is not already protected. */
    fun verifiedProfile(uid:String){if(!enabled(uid))freshLogin(uid)}
    fun enabled(uid:String):Boolean
    fun requiresUnlock(uid:String):Boolean
    fun freshLogin(uid:String)
    fun lock(uid:String)
    fun disable(uid:String)
}
object NoLocalUnlockGate:LocalUnlockGate {
    override fun enabled(uid:String)=false
    override fun requiresUnlock(uid:String)=false
    override fun freshLogin(uid:String)=Unit
    override fun lock(uid:String)=Unit
    override fun disable(uid:String)=Unit
}
