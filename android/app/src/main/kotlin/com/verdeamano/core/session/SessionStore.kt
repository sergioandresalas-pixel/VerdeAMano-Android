package com.verdeamano.core.session

import com.verdeamano.contracts.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicLong

// Profile/token snapshots are memory-only. Firebase owns identity recovery and token renewal.
@Singleton class SessionStore @Inject constructor() {
    private val current = MutableStateFlow<Session?>(null)
    val session = current.asStateFlow()
    private val revision = AtomicLong()
    private val scope = MutableStateFlow<Pair<String?,Long>>(null to 0L)
    val scopeChanges = scope.asStateFlow()
    @Volatile private var identityUid: String? = null
    val firebaseUid get() = identityUid
    val version get() = revision.get()
    @Synchronized fun set(session: Session, firebaseUid: String? = null) { if (identityUid != firebaseUid || current.value?.user?.id != session.user.id || current.value?.user?.role != session.user.role || current.value?.user?.active != session.user.active) revision.incrementAndGet(); identityUid=firebaseUid; current.value = session; scope.value=identityUid to revision.get() }
    @Synchronized fun updateProfile(user: com.verdeamano.contracts.User) { current.value?.takeIf{it.user.id==user.id}?.let{set(it.copy(user=user),identityUid)} }
    @Synchronized fun clear() { revision.incrementAndGet(); identityUid=null; current.value = null; scope.value=null to revision.get() }
}
