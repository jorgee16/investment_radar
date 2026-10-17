package com.investmentradar.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

interface NativeBridge:AutoCloseable { suspend fun get(path:String):String }

class LoopbackNativeBridge:NativeBridge {
    private val started=AtomicBoolean(false)
    private var process:Process?=null
    private var port:Int=0

    private fun candidateHosts():List<File> {
        val env=System.getenv("IR_NATIVE_HOST")?.takeIf{it.isNotBlank()}?.let(::File)
        val commandDir=ProcessHandle.current().info().command().orElse(null)?.let{File(it).parentFile}
        val cwd=File(System.getProperty("user.dir"))
        val windows=System.getProperty("os.name").lowercase().contains("win")
        val exe=if(windows) "investment-radar.exe" else "investment-radar"
        val coreHost=if(windows) "investment-radar-core-host.exe" else "investment-radar-core-host"
        return listOfNotNull(env,commandDir?.resolve(coreHost),commandDir?.resolve(exe),cwd.resolve(coreHost),cwd.resolve(exe),
            cwd.resolve("../build/windows/Release/$exe"),cwd.resolve("../build/linux/$exe"),cwd.resolve("../build/headless/$exe"))
            .distinctBy{it.absolutePath}
    }

    private suspend fun ensureStarted()=withContext(Dispatchers.IO) {
        if(started.get()) return@withContext
        val host=candidateHosts().firstOrNull{it.isFile} ?: error("Native C++ host not found. Build the C++ core or set IR_NATIVE_HOST.")
        ServerSocket(0).use{port=it.localPort}
        process=ProcessBuilder(host.absolutePath,"--headless","--port",port.toString()).redirectErrorStream(true).start()
        repeat(80) {
            if(runCatching{request("/api/status")}.isSuccess){started.set(true);return@withContext}
            if(process?.isAlive==false) error("Native C++ host exited during startup.")
            delay(50)
        }
        error("Native C++ host did not become ready.")
    }

    override suspend fun get(path:String):String {
        ensureStarted()
        return withContext(Dispatchers.IO){request(path)}
    }

    private fun request(path:String):String {
        val url=URI("http://127.0.0.1:$port$path").toURL()
        val connection=(url.openConnection() as HttpURLConnection).apply{
            connectTimeout=3000;readTimeout=15000;requestMethod="GET";setRequestProperty("Host","127.0.0.1:$port")
        }
        val code=connection.responseCode
        val stream=if(code in 200..299) connection.inputStream else connection.errorStream
        val body=stream?.readBytes()?.toString(StandardCharsets.UTF_8) ?: ""
        connection.disconnect()
        if(code !in 200..299) error("Native request failed ($code)")
        return body
    }

    override fun close(){process?.destroy();process=null;started.set(false)}
}
