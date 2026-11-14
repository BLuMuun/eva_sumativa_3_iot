# Instalación y ejecución

## Requisitos de desarrollo

- Android Studio.
- JDK 21 para Gradle (en Android Studio, usa **Settings → Build Tools → Gradle → Gradle JDK 21**).
- Android SDK Platform 36 y Build Tools instalados mediante SDK Manager.
- Teléfonos Android con API 26 o posterior para pruebas reales.
- Una cuenta/proyecto Firebase configurado según [`FIREBASE_SETUP.md`](FIREBASE_SETUP.md).

## Compilar y abrir en Android Studio

1. Clona `https://github.com/BLuMuun/evaluacion-sumativa-n2-iot.git` o descarga su ZIP.
2. Copia el `google-services.json` descargado de Firebase a `app/google-services.json`.
3. Abre en Android Studio la carpeta raíz `evaluacion-sumativa-n2-iot` y espera la sincronización de Gradle.
4. Selecciona un teléfono físico o emulador y ejecuta la configuración `app`.

Desde PowerShell, con JDK 21 configurado:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`. `google-services.json`, `local.properties` y los archivos locales `secrets.h` están ignorados por Git.

## Probar con dos teléfonos Android

1. Instala el APK en ambos teléfonos, inicia sesión con una cuenta autorizada y habilita notificaciones si el sistema lo pregunta.
2. Conecta ambos a la misma red Wi-Fi o hotspot. Evita redes con aislamiento de clientes.
3. En Teléfono A, elige **Teléfonos → Nodo**, ingresa un código de 6 dígitos y toca **Iniciar Nodo**. Anota la IP que muestra.
4. En Teléfono B, elige **Teléfonos → Panel**, ingresa el mismo código y la IP del Nodo y toca **Conectar Panel**.
5. Verifica rasguños, alerta, permisos del rol, dispensación y ACK. Un rol Operador debe estar asignado desde Firebase Console.

Los dos teléfonos deben usar el mismo código durante esa sesión. Una sal aleatoria se negocia al conectar; la clave AES se deriva en ambos extremos.

## Probar la Sumativa de Unidad 3 con Wokwi

Usa `wokwi-unidad-3/cama` y `wokwi-unidad-3/comedero`; las carpetas `wokwi pruebas/` conservan la versión anterior. Sigue los pasos detallados en [`SUMATIVA_UNIDAD_3.md`](SUMATIVA_UNIDAD_3.md).

1. Copia `secrets.example.h` a `secrets.h` en ambas carpetas. Configura credenciales HiveMQ nuevas y la misma clave HMAC MQTT de al menos 32 bytes.
2. En `comedero/secrets.h`, genera un `PANEL_HMAC_SECRET_VALUE` distinto, aleatorio y de al menos 32 bytes. No envíes ni publiques esos secretos.
3. Inicia ambas simulaciones y confirma MQTT/TLS y TCP/5050 disponible en Comedero.
4. Conecta un teléfono por USB, autoriza ADB y ejecuta `adb reverse tcp:5050 tcp:5050`.
5. En la app inicia sesión como `operador`, elige **ESP32 seguro**, ingresa `127.0.0.1` y el secreto local del gateway.

Cloud Firestore en Android mantiene caché persistente y sincroniza escrituras en cola al recuperar Internet. El firmware conserva el estado de una solicitud activa y reintenta las lecturas MQTT hasta recibir confirmación. No marques como probadas las verificaciones que no hayas ejecutado en Wokwi o en hardware.

### Configurar MQTT local para las simulaciones Sumativa

1. En cada carpeta Sumativa (`wokwi pruebas/cama` y `wokwi pruebas/comedero`), copia `secrets.example.h` como `secrets.h`.
2. Usa el host de tu clúster HiveMQ y credenciales nuevas para cada nodo. El usuario de Cama necesita publicar en `comedero/solicitud` y leer `comedero/acuse`; el de Comedero necesita leer `comedero/solicitud` y publicar en `comedero/acuse`. Si el proveedor usa una ACL, otorga esos permisos a cada cuenta.
3. Usa exactamente la misma clave HMAC aleatoria de al menos 32 bytes en los dos `secrets.h`. No pegues credenciales ni claves en el chat, los `.ino`, capturas o archivos versionados.
4. Si la plantilla no se reemplazó, el monitor serie avisará que falta configurar HiveMQ y dejará de intentar conectar. Si aparece el estado MQTT 5, el broker no autorizó la cuenta; revisa usuario y permisos. Los avisos se limitan a uno cada 30 segundos para no llenar la terminal.
5. Reinicia ambas simulaciones tras guardar los dos archivos. Al conectar deben aparecer los bloques de inicio, la IP de Wi-Fi, la hora TLS y el mensaje `[MQTT/TLS] Conectado`.

### Probar el gateway Wokwi con un teléfono conectado por USB

Para el ensayo con simulaciones, basta un teléfono en rol **Panel** y las dos simulaciones ESP32. El teléfono no puede conectarse directamente al IP virtual de Wokwi. En la simulación Comedero se requiere el **Private Wokwi IoT Gateway**, que admite conexiones entrantes; el gateway público solo admite conexiones salientes.

1. Abre en VS Code el proyecto Sumativa `wokwi pruebas/comedero` y habilita el comando **F1 → Wokwi: Enable Private Wokwi IoT Gateway**. Si VS Code pide confirmación, elige habilitarlo.
2. Verifica que `wokwi.toml` del Comedero contenga el reenvío local `localhost:5050` hacia `target:5050`; reinicia esa simulación después de cambiar el archivo. Ejecuta también la simulación Sumativa de la Cama para que publique las lecturas por MQTT.
3. Conecta el teléfono al PC por USB, acepta la depuración USB y ejecuta en PowerShell: `adb reverse tcp:5050 tcp:5050`.
4. En la app elige **ESP32 → Panel**, escribe `127.0.0.1` como IP del gateway y toca **Conectar Panel**. El reenvío USB dirige el socket del teléfono al puerto local de Wokwi.
5. Confirma en el monitor serie del Comedero el mensaje `Panel conectado al gateway TCP/5050.`. Luego pulsa el botón de la simulación Cama y comprueba la lectura en la app antes de probar la orden de dispensación. La compuerta cierra a los 1,5 s; después de una entrega confirmada, la Cama y el Comedero bloquean nuevas entregas durante 10 s. Si se pierde el latido del Panel, el enlace vence a los 60 s.

Si no aparece la opción del Private Gateway, o el puerto local 5050 no queda disponible al reiniciar el Comedero, esa cuenta de Wokwi no está ofreciendo conexiones entrantes. En ese caso el ensayo Panel–ESP32 debe hacerse con una placa ESP32 real conectada a la misma Wi-Fi que el teléfono; no se debe registrar como aprobada usando solo la conexión MQTT saliente de Wokwi.
