package com.streambox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.streambox.app.data.ApiClient
import com.streambox.app.data.CredentialsDto
import com.streambox.app.data.TokenStore
import kotlinx.coroutines.launch
import retrofit2.HttpException

private val Neon = Color(0xFF00D65A)
private val Deep = Color(0xFF050B08)
private val Panel = Color(0xFF0B1710)
private val Muted = Color(0xFF9BA79F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 11, 8)
        window.navigationBarColor = android.graphics.Color.rgb(5, 11, 8)
        val tokenStore = TokenStore(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Neon, background = Deep, surface = Panel,
                onPrimary = Color.Black, onBackground = Color.White, onSurface = Color.White
            )) {
                var loggedIn by remember { mutableStateOf(false) }
                var checkingSession by remember { mutableStateOf(true) }
                var currentEmail by remember { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    val saved = tokenStore.get()
                    if (!saved.isNullOrBlank()) {
                        try {
                            val user = ApiClient.api.me("Bearer $saved")
                            currentEmail = user.email
                            loggedIn = true
                        } catch (_: Exception) {
                            tokenStore.clear()
                        }
                    }
                    checkingSession = false
                }

                if (checkingSession) {
                    Box(Modifier.fillMaxSize().background(Deep), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Neon)
                    }
                } else if (loggedIn) {
                    HomeScreen(email = currentEmail, tokenStore = tokenStore, onLogout = {
                        kotlinx.coroutines.MainScope().launch {
                            tokenStore.clear()
                            loggedIn = false
                            currentEmail = ""
                        }
                    })
                } else {
                    AuthScreen(tokenStore = tokenStore, onAuthenticated = { email ->
                        currentEmail = email
                        loggedIn = true
                    })
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(tokenStore: TokenStore, onAuthenticated: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10251A), Deep)))) {
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 430.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("STREAMBOX", color = Neon, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("Seu entretenimento em um só lugar", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (isRegister) "Criar sua conta" else "Entrar na sua conta",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("E-mail") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha (mínimo 8 caracteres)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                    if (message.isNotBlank()) {
                        Text(message, color = if (message.startsWith("Conta") || message.startsWith("Login")) Neon else Color(0xFFFF8A80),
                            fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                message = ""
                                try {
                                    val credentials = CredentialsDto(email.trim(), password)
                                    val token = if (isRegister) ApiClient.api.register(credentials)
                                                else ApiClient.api.login(credentials)
                                    tokenStore.save(token.access_token)
                                    val user = ApiClient.api.me("Bearer ${token.access_token}")
                                    message = if (isRegister) "Conta criada com sucesso" else "Login realizado"
                                    onAuthenticated(user.email)
                                } catch (e: HttpException) {
                                    message = when (e.code()) {
                                        409 -> "Este e-mail já está cadastrado."
                                        401 -> "E-mail ou senha incorretos."
                                        422 -> "Confira o e-mail e use uma senha com pelo menos 8 caracteres."
                                        else -> "Não foi possível concluir. Código ${e.code()}."
                                    }
                                    tokenStore.clear()
                                } catch (_: Exception) {
                                    message = "Não foi possível conectar. Verifique se a API está ligada e acessível."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && email.contains("@") && password.length >= 8,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(if (isRegister) "CRIAR CONTA" else "ENTRAR", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { isRegister = !isRegister; message = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isRegister) "Já tenho conta — entrar" else "Ainda não tenho conta — cadastrar")
                    }
                    Text("Conexão com API e PostgreSQL", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(email: String, tokenStore: TokenStore, onLogout: () -> Unit) {
    var selected by remember { mutableStateOf("Início") }
    val items = listOf("Início" to "⌂", "Esportes" to "⚽", "TV ao vivo" to "▣",
        "Filmes" to "▷", "Séries" to "▤", "Recentes" to "◷", "Configurações" to "⚙")
    Row(Modifier.fillMaxSize().background(Deep)) {
        Column(Modifier.width(76.dp).fillMaxHeight().background(Color(0xFF03170B)).padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { (label, glyph) ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp)
                    .background(if (selected == label) Color(0xFF123D20) else Color.Transparent, RoundedCornerShape(13.dp))
                    .clickable { selected = label }.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(glyph, color = Neon, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("●", color = Neon, fontSize = 17.sp)
                Spacer(Modifier.width(9.dp))
                Text("STREAMBOX", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                Spacer(Modifier.weight(1f))
                Text(email, color = Muted, fontSize = 11.sp)
                Spacer(Modifier.width(12.dp))
                Text("Sair", color = Neon, modifier = Modifier.clickable { onLogout() })
            }
            Spacer(Modifier.height(22.dp))
            Text(selected.uppercase(), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (selected == "TV ao vivo") {
                PlaylistManager(tokenStore)
            } else {
            Box(Modifier.fillMaxWidth().heightIn(min = 170.dp, max = 245.dp)
                .border(2.dp, Neon.copy(alpha = .75f), RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF123D24), Color(0xFF07120B), Color(0xFF183B25))),
                    RoundedCornerShape(8.dp)).padding(22.dp)) {
                Column(Modifier.align(Alignment.CenterStart)) {
                    Text("DESTAQUES", color = Neon, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Seu conteúdo começa aqui", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Text("Banners e programações da sua lista autorizada", color = Color(0xFFD0D9D2), fontSize = 12.sp)
                }
                Text("▶", color = Color.White, fontSize = 25.sp,
                    modifier = Modifier.align(Alignment.CenterEnd).background(Color.Black.copy(alpha = .35f), CircleShape).padding(14.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text("EXPLORE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("TV ao vivo", "Filmes", "Séries").forEach { title ->
                    Card(modifier = Modifier.weight(1f).height(92.dp).clickable { selected = title },
                        colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(13.dp)) {
                        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.Center) {
                            Text("▣", color = Neon, fontSize = 22.sp)
                            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("CONTA CONECTADA", color = Neon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Sessão autenticada para $email", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PlaylistManager(tokenStore: TokenStore) {
    var token by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("Minha lista") }
    var content by remember { mutableStateOf("") }
    var playlists by remember { mutableStateOf(emptyList<com.streambox.app.data.PlaylistDto>()) }
    var channels by remember { mutableStateOf(emptyList<com.streambox.app.data.ChannelDto>()) }
    var selectedPlaylist by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf("Importe uma lista M3U autorizada em texto.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        token = tokenStore.get()
        if (!token.isNullOrBlank()) {
            try { playlists = ApiClient.api.playlists("Bearer $token") }
            catch (_: Exception) { message = "Não foi possível carregar suas listas." }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SUAS LISTAS M3U", color = Neon, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nome da lista") }, singleLine = true)
        OutlinedTextField(content, { content = it }, modifier = Modifier.fillMaxWidth().height(150.dp),
            label = { Text("Cole aqui o conteúdo M3U (#EXTM3U...)") })
        Button(onClick = {
            scope.launch {
                busy = true
                try {
                    val body = content.toRequestBody("text/plain; charset=utf-8".toMediaType())
                    val result = ApiClient.api.importPlaylist("Bearer $token", name, body)
                    playlists = ApiClient.api.playlists("Bearer $token")
                    content = ""
                    message = "Lista '${result.name}' importada: ${result.channel_count} canais."
                } catch (e: Exception) { message = "Falha ao importar. Confira se o M3U está válido e a API acessível." }
                finally { busy = false }
            }
        }, enabled = !busy && !token.isNullOrBlank() && content.trimStart().startsWith("#EXTM3U") && name.isNotBlank()) {
            Text(if (busy) "IMPORTANDO..." else "IMPORTAR LISTA")
        }
        Text(message, color = Muted, fontSize = 12.sp)
        playlists.forEach { playlist ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(playlist.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${playlist.channel_count} canais", color = Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Ver canais", color = Neon, modifier = Modifier.clickable {
                            scope.launch {
                                try {
                                    channels = ApiClient.api.channels("Bearer $token", playlist.id)
                                    selectedPlaylist = playlist.id
                                } catch (_: Exception) { message = "Erro ao abrir canais." }
                            }
                        })
                        Text("Excluir", color = Color(0xFFFF8A80), modifier = Modifier.clickable {
                            scope.launch {
                                try {
                                    ApiClient.api.deletePlaylist("Bearer $token", playlist.id)
                                    playlists = ApiClient.api.playlists("Bearer $token")
                                    if (selectedPlaylist == playlist.id) { selectedPlaylist = null; channels = emptyList() }
                                } catch (_: Exception) { message = "Não foi possível excluir a lista." }
                            }
                        })
                    }
                }
            }
        }
        if (selectedPlaylist != null) {
            Text("CANAIS", color = Neon, fontWeight = FontWeight.Bold)
            channels.forEach { channel ->
                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).padding(10.dp)) {
                    Text(channel.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(channel.group_title ?: "Sem categoria", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}
