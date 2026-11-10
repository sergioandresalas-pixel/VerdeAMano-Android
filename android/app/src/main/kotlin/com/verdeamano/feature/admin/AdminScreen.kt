package com.verdeamano.feature.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.platform.testTag
import com.verdeamano.contracts.*
import com.verdeamano.core.design.*

@Composable fun AdminScreen(viewModel:AdminViewModel,section:String,users:()->Unit,audits:()->Unit,detail:(String)->Unit={},create:()->Unit={},products:()->Unit={},categories:()->Unit={},privacy:()->Unit={},policies:()->Unit={},invitations:()->Unit={},sales:()->Unit={}){
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember{mutableStateOf(viewModel.query)};var role by remember{mutableStateOf(viewModel.role)};var active by remember{mutableStateOf(viewModel.active)};var result by remember{mutableStateOf(viewModel.result)}
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("admin-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item(key="header"){BrandHeader(if(section=="users")"Usuarios" else if(section=="audit")"Auditoría" else "Administración",if(section=="users")"Consulta y administra accesos" else if(section=="audit")"Registro de actividad" else "Usuarios, productos y actividad")}
        if(state.loading)item(key="loading"){LoadingState()};state.error?.let{item(key="error"){ErrorState(it,viewModel::load)}}
        if(section=="dashboard"){
            state.data?.let{data->item(key="count"){MetricCard("${data.users.total}", "Usuarios registrados", Modifier.fillMaxWidth())}}
            item(key="dashboard-users"){Button(users,Modifier.fillMaxWidth(),enabled=!state.loading){Text("Consultar usuarios")}}
            item(key="dashboard-audit"){OutlinedButton(audits,Modifier.fillMaxWidth(),enabled=!state.loading){Text("Consultar auditoría")}}
            item(key="dashboard-sales"){OutlinedButton(sales,Modifier.fillMaxWidth()){Text("Reporte de ventas")}}
            item(key="dashboard-products"){OutlinedButton(products,Modifier.fillMaxWidth(),enabled=!state.loading){Text("Supervisar productos")}}
            item(key="dashboard-categories"){OutlinedButton(categories,Modifier.fillMaxWidth(),enabled=!state.loading){Text("Gestionar categorías")}}
            item(key="dashboard-privacy"){OutlinedButton(privacy,Modifier.fillMaxWidth(),enabled=!state.loading){Text("Revisar privacidad")}}
            item(key="dashboard-policies"){OutlinedButton(policies,Modifier.fillMaxWidth(),enabled=!state.loading){Text("Consultar propuestas comerciales")}}
            item(key="dashboard-invitations"){OutlinedButton(invitations,Modifier.fillMaxWidth(),enabled=!state.loading){Text("Consultar invitaciones a vendedores")}}
        }else{
            item(key="search"){OutlinedTextField(query,{query=it},label={Text(if(section=="users")"Nombre o correo" else "Acción")},modifier=Modifier.fillMaxWidth())}
            if(section=="users"){
                item(key="roles"){FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){FilterChip(role==null,{role=null},label={Text("Todos")});Role.entries.forEach{r->FilterChip(role==r.name,{role=r.name},label={Text(r.label())})}}}
                item(key="states"){FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){FilterChip(active==null,{active=null},label={Text("Cualquier estado")});FilterChip(active==true,{active=true},label={Text("Activos")});FilterChip(active==false,{active=false},label={Text("Inactivos")})}}
                item(key="create"){OutlinedButton(create){Text("Crear usuario o invitar vendedor")}}
            }else item{Row{FilterChip(result==null,{result=null},label={Text("Todos")});FilterChip(result=="SUCCESS",{result="SUCCESS"},label={Text("Aprobados")});FilterChip(result=="REJECTED",{result="REJECTED"},label={Text("Rechazados")})}}
            item(key="apply"){Button({viewModel.filter(query,role,active,result)},enabled=!state.loading){Text("Aplicar filtros")}}
            if(section=="users"){
                if(!state.loading&&state.data?.users?.items.isNullOrEmpty())item{EmptyState("No hay usuarios para estos filtros.")}
                items(state.data?.users?.items.orEmpty(),key={it.user.id}){summary->OutlinedCard(onClick={detail(summary.user.id)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(summary.user.name,style=MaterialTheme.typography.titleMedium);Text(summary.user.email);Text("Consultar y editar acceso",style=MaterialTheme.typography.labelMedium);VerdeStatusChip("${summary.user.role.label()} · ${if(summary.user.active)"Activo" else "Inactivo"}",summary.user.active)}}}
            }else{
                if(!state.loading&&state.data?.audits?.items.isNullOrEmpty())item{EmptyState("No hay acciones para estos filtros.")}
                items(state.data?.audits?.items.orEmpty(),key={it.id}){audit->AuditCard(audit)}
            }
            state.data?.let{data->val p=if(section=="audit")data.audits.page else data.users.page;val total=if(section=="audit")data.audits.total else data.users.total;val size=if(section=="audit")data.audits.pageSize else data.users.pageSize
                item{Text("Página $p · $total registros");Row{TextButton({viewModel.next(section=="audit",-1)},enabled=p>1&&!state.loading){Text("Anterior")};TextButton({viewModel.next(section=="audit",1)},enabled=p*size<total&&!state.loading){Text("Siguiente")}}}
            }
        }
    }
}
@Composable fun AdminUserScreen(viewModel:AdminViewModel,id:String){
    val state by viewModel.selected.collectAsStateWithLifecycle();val notice by viewModel.message.collectAsStateWithLifecycle();LaunchedEffect(id){viewModel.select(id)}
    var confirmation by remember(id){mutableStateOf<Pair<Role,Boolean>?>(null)}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        BrandHeader("Editar acceso","Rol, estado e historial de la cuenta")
        if(state.loading)LoadingState();state.error?.let{ErrorState(it){viewModel.select(id)}};notice?.let{Text(it)}
        state.data?.let{data->
            var role by remember(id,data.profile.version){mutableStateOf(data.profile.user.role)}
            Text(data.profile.user.name,style=MaterialTheme.typography.titleLarge);Text(data.profile.user.email);
            VerdeStatusChip("${data.profile.user.role.label()} · ${if(data.profile.user.active) "Activa" else "Inactiva"}",data.profile.user.active)
            Text("Proveedores: ${data.providers.joinToString()}");Text(if(data.emailVerified)"Correo verificado" else "Correo sin verificar");Text("Creado: ${data.profile.createdAt}")
            if(data.protectedAccount)Text("Cuenta protegida: no se permite cambiar su propio acceso ni el administrador principal.")
            val user = data.profile.user
            val editable = !state.loading && !data.protectedAccount
            FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Role.entries.forEach { option ->
                    FilterChip(role==option,{role=option},label={Text(option.label())},enabled=editable)
                }
            }
            Button({confirmation=role to user.active},enabled=editable && role!=user.role){Text("Guardar rol")}
            OutlinedButton({confirmation=user.role to !user.active},enabled=editable){
                Text(if(user.active)"Desactivar cuenta" else "Reactivar cuenta")
            }
            confirmation?.let { (newRole, active) ->
                val action = when {
                    active == user.active -> "Confirmar cambio de rol"
                    active -> "Reactivar cuenta"
                    else -> "Desactivar cuenta"
                }
                val description = if (!active && user.active) "Perderá el acceso. Sus datos e historial se conservarán."
                    else "Rol: ${newRole.label()}. Estado: ${if(active) "activo" else "inactivo"}."
                AlertDialog(
                    onDismissRequest={confirmation=null},
                    title={Text(action)},
                    text={Text("${user.name}\n$description")},
                    confirmButton={TextButton({confirmation=null;viewModel.access(id,newRole,active)},enabled=editable){Text("Confirmar")}},
                    dismissButton={TextButton({confirmation=null}){Text("Cancelar")}}
                )
            }
            Text("Historial reciente");data.history.forEach{AuditCard(it)}
        }
    }
}
@Composable fun AdminCreateScreen(viewModel:AdminViewModel){
    val state by viewModel.state.collectAsStateWithLifecycle();val notice by viewModel.message.collectAsStateWithLifecycle()
    var name by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var role by remember{mutableStateOf(Role.BUYER)};var confirm by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        BrandHeader("Crear usuario","Invita a una persona a usar Verde a Mano")
        OutlinedTextField(name,{name=it},label={Text("Nombre")},modifier=Modifier.fillMaxWidth(),enabled=!state.loading&&notice==null)
        OutlinedTextField(email,{email=it},label={Text("Correo")},modifier=Modifier.fillMaxWidth(),enabled=!state.loading&&notice==null)
        Row{listOf(Role.BUYER,Role.SELLER).forEach{r->FilterChip(role==r,{role=r},label={Text(r.label())},enabled=!state.loading&&notice==null)}}
        Text("No se asignan contraseñas compartidas ni se restablecen cuentas existentes. Para correos reales se solicitará a Firebase un enlace de activación.")
        if(state.loading)LoadingState();state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};notice?.let{Text(it)}
        Button({confirm=true},enabled=!state.loading&&notice==null){Text("Crear e invitar")}
        if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Confirmar invitación")},text={Text("Crear $email como ${role.label()} y solicitar su enlace de acceso.")},confirmButton={TextButton({confirm=false;viewModel.create(name,email,role)}){Text("Confirmar invitación")}},dismissButton={TextButton({confirm=false}){Text("Cancelar")}})
    }
}
@Composable private fun AuditCard(audit:AdministrativeAudit){OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("${audit.action} · ${audit.result}");Text("${audit.actorName} · ${audit.createdAt}");Text("Recurso: ${audit.resourceId}",style=MaterialTheme.typography.bodySmall);Text("Antes: ${audit.before}",style=MaterialTheme.typography.bodySmall);Text("Después: ${audit.after}",style=MaterialTheme.typography.bodySmall)}}}
fun Role.label():String=when(this){Role.BUYER->"Comprador";Role.SELLER->"Vendedor";Role.ADMIN->"Administrador"}
