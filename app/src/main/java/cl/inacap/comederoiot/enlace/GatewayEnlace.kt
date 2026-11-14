package cl.inacap.comederoiot.enlace

import cl.inacap.comederoiot.datos.RolUsuario
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Cliente del gateway ESP32: autenticación HMAC y tramas AES-GCM con secuencia anti-repetición. */
class GatewayEnlace(
    private val secreto: ByteArray,
    private val rolUsuario: RolUsuario
) : Enlace {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _estado = MutableStateFlow(EstadoEnlace.DESCONECTADO)
    private val _entrantes = MutableSharedFlow<Mensaje>(extraBufferCapacity = 32)
    private val _rolPanelAutorizado = MutableStateFlow<RolUsuario?>(null)
    private val cerrojoEnvio = Mutex()

    @Volatile private var socket: Socket? = null
    @Volatile private var ipPanelAnterior: String? = null
    @Volatile private var claveSesion: ByteArray? = null
    @Volatile private var nonceSesion: String? = null
    @Volatile private var secuenciaSalida = 0L
    @Volatile private var secuenciaEntrada = 0L
    @Volatile private var cerrado = false

    override val estado = _estado.asStateFlow()
    override val entrantes = _entrantes.asSharedFlow()
    override val rolPanelAutorizado = _rolPanelAutorizado.asStateFlow()

    override fun iniciarNodo() {
        _estado.value = EstadoEnlace.ERROR
    }

    override fun conectarPanel(ipNodo: String) {
        if (_estado.value != EstadoEnlace.DESCONECTADO || cerrado) return
        ipPanelAnterior = ipNodo
        _estado.value = EstadoEnlace.CONECTANDO
        alcance.launch {
            val cliente = Socket()
            try {
                cliente.tcpNoDelay = true
                cliente.keepAlive = true
                cliente.soTimeout = TIEMPO_ESPERA_MS
                cliente.connect(InetSocketAddress(ipNodo, PUERTO), TIEMPO_ESPERA_MS)
                val entrada = cliente.getInputStream()
                val salida = cliente.getOutputStream()
                val hello = leerLineaLimitada(entrada) ?: error("Sin saludo del gateway")
                val nonce = hello.removePrefix("HELLO;").takeIf {
                    hello.startsWith("HELLO;") && it.length in 20..32 && it.all { c -> c.isLetterOrDigit() || c in "+/=" }
                } ?: error("Saludo del gateway no válido")

                val clave = hmac(secreto, "SESSION;$nonce")
                enviarLinea(salida, "AUTH;$nonce;${hex(hmac(secreto, "AUTH;$nonce;PANEL"))}")
                val welcome = leerLineaLimitada(entrada) ?: error("El gateway no autenticó el Panel")
                val campos = welcome.split(';')
                if (campos.size != 3 || campos[0] != "WELCOME" || campos[1] != nonce) {
                    error("Respuesta de autenticación no válida")
                }
                val esperado = hex(hmac(secreto, "WELCOME;$nonce;GATEWAY"))
                if (!MessageDigest.isEqual(esperado.toByteArray(), campos[2].toByteArray())) {
                    error("No se pudo autenticar el gateway")
                }
                if (cerrado) {
                    cliente.close()
                    return@launch
                }

                socket = cliente
                nonceSesion = nonce
                claveSesion = clave
                secuenciaSalida = 0
                secuenciaEntrada = 0
                cliente.soTimeout = 0
                _rolPanelAutorizado.value = rolUsuario
                _estado.value = EstadoEnlace.CONECTADO

                while (alcance.isActive && !cerrado && !cliente.isClosed) {
                    val linea = leerLineaLimitada(entrada) ?: break
                    val mensaje = descifrarTrama(linea) ?: continue
                    _entrantes.emit(mensaje)
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                if (!cerrado) _estado.value = EstadoEnlace.ERROR
            } finally {
                runCatching { cliente.close() }
                if (socket === cliente) {
                    socket = null
                    limpiarClave()
                    _rolPanelAutorizado.value = null
                    if (!cerrado) _estado.value = EstadoEnlace.DESCONECTADO
                } else if (socket == null && !cerrado && _estado.value == EstadoEnlace.CONECTANDO) {
                    _rolPanelAutorizado.value = null
                    _estado.value = EstadoEnlace.ERROR
                }
            }
        }
    }

    override fun reconectarPanel() {
        val destino = ipPanelAnterior ?: return
        if (cerrado || _estado.value !in setOf(EstadoEnlace.DESCONECTADO, EstadoEnlace.ERROR)) return
        _estado.value = EstadoEnlace.DESCONECTADO
        conectarPanel(destino)
    }

    override suspend fun enviar(mensaje: Mensaje): Boolean = cerrojoEnvio.withLock {
        withContext(Dispatchers.IO) {
            if (!mensaje.permitidoParaGateway()) return@withContext false
            val cliente = socket ?: return@withContext false
            val clave = claveSesion ?: return@withContext false
            val nonce = nonceSesion ?: return@withContext false
            if (secuenciaSalida == Long.MAX_VALUE) return@withContext false
            val siguiente = secuenciaSalida + 1
            try {
                val linea = cifrarTrama(mensaje.aLinea(), clave, nonce, "P", siguiente)
                if (linea.length > MAX_LINEA) return@withContext false
                enviarLinea(cliente.getOutputStream(), linea)
                secuenciaSalida = siguiente
                true
            } catch (_: Exception) {
                runCatching { cliente.close() }
                false
            }
        }
    }

    private fun descifrarTrama(linea: String): Mensaje? {
        return try {
            val campos = linea.split(';', limit = 4)
            if (campos.size != 3 || campos[0] != "G") return null
            val secuencia = campos[1].toLongOrNull() ?: return null
            if (secuencia <= secuenciaEntrada) return null
            val clave = claveSesion ?: return null
            val nonce = nonceSesion ?: return null
            val paquete = Base64.getDecoder().decode(campos[2])
            if (paquete.size !in (BYTES_IV + BYTES_TAG + 1)..MAX_PAQUETE) return null
            val iv = paquete.copyOfRange(0, BYTES_IV)
            val cifrado = paquete.copyOfRange(BYTES_IV, paquete.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD("G;$nonce;$secuencia".toByteArray(Charsets.UTF_8))
            val texto = String(cipher.doFinal(cifrado), Charsets.UTF_8)
            val mensaje = Mensaje.desdeLinea(texto)?.takeIf { it.permitidoParaPanel() } ?: return null
            secuenciaEntrada = secuencia
            mensaje
        } catch (_: Exception) {
            null
        }
    }

    private fun cifrarTrama(texto: String, clave: ByteArray, nonce: String, direccion: String, secuencia: Long): String {
        val iv = ByteArray(BYTES_IV).also(aleatorio::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD("$direccion;$nonce;$secuencia".toByteArray(Charsets.UTF_8))
        val cifrado = cipher.doFinal(texto.toByteArray(Charsets.UTF_8))
        val paquete = ByteArray(iv.size + cifrado.size)
        iv.copyInto(paquete)
        cifrado.copyInto(paquete, iv.size)
        return "$direccion;$secuencia;${Base64.getEncoder().encodeToString(paquete)}"
    }

    override fun cerrarConexionActual() {
        runCatching { socket?.close() }
        socket = null
        limpiarClave()
        _rolPanelAutorizado.value = null
        if (!cerrado) _estado.value = EstadoEnlace.DESCONECTADO
    }

    override fun cerrar() {
        cerrado = true
        cerrarConexionActual()
        secreto.fill(0)
        alcance.cancel()
        _rolPanelAutorizado.value = null
        _estado.value = EstadoEnlace.DESCONECTADO
    }

    private fun limpiarClave() {
        claveSesion?.fill(0)
        claveSesion = null
        nonceSesion = null
        secuenciaSalida = 0
        secuenciaEntrada = 0
    }

    private fun enviarLinea(salida: java.io.OutputStream, linea: String) {
        salida.write((linea + "\n").toByteArray(Charsets.UTF_8))
        salida.flush()
    }

    private fun leerLineaLimitada(entrada: java.io.InputStream): String? {
        val bytes = ByteArrayOutputStream()
        var excedida = false
        while (true) {
            val siguiente = entrada.read()
            if (siguiente == -1) return null
            if (siguiente == '\n'.code) {
                if (excedida) return ""
                val trama = bytes.toByteArray()
                val largo = if (trama.lastOrNull() == '\r'.code.toByte()) trama.size - 1 else trama.size
                return String(trama, 0, largo, Charsets.UTF_8)
            }
            if (!excedida) {
                if (bytes.size() >= MAX_LINEA) excedida = true else bytes.write(siguiente)
            }
        }
    }

    companion object {
        const val PUERTO = 5050
        private const val TIEMPO_ESPERA_MS = 5_000
        private const val MAX_LINEA = 256
        private const val BYTES_IV = 12
        private const val BYTES_TAG = 16
        private const val MAX_PAQUETE = 220
        private val aleatorio = java.security.SecureRandom()

        fun secretoValido(secreto: String): Boolean = secreto.length in 32..128 &&
            secreto.all { it.code in 0x21..0x7e }

        private fun hmac(llave: ByteArray, texto: String): ByteArray = Mac.getInstance("HmacSHA256")
            .run {
                init(SecretKeySpec(llave, "HmacSHA256"))
                doFinal(texto.toByteArray(Charsets.UTF_8))
            }

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }
}
