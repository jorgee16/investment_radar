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

interface NativeBridge:AutoCloseable {
    suspend fun request(path:String, method:String="GET", body:String?=null):String
    suspend fun get(path:String):String = request(path)
    suspend fun post(path:String, body:String):String = request(path,"POST",body)
}

class LoopbackNativeBridge:NativeBridge {
    private val started=AtomicBoolean(false)
    private var process:Process?=null
    private var port:Int=0

    private fun candidateHosts():List<File> {
        val env=System.getenv("IR_NATIVE_HOST")?.takeIf{it.isNotBlank()}?.let(::File)
        val commandDir=ProcessHandle.current().info().command().orElse(null)?.let{File(it).parentFile}
        val packagedDir=System.getProperty("jpackage.app-path")?.takeIf{it.isNotBlank()}?.let{File(it).parentFile}
        val cwd=File(System.getProperty("user.dir"))
        val windows=System.getProperty("os.name").lowercase().contains("win")
        val exe=if(windows) "investment-radar.exe" else "investment-radar"
        val coreHost=if(windows) "investment-radar-core-host.exe" else "investment-radar-core-host"
        return listOfNotNull(
            env,
            packagedDir?.resolve(coreHost), packagedDir?.resolve(exe),
            commandDir?.resolve(coreHost), commandDir?.resolve(exe),
            cwd.resolve(coreHost), cwd.resolve(exe),
            cwd.resolve("../build/windows/Release/$coreHost"),
            cwd.resolve("../build/windows/Release/$exe"),
            cwd.resolve("../build/linux/$coreHost"),
            cwd.resolve("../build/linux/$exe")
        ).distinctBy{it.absolutePath}
    }

    private suspend fun ensureStarted()=withContext(Dispatchers.IO) {
        if(started.get()) return@withContext
        val host=candidateHosts().firstOrNull{it.isFile}
            ?: error("Native C++ host not found. Build the C++ core or set IR_NATIVE_HOST.")
        ServerSocket(0).use{port=it.localPort}
        process=ProcessBuilder(host.absolutePath,"--headless","--port",port.toString())
            .redirectErrorStream(true).start()
        repeat(100) {
            if(runCatching{rawRequest("/api/status","GET",null)}.isSuccess){
                started.set(true)
                return@withContext
            }
            if(process?.isAlive==false) error("Native C++ host exited during startup.")
            delay(50)
        }
        error("Native C++ host did not become ready.")
    }

    override suspend fun request(path:String,method:String,body:String?):String {
        ensureStarted()
        return withContext(Dispatchers.IO){rawRequest(path,method,body)}
    }

    private fun rawRequest(path:String,method:String,body:String?):String {
        val url=URI("http://127.0.0.1:$port$path").toURL()
        val connection=(url.openConnection() as HttpURLConnection).apply{
            connectTimeout=3000
            readTimeout=20000
            requestMethod=method
            setRequestProperty("Host","127.0.0.1:$port")
            if(body!=null){
                doOutput=true
                setRequestProperty("Content-Type","application/json")
            }
        }
        if(body!=null) connection.outputStream.use{it.write(body.toByteArray(StandardCharsets.UTF_8))}
        val code=connection.responseCode
        val stream=if(code in 200..299) connection.inputStream else connection.errorStream
        val response=stream?.readBytes()?.toString(StandardCharsets.UTF_8) ?: ""
        connection.disconnect()
        if(code !in 200..299) error(runCatching {
            val marker="\"error\":"
            if(response.contains(marker)) response else "Native request failed ($code)"
        }.getOrDefault("Native request failed ($code)"))
        return response
    }

    override fun close(){
        process?.destroy()
        process=null
        started.set(false)
    }
}
