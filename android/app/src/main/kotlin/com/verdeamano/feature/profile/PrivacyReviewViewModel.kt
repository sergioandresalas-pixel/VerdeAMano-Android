package com.verdeamano.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verdeamano.contracts.*
import com.verdeamano.core.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel class PrivacyReviewViewModel @Inject constructor(private val repository:AccountRepository):ViewModel(){
    private val mutable=MutableStateFlow(UiState<PrivacyRequestPage>())
    val page=mutable.asStateFlow()
    private val selected=MutableStateFlow(UiState<PrivacyRequest>())
    val detail=selected.asStateFlow()
    private val people=MutableStateFlow<List<User>>(emptyList())
    val responsibles=people.asStateFlow()
    private val evalState=MutableStateFlow(UiState<UserRetentionEvaluation>())
    val retentionEvaluation=evalState.asStateFlow()
    private var filter:PrivacyStatus?=null
    private var pending:Pair<String,PrivacyReview>?=null
    private var selection:Pair<String,Boolean>?=null
    private var selectionStamp:Pair<String?,Long>?=null
    private var generation=0L
    private var detailJob:Job?=null
    private var evaluationJob:Job?=null
    fun list(status:PrivacyStatus?,page:Int=1){if(mutable.value.loading)return;filter=status;mutable.value=UiState(loading=true);viewModelScope.launch{try{mutable.value=UiState(repository.privacyPage(page,status))}catch(e:CancellationException){throw e}catch(e:Exception){mutable.value=UiState(error=e.userMessage())}}}
    fun next(delta:Int){list(filter,(page.value.data?.page?:1)+delta)}
    fun select(id:String,admin:Boolean){
        val current=++generation
        detailJob?.cancel();evaluationJob?.cancel()
        selection=id to admin;selectionStamp=repository.scopeStamp();pending=null
        selected.value=UiState(loading=true);people.value=emptyList();evalState.value=UiState()
        detailJob=viewModelScope.launch{
            try{
                val request=repository.privacyDetail(id)
                val responsible=if(admin)repository.privacyResponsibles() else emptyList()
                ensureActive();checkScope(current)
                people.value=responsible;selected.value=UiState(request)
                if(admin)retryEvaluation()
            }catch(e:CancellationException){
                if(generation==current){selected.value=UiState();evalState.value=UiState();people.value=emptyList()}
                throw e
            }catch(e:Exception){if(generation==current)selected.value=UiState(error=e.userMessage())}
        }
    }
    private fun checkScope(current:Long){
        if(current!=generation||selectionStamp!=repository.scopeStamp())throw CancellationException("Privacy selection or identity changed")
    }
    fun retryEvaluation(){
        val (id,admin)=selection?:return
        if(!admin||selected.value.data?.id!=id||evalState.value.loading)return
        val current=generation
        evaluationJob?.cancel();evalState.value=UiState(loading=true)
        evaluationJob=viewModelScope.launch{
            try{
                checkScope(current)
                val value=repository.privacyRetentionEvaluation(id)
                ensureActive();checkScope(current);evalState.value=UiState(value)
            }catch(e:CancellationException){
                if(generation==current){evalState.value=UiState();if(selectionStamp!=repository.scopeStamp()){selected.value=UiState();people.value=emptyList()}}
                throw e
            }catch(e:Exception){if(generation==current)evalState.value=UiState(error="No pudimos consultar el diagnóstico de conservación. ${e.userMessage()}")}
        }
    }
    fun review(status:PrivacyStatus,response:String,policy:String,assigned:String?){
        val request=detail.value.data?:return;if(detail.value.loading)return
        if(response.trim().length !in 10..2000||policy.trim().length !in 10..1000){selected.value=selected.value.copy(error="Escribe una respuesta y una decisión manual de conservación de al menos 10 caracteres.");return}
        val candidate=PrivacyReview(status,response.trim(),policy.trim(),assigned,request.version,"")
        val signature="${request.id}:$candidate"
        val input=if(pending?.first==signature)requireNotNull(pending).second else candidate.copy(requestId=UUID.randomUUID().toString()).also{pending=signature to it}
        selected.value=selected.value.copy(loading=true,error=null)
        val current=generation
        viewModelScope.launch{try{val value=repository.reviewPrivacy(request.id,input);ensureActive();checkScope(current);selected.value=UiState(value);pending=null;evalState.value=UiState();if(selection?.second==true)retryEvaluation()}catch(e:CancellationException){if(generation==current){selected.value=UiState();evalState.value=UiState()};throw e}catch(e:Exception){if(generation==current)selected.value=selected.value.copy(loading=false,error=e.userMessage())}}
    }
}
