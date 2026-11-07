package com.investmentradar.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.investmentradar.desktop.data.NativeBridge
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal fun JsonObject.text(key:String)=get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
internal fun units(value:JsonElement?)=value?.jsonPrimitive?.longOrNull?.let { java.math.BigDecimal.valueOf(it,6).stripTrailingZeros().toPlainString() }?:"—"

// Implicit TLS only, host verification on. Secrets remain in the launching user's environment.
// No send occurs unless both local opt-in and an email delivery are present.
internal object MonitorEmail {
 fun send(event:JsonObject){
  require(System.getenv("IR_MONITOR_EMAIL_ENABLED")=="1") { "Email disabled" }
  fun env(k:String)=System.getenv(k)?.takeIf{it.isNotBlank()}?:error("Email configuration missing")
  val host=env("IR_MONITOR_SMTP_HOST");val user=env("IR_MONITOR_SMTP_USER");val password=env("IR_MONITOR_SMTP_PASSWORD")
  val from=env("IR_MONITOR_SMTP_FROM");val to=event.text("email")
  require(listOf(from,to).all{it.matches(Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+"))})
  val raw=java.net.Socket();raw.connect(java.net.InetSocketAddress(host,465),5000)
  ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw,host,465,true) as SSLSocket).use { socket ->
   socket.soTimeout=10000
   socket.sslParameters=socket.sslParameters.apply{endpointIdentificationAlgorithm="HTTPS"}
   socket.startHandshake()
   val input=BufferedReader(InputStreamReader(socket.inputStream,Charsets.US_ASCII));val out=OutputStreamWriter(socket.outputStream,Charsets.US_ASCII)
   fun response(expected:Int){var line:String;do{line=input.readLine()?:error("SMTP disconnected");require(line.length>=3&&line.take(3).toIntOrNull()==expected){"SMTP rejected"}}while(line.length>3&&line[3]=='-')}
   fun cmd(s:String,code:Int){out.write(s+"\r\n");out.flush();response(code)}
   response(220);cmd("EHLO investment-radar.local",250)
   cmd("AUTH PLAIN "+Base64.getEncoder().encodeToString(("\u0000"+user+"\u0000"+password).toByteArray(Charsets.UTF_8)),235)
   cmd("MAIL FROM:<$from>",250);cmd("RCPT TO:<$to>",250);cmd("DATA",354)
   val body="Price alert: ${event.text("instrument")}\r\n${event.text("basis")}: ${units(event["priceUnits"])} ${event.text("currency")}\r\nFeed: ${event.text("feed")} / ${event.text("venue")}\r\nExchange timestamp: ${event.text("observedAt")}\r\nThis is a user-defined price threshold."
   out.write("From: <$from>\r\nTo: <$to>\r\nSubject: Investment Radar price alert\r\nMessage-ID: <${event.text("id").replace(":","-")}@investment-radar.local>\r\nContent-Type: text/plain; charset=us-ascii\r\n\r\n$body\r\n.\r\n");out.flush();response(250)
   // Accepted by SMTP, not proof of inbox delivery. Do not retry solely on QUIT failure.
  }
 }
}

@Composable internal fun PriceMonitorPanel(bridge:NativeBridge){
 var snapshot by remember{mutableStateOf(JsonObject(emptyMap()))};var error by remember{mutableStateOf("")}
 var search by remember{mutableStateOf("")};var selected by remember{mutableStateOf<JsonObject?>(null)}
 var target by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var last by remember{mutableStateOf(false)};var cross by remember{mutableStateOf(false)};var editId by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope()
 suspend fun refresh(){snapshot=Json.parseToJsonElement(bridge.get("/api/monitor")).jsonObject}
 LaunchedEffect(Unit){while(isActive){try{refresh();error=""}catch(_:Exception){error="Monitor indisponível. Verifica o host C++."};delay(2000)}}
 fun action(id:String,a:String){scope.launch{try{bridge.post("/api/monitor/action",buildJsonObject{put("id",id);put("action",a)}.toString());refresh()}catch(_:Exception){error="Não foi possível alterar o alerta."}}}
 val instruments=snapshot["instruments"]?.jsonArray?.map{it.jsonObject}.orEmpty()
 LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Text("Alertas de preço",style=MaterialTheme.typography.headlineSmall);Text("O PC deve ficar ligado e sem suspensão. Fechar a janela mantém o monitor na área de notificação quando disponível.")}
  item{Card{Column(Modifier.padding(12.dp)){Text("Fonte: "+(snapshot["health"]?.jsonObject?.text("status")?:"a iniciar"));Text(snapshot["health"].toString(),style=MaterialTheme.typography.bodySmall);Text("IEX: uma praça americana, não preço consolidado. Europa: acesso não verificado. Sem ativação automática de fontes pagas.")}}}
  item{if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)}
  item{OutlinedTextField(search,{search=it},label={Text("Procurar nome, ticker ou ISIN — seis instrumentos de validação")},modifier=Modifier.fillMaxWidth())}
  items(instruments.filter{(it.text("name")+it.text("id")+it.text("isin")).contains(search,true)}){i->TextButton(onClick={selected=i}){Text("${if(selected==i) "✓ " else ""}${i.text("name")} · ${i.text("id")} · ${i.text("currency")} · ${i.text("mic")}")}}
  item{Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text(selected?.let{"${it.text("id")} · ${it.text("isin")}"}?:"Seleciona uma cotação concreta")
   OutlinedTextField(target,{target=it},label={Text("Preço limite na moeda da cotação (ponto decimal)")})
   Row{Checkbox(last,{last=it});Text("Usar LAST explicitamente em vez de ASK (sem fallback automático)")}
   Row{Checkbox(cross,{cross=it});Text("Exigir cruzamento observado; sem marcação: igual ou abaixo")}
   OutlinedTextField(email,{email=it},label={Text("Email opcional — envio exige configuração local TLS")})
   Button(enabled=selected!=null,onClick={scope.launch{try{bridge.post("/api/monitor/rules",buildJsonObject{put("id",editId);put("instrument",selected!!.text("id"));put("target",target);put("email",email);put("basis",if(last)"LAST" else "ASK");put("mode",if(cross)"CROSS" else "LEVEL")}.toString());editId="";error="";refresh()}catch(_:Exception){error="Não guardado: verifica preço, email e instrumento."}}}){Text(if(editId.isEmpty())"Criar alerta" else "Guardar e rearmar")}
  }}}
  items(snapshot["rules"]?.jsonArray?.map{it.jsonObject}.orEmpty()){r->Card{Column(Modifier.padding(12.dp)){
   Text("${r.text("instrument")} · ≤ ${units(r["targetUnits"])} ${r.text("currency")}",style=MaterialTheme.typography.titleMedium)
   Text("${r.text("basis")} · ${r.text("venue")} · ${r.text("mode")} · ${if(r["armed"]?.jsonPrimitive?.booleanOrNull==true)"Armado" else "Pausado / disparado"}")
   Row{TextButton(onClick={editId=r.text("id");selected=instruments.find{it.text("id")==r.text("instrument")};target=units(r["targetUnits"]);email=r.text("email");last=r.text("basis")=="LAST";cross=r.text("mode")=="CROSS"}){Text("Editar")};for(a in listOf("pause","rearm","delete"))TextButton(onClick={action(r.text("id"),a)}){Text(mapOf("pause" to "Pausar","rearm" to "Rearmar","delete" to "Eliminar")[a]!!)}}
  }}}
  item{Text("Entregas: aceitação pelo serviço não confirma receção na caixa de email.");Text(snapshot["deliveries"]?.toString()?:"Sem entregas");Text("Histórico de eventos");Text(snapshot["events"]?.toString()?:"Sem eventos")}
 }
}
