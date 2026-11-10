package com.verdeamano

import com.verdeamano.contracts.*
import com.verdeamano.core.data.AccountRepository
import com.verdeamano.core.data.CommercialRepository
import com.verdeamano.core.session.SessionStore
import com.verdeamano.feature.commercial.CommercialViewModel
import com.verdeamano.feature.profile.PrivacyReviewViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyRetentionStateTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    private fun adminSession() = SessionStore().apply {
        set(Session("admin-token", User("admin-id", "Admin QA", "admin@verdeamano.test", Role.ADMIN), "future", false), "uid-admin")
    }

    private val sampleEvaluation = UserRetentionEvaluation(
        userId = "buyer-123",
        activeOrdersCount = 0,
        completedOrdersCount = 2,
        openClaimsCount = 0,
        activeCartItemsCount = 0,
        verdict = RetentionEvaluationVerdict.RETENCION_PARCIAL,
        summaryJustification = "Deber legal de conservación sobre compras históricas.",
        categories = listOf(
            CategoryRetentionAssessment(
                category = DataCategory.COMMERCIAL_ORDERS,
                status = EvaluationStatus.CHECKED_WITH_RECORDS,
                recordCount = 2,
                obligationStatus = RetentionObligationStatus.MANDATORY_LEGAL_GROUND_IDENTIFIED,
                legalGround = "Código de Comercio Art. 60 y Estatuto Tributario Art. 632",
                observation = "Dos órdenes completadas."
            ),
            CategoryRetentionAssessment(
                category = DataCategory.AUTH_CREDENTIALS,
                status = EvaluationStatus.EXTERNAL_VERIFICATION_REQUIRED,
                recordCount = null,
                obligationStatus = RetentionObligationStatus.PENDING_LEGAL_REVIEW,
                legalGround = "Firebase Authentication externo",
                observation = "Requiere verificación independiente."
            )
        )
    )

    private val sampleRequest = PrivacyRequest(
        id = "req-1",
        type = PrivacyRequestType.DELETION,
        note = "Solicito supresión",
        status = "OPEN",
        createdAt = "2026-10-13T20:00:00Z",
        version = 1,
        ownerName = "Comprador QA"
    )

    @Test
    fun adminRetentionEvaluationIsLoadedWhenAdminSelectsRequest() = runTest(dispatcher) {
        val api = object : TestApi() {
            override suspend fun privacyDetail(id: String) = sampleRequest
            override suspend fun privacyResponsibles() = listOf(User("admin-id", "Admin QA", "admin@verdeamano.test", Role.ADMIN))
            override suspend fun privacyRetentionEvaluation(id: String) = sampleEvaluation
        }
        val accountRepo = AccountRepository(api, adminSession())
        val vm = PrivacyReviewViewModel(accountRepo)

        vm.select("req-1", admin = true)
        advanceUntilIdle()

        assertNotNull(vm.detail.value.data)
        assertEquals("req-1", vm.detail.value.data!!.id)
        assertNotNull(vm.retentionEvaluation.value.data)
        val eval = vm.retentionEvaluation.value.data!!
        assertEquals(RetentionEvaluationVerdict.RETENCION_PARCIAL, eval.verdict)
        assertEquals(2, eval.completedOrdersCount)

        // Verify un-evaluated / external category has null recordCount
        val authCat = eval.categories.single { it.category == DataCategory.AUTH_CREDENTIALS }
        assertEquals(EvaluationStatus.EXTERNAL_VERIFICATION_REQUIRED, authCat.status)
        assertNull(authCat.recordCount)
    }

    @Test
    fun retentionEvaluationFailureDoesNotBlockDetailLoading() = runTest(dispatcher) {
        val api = object : TestApi() {
            override suspend fun privacyDetail(id: String) = sampleRequest
            override suspend fun privacyResponsibles() = emptyList<User>()
            override suspend fun privacyRetentionEvaluation(id: String): UserRetentionEvaluation = throw IOException("Service offline")
        }
        val accountRepo = AccountRepository(api, adminSession())
        val vm = PrivacyReviewViewModel(accountRepo)

        vm.select("req-1", admin = true)
        advanceUntilIdle()

        // Detail succeeds even if retention diagnostic fails
        assertNotNull(vm.detail.value.data)
        assertNull(vm.retentionEvaluation.value.data)
        assertNotNull(vm.retentionEvaluation.value.error)
        assertFalse(vm.retentionEvaluation.value.loading)
    }

    @Test
    fun commercialViewModelLoadsAdminPoliciesSuccessfully() = runTest(dispatcher) {
        val api = object : TestApi() {
            override suspend fun adminClaimPolicies() = ClaimKind.entries.map{CommercialPolicyDefinition("qa-$it",it,"QA_DRAFT","Propuesta QA",PolicyLifecycleStatus.PROPOSED_PENDING_APPROVAL,"QA", "QA",emptyList(),emptyList(),"QA","QA",emptyList())}
        }
        val commercialRepo = CommercialRepository(api, adminSession())
        val vm = CommercialViewModel(commercialRepo)

        vm.adminPolicies()
        advanceUntilIdle()

        assertEquals(5, vm.state.value.adminPolicies.size)
        val retracto = vm.state.value.adminPolicies.single { it.kind == ClaimKind.RETRACTO }
        assertEquals(PolicyLifecycleStatus.PROPOSED_PENDING_APPROVAL, retracto.status)
    }
    @Test fun diagnosticErrorCanRetryWithoutDiscardingTheRequest()=runTest(dispatcher){
        var attempts=0
        val api=object:TestApi(){
            override suspend fun privacyDetail(id:String)=sampleRequest
            override suspend fun privacyResponsibles()=emptyList<User>()
            override suspend fun privacyRetentionEvaluation(id:String):UserRetentionEvaluation{attempts++;if(attempts==1)throw IOException("offline");return sampleEvaluation}
        }
        val vm=PrivacyReviewViewModel(AccountRepository(api,adminSession()));vm.select("req-1",true);advanceUntilIdle()
        assertNotNull(vm.retentionEvaluation.value.error);assertEquals("req-1",vm.detail.value.data!!.id)
        vm.retryEvaluation();advanceUntilIdle();assertEquals(2,attempts);assertEquals(sampleEvaluation,vm.retentionEvaluation.value.data);assertNull(vm.retentionEvaluation.value.error)
    }
    @Test fun diagnosticCancellationDoesNotBecomeAnErrorOrPublishData()=runTest(dispatcher){
        val api=object:TestApi(){override suspend fun privacyDetail(id:String)=sampleRequest;override suspend fun privacyResponsibles()=emptyList<User>();override suspend fun privacyRetentionEvaluation(id:String):UserRetentionEvaluation=throw CancellationException("Account changed")}
        val vm=PrivacyReviewViewModel(AccountRepository(api,adminSession()));vm.select("req-1",true);advanceUntilIdle()
        assertNull(vm.retentionEvaluation.value.data);assertNull(vm.retentionEvaluation.value.error);assertFalse(vm.retentionEvaluation.value.loading)
    }
    @Test fun lateDiagnosticFromPreviousAccountCannotAppearInTheNextAccount()=runTest(dispatcher){
        val session=adminSession();val response=CompletableDeferred<UserRetentionEvaluation>()
        val api=object:TestApi(){override suspend fun privacyDetail(id:String)=sampleRequest;override suspend fun privacyResponsibles()=emptyList<User>();override suspend fun privacyRetentionEvaluation(id:String)=response.await()}
        val vm=PrivacyReviewViewModel(AccountRepository(api,session));vm.select("req-1",true);runCurrent()
        session.clear();session.set(Session("new",User("other","Other","other@test.test",Role.ADMIN),"future",false),"other-uid");response.complete(sampleEvaluation);advanceUntilIdle()
        assertNull(vm.retentionEvaluation.value.data);assertNull(vm.detail.value.data);assertTrue(vm.responsibles.value.isEmpty())
    }
    @Test fun changingRequestCancelsOldDiagnosticAndClearsItsState()=runTest(dispatcher){
        val old=CompletableDeferred<UserRetentionEvaluation>()
        val api=object:TestApi(){override suspend fun privacyDetail(id:String)=sampleRequest.copy(id=id);override suspend fun privacyResponsibles()=emptyList<User>();override suspend fun privacyRetentionEvaluation(id:String)=if(id=="first")old.await() else sampleEvaluation.copy(userId="second-owner")}
        val vm=PrivacyReviewViewModel(AccountRepository(api,adminSession()));vm.select("first",true);runCurrent();vm.select("second",true);advanceUntilIdle();old.complete(sampleEvaluation);advanceUntilIdle()
        assertEquals("second",vm.detail.value.data!!.id);assertEquals("second-owner",vm.retentionEvaluation.value.data!!.userId)
    }
    @Test fun buyerSelectionNeverRequestsAdministrativeEvaluation()=runTest(dispatcher){
        var calls=0;val api=object:TestApi(){override suspend fun privacyDetail(id:String)=sampleRequest;override suspend fun privacyRetentionEvaluation(id:String):UserRetentionEvaluation{calls++;return sampleEvaluation}}
        val vm=PrivacyReviewViewModel(AccountRepository(api,adminSession()));vm.select("req-1",false);advanceUntilIdle();vm.retryEvaluation();advanceUntilIdle()
        assertEquals(0,calls);assertNull(vm.retentionEvaluation.value.data)
    }
    @Test fun publicGuidanceIncludesPerishablesWithoutStoreDraftsOrAutomaticPromises(){
        assertTrue(ClaimGuidance.text(ClaimKind.GARANTIA).contains("perecederos"));assertTrue(ClaimGuidance.text(ClaimKind.GARANTIA).contains("expiración"))
        assertTrue(ClaimGuidance.notice.contains("no es una política comercial aprobada"))
        assertFalse(ClaimGuidance.text(ClaimKind.DEVOLUCION).contains("5 días"))
    }
}
