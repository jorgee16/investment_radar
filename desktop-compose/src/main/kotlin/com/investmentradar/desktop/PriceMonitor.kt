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
import java.net.URLEncoder
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

@Composable internal fun PriceMonitorPanel(bridge:NativeBridge,watchSymbols:List<String> = emptyList(),portfolioSymbols:List<String> = emptyList()){
 var snapshot by remember{mutableStateOf(JsonObject(emptyMap()))};var error by remember{mutableStateOf("")}
 var search by remember{mutableStateOf("")};var selected by remember{mutableStateOf<JsonObject?>(null)}
 var matches by remember{mutableStateOf<List<JsonObject>>(emptyList())}
 var searchMode by remember{mutableStateOf("")}
 var newId by remember{mutableStateOf("")};var newName by remember{mutableStateOf("")}
 var newIsin by remember{mutableStateOf("")};var newMic by remember{mutableStateOf("")}
 var newCurrency by remember{mutableStateOf("")}
 var target by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var last by remember{mutableStateOf(false)};var cross by remember{mutableStateOf(false)};var editId by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope()
 suspend fun refresh(){snapshot=Json.parseToJsonElement(bridge.get("/api/monitor")).jsonObject}
 LaunchedEffect(Unit){while(isActive){try{refresh();error=""}catch(_:Exception){error="Monitor indisponível. Verifica o host C++."};delay(2000)}}
 fun action(id:String,a:String){scope.launch{try{bridge.post("/api/monitor/action",buildJsonObject{put("id",id);put("action",a)}.toString());refresh()}catch(_:Exception){error="Não foi possível alterar o alerta."}}}
 val instruments=snapshot["instruments"]?.jsonArray?.map{it.jsonObject}.orEmpty()
 val quotes=snapshot["quotes"]?.jsonArray?.map{it.jsonObject}.orEmpty()
 val preferred=(watchSymbols+portfolioSymbols).toSet()
 val listingMic: (String)->String = { v ->
     when(v.trim().uppercase()){
         "NYSE","XNYS"->"XNYS"
         "NASDAQ","XNAS"->"XNAS"
         "ARCA","NYSE ARCA","ARCX"->"ARCX"
         "AMEX","NYSE AMERICAN","XASE"->"XASE"
         "BATS"->"BATS"
         "XETRA","XETR"->"XETR"
         "PARIS","EURONEXT PARIS","XPAR"->"XPAR"
         else->v.trim().uppercase().takeIf{it.length==4}.orEmpty()
     }
 }
 fun register(){
     scope.launch{
         try{
             val registered=Json.parseToJsonElement(bridge.post("/api/monitor/instruments",buildJsonObject{
                 put("id",newId.trim().uppercase());put("name",newName.trim())
                 put("isin",newIsin.trim().uppercase());put("mic",newMic.trim().uppercase())
                 put("currency",newCurrency.trim().uppercase())
             }.toString())).jsonObject
             refresh();selected=registered;error="Instrumento registado como não verificado. Sem cotações reais até validar fonte e permissões."
         }catch(_:Exception){error="Falha ao registar: confirma o ticker, ISIN, MIC, moeda e identidade da listagem."}
     }
 }
 LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Text("Alertas de preço",style=MaterialTheme.typography.headlineSmall);Text("O PC deve ficar ligado e sem suspensão. Fechar a janela mantém o monitor na área de notificação quando disponível.")}
  item{Card{Column(Modifier.padding(12.dp)){Text("Fonte: "+(snapshot["health"]?.jsonObject?.text("status")?:"a iniciar"));Text(snapshot["health"].toString(),style=MaterialTheme.typography.bodySmall);Text("IEX: uma praça americana, não preço consolidado. Europa: acesso não verificado. Sem ativação automática de fontes pagas.")}}}
  item{if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)}
  item{Text("Carteira e favoritos",style=MaterialTheme.typography.titleMedium)}
  item{Text("Podes selecionar qualquer instrumento já registado; os que não têm feed confirmado ficam assinalados como indisponíveis.",style=MaterialTheme.typography.bodySmall)}
  items(instruments.filter{it.text("id") in preferred}){i->
    TextButton(onClick={selected=i}){Text("${i.text("id")} · ${i.text("name")} · ${i.text("currency")} · ${i.text("source")}")}}
  item{OutlinedTextField(search,{search=it},label={Text("Pesquisar nome, ticker ou ISIN")},modifier=Modifier.fillMaxWidth())}
  item{Row{
     Button(enabled=search.trim().length>=2,onClick={scope.launch{
       try{
         val query=URLEncoder.encode(search.trim(),"UTF-8")
         val response=Json.parseToJsonElement(bridge.get("/api/monitor/search?query=$query")).jsonObject
         matches=response["results"]?.jsonArray?.map{it.jsonObject}.orEmpty()
         searchMode=if(response["demo"]?.jsonPrimitive?.booleanOrNull==true)
           "Pesquisa em DEMO — resultados não confirmam o instrumento nem a fonte." else
           "Metadados de pesquisa — confirma a bolsa, ISIN e moeda antes de registar."
       }catch(_:Exception){searchMode="Pesquisa indisponível. Podes introduzir os dados manualmente.";matches=emptyList()}
     }){Text("Pesquisar catálogo")}
  }}
  item{if(searchMode.isNotBlank())Text(searchMode,style=MaterialTheme.typography.bodySmall)}
  items(matches){hit->
    val symbol=hit.text("symbol").uppercase()
    TextButton(onClick={
      newId=symbol;newName=hit.text("name")
      newIsin=hit.text("isin").uppercase();newCurrency=hit.text("currency").uppercase()
      newMic=listingMic(hit.text("exchange"));selected=null
    }){Text("${hit.text("name")} · $symbol · ${hit.text("exchange")} · ${hit.text("currency")} · ${hit.text("isin")}")}}
  item{Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
    Text("Registar cotação concreta",style=MaterialTheme.typography.titleMedium)
    Text("Os metadados da pesquisa não garantem acesso à bolsa. Confirma a listagem e a moeda; nenhum ETF é automaticamente substituído.",style=MaterialTheme.typography.bodySmall)
    OutlinedTextField(newId,{newId=it},label={Text("Ticker da listagem, por exemplo MSFT ou VUAA.DE")})
    OutlinedTextField(newName,{newName=it},label={Text("Nome do instrumento")})
    OutlinedTextField(newIsin,{newIsin=it},label={Text("ISIN (opcional se não confirmado)")})
    Row{
      OutlinedTextField(newMic,{newMic=it},label={Text("MIC, por exemplo XETR")},modifier=Modifier.weight(1f))
      Spacer(Modifier.width(8.dp))
      OutlinedTextField(newCurrency,{newCurrency=it},label={Text("Moeda, por exemplo EUR")},modifier=Modifier.weight(1f))
    }
    Button(onClick={register()},enabled=newId.isNotBlank()&&newName.isNotBlank()&&newMic.length==4&&newCurrency.length==3){Text("Adicionar ao monitor (não verificado)")}
  }}}
  item{Text("Instrumentos registados",style=MaterialTheme.typography.titleMedium)}
  items(instruments.filter{(it.text("name")+it.text("id")+it.text("isin")).contains(search,true)}){i->
     Column {
       TextButton(onClick={selected=i}){Text("${if(selected?.text("id")==i.text("id")) "✓ " else ""}${i.text("name")} · ${i.text("id")} · ${i.text("currency")} · ${i.text("mic")} · ${i.text("source")}") }
       if(i.text("source")=="unverified"&&i.text("currency")=="USD"&&i.text("mic") in setOf("XNYS","XNAS","ARCX","XASE","BATS")){
         TextButton(onClick={scope.launch{
             try{
               bridge.post("/api/monitor/verify-iex",buildJsonObject{put("symbol",i.text("id"))}.toString())
               refresh();selected=snapshot["instruments"]?.jsonArray?.map{it.jsonObject}?.firstOrNull{it.text("id")==i.text("id")}
               error="Símbolo verificado no catálogo Alpaca. O acesso a cotações IEX continua sujeito a permissões reais."
             }catch(_:Exception){error="Verificação Alpaca indisponível ou símbolo/bolsa incompatível. Requer autorização local."}
         }){Text("Verificar símbolo na Alpaca (IEX gratuito, opcional)")}
       }
     }
  }
  item{Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text(selected?.let{"${it.text("id")} · ${it.text("isin")} · ${it.text("source")}; fonte sem validação de feed não dispara alertas"}?:"Seleciona uma cotação concreta")
   OutlinedTextField(target,{target=it},label={Text("Preço limite na moeda da cotação (ponto decimal)")})
   Row{Checkbox(last,{last=it});Text("Usar LAST explicitamente em vez de ASK (sem fallback automático)")}
   Row{Checkbox(cross,{cross=it});Text("Exigir cruzamento observado; sem marcação: igual ou abaixo")}
   OutlinedTextField(email,{email=it},label={Text("Email opcional — envio exige configuração local TLS")})
   Button(enabled=selected!=null,onClick={scope.launch{try{bridge.post("/api/monitor/rules",buildJsonObject{put("id",editId);put("instrument",selected!!.text("id"));put("target",target);put("email",email);put("basis",if(last)"LAST" else "ASK");put("mode",if(cross)"CROSS" else "LEVEL")}.toString());editId="";error="";refresh()}catch(_:Exception){error="Não guardado: verifica preço, email e instrumento."}}}){Text(if(editId.isEmpty())"Criar alerta" else "Guardar e rearmar")}
  }}}
  items(snapshot["rules"]?.jsonArray?.map{it.jsonObject}.orEmpty()){r->Card{Column(Modifier.padding(12.dp)){
   Text("${r.text("instrument")} · ≤ ${units(r["targetUnits"])} ${r.text("currency")}",style=MaterialTheme.typography.titleMedium)
   Text("${r.text("basis")} · ${r.text("venue")} · ${r.text("mode")} · ${if(r["armed"]?.jsonPrimitive?.booleanOrNull==true)"Armado" else "Pausado / disparado"}")
   val quote=quotes.firstOrNull{it.text("instrument")==r.text("instrument")}
   val observed=quote?.get(if(r.text("basis")=="ASK")"askUnits" else "lastUnits")
   val age=quote?.get(if(r.text("basis")=="LAST")"lastAgeSeconds" else "ageSeconds")?.jsonPrimitive?.longOrNull
   val maxAge=r["maxAge"]?.jsonPrimitive?.longOrNull?:60L
   val price=observed?.jsonPrimitive?.longOrNull
   val threshold=r["targetUnits"]?.jsonPrimitive?.longOrNull
   val percent=if(price!=null&&threshold!=null&&threshold>0)
       "%.2f%%".format(java.util.Locale.ROOT, (price.toDouble()/threshold-1.0)*100.0) else "—"
   Text("Cotação ${r.text("basis")}: ${units(observed)} ${r.text("currency")} · distância ${percent}")
   Text("Feed: ${quote?.text("feed")?:r.text("feed")} / ${quote?.text("venue")?:r.text("venue")} · hora bolsa ${quote?.get(if(r.text("basis")=="LAST")"lastTime" else "exchangeTime")?.jsonPrimitive?.longOrNull?.takeIf{it>0}?.let{java.time.Instant.ofEpochSecond(it).toString()}?:"indisponível"}",
        style=MaterialTheme.typography.bodySmall)
   Text("Qualidade: ${if(quote==null)"Sem observações" else if(age==null)"Timestamp inválido" else if(age>maxAge)"COTAÇÃO ANTIGA (${age}s)" else if(quote.text("session")!="open")"Mercado fechado" else if(quote["entitled"]?.jsonPrimitive?.booleanOrNull!=true)"Sem permissão confirmada" else "Observação recebida há ${age}s"}",
        style=MaterialTheme.typography.bodySmall)
   Row{TextButton(onClick={editId=r.text("id");selected=instruments.find{it.text("id")==r.text("instrument")};target=units(r["targetUnits"]);email=r.text("email");last=r.text("basis")=="LAST";cross=r.text("mode")=="CROSS"}){Text("Editar")};for(a in listOf("pause","rearm","delete"))TextButton(onClick={action(r.text("id"),a)}){Text(mapOf("pause" to "Pausar","rearm" to "Rearmar","delete" to "Eliminar")[a]!!)}}
  }}}
  item{Text("Entregas: aceitação pelo serviço não confirma receção na caixa de email.");Text(snapshot["deliveries"]?.toString()?:"Sem entregas");Text("Histórico de eventos");Text(snapshot["events"]?.toString()?:"Sem eventos")}
 }
}
