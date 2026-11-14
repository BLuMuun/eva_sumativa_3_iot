# Sumativa Unidad 3

Esta entrega integra la aplicación Android de Sumativa 2 con los nodos ESP32 de Unidad 1. La Cama detecta las pulsaciones; el Comedero recibe los datos y opera el servo; el teléfono Panel monitorea la sesión y autoriza la dispensación. El modo entre placas conserva MQTT/TLS y HMAC, y el enlace Panel–gateway usa autenticación HMAC mutua y tramas AES-GCM.

## Correspondencia con la guía

| Paso y criterio | Implementación | Evidencia que debe registrarse |
|---|---|---|
| 1. Revisar prototipo Unidad 1 | Diagramas y sketches de Cama y Comedero en `wokwi-unidad-3/`; botón de prueba, ESP32 y servo. | Compilación de ambos sketches y prueba en Wokwi; la simulación no sustituye placas físicas. |
| 2. Corregir app Unidad 2 | Login Firebase, perfil Operador/Observador, panel, control y Firestore permanecen integrados. | Login de cada perfil, permiso de control y consulta del historial. |
| 3. Envío de lecturas, integridad y reconexión | Cama publica cambios por MQTT/TLS con HMAC y secuencia; gateway valida, confirma y conserva la solicitud activa. Android actualiza el panel y reconecta tras una pérdida de red. | Lecturas 1/3, 2/3, 3/3, ventana expirada y reconexión. |
| 4. Control del actuador | Panel Operador envía `DISPENSAR` por TCP/5050 protegido; Comedero abre 1,5 s, cierra y confirma. Timeout o pérdida del Panel cancela y deja `CERRADO`. | Apertura, cierre, ACK, bloqueo de 10 s y corte del Panel. |
| 5. Datos temporales y sincronización | Firestore Android mantiene caché persistente y cola de escrituras offline, y las sincroniza al volver Internet. La app muestra escrituras pendientes de nube de la instancia actual. Cama reintenta cada lectura MQTT hasta el ACK; Preferences conserva contadores y estado de una solicitud pendiente. | Interrumpir Internet sin detener el enlace local; verificar escrituras pendientes y su sincronización tras recuperar Internet. |
| 6. Seguridad | Firebase Auth y reglas por rol; AES-GCM y anti-repetición entre teléfonos; HMAC mutuo, AES-GCM y anti-repetición en Panel–gateway; MQTT/TLS + HMAC entre placas; límites de trama y secretos locales. | Clave incorrecta, trama alterada o repetida, observador sin control, y logs sin secretos. |

La guía también menciona comunicación serial. Los sketches usan UART a 115200 baudios para diagnóstico; el control de esta integración usa Wi-Fi/TCP y MQTT. Si la pauta de apreciación exige que Android controle la placa por USB-UART físico, ese caso requiere un adaptador USB-Serial y no queda demostrado por Wokwi.

## Datos offline y consumo

La app escribe solo lecturas válidas y el filtro omite valores consecutivos repetidos. En Android, la persistencia offline de Cloud Firestore está habilitada por defecto: conserva escrituras pendientes localmente y las sincroniza al recuperar conexión. El contador mostrado refleja las escrituras que siguen pendientes en la instancia actual; tras reiniciar la app, Firestore conserva su propia cola, aunque el contador de interfaz vuelve a iniciar.

El firmware mantiene una cola acotada en RAM para lecturas MQTT, reintenta la cabeza de la cola hasta recibir confirmación y guarda en NVS el identificador, contador y estado de una solicitud aceptada. La ventana incompleta de cinco segundos no se restaura después de reiniciar, porque ya expiró. La app redujo los latidos a uno cada cinco segundos y mantiene el timeout seguro de 60 segundos. Wokwi no mide consumo real de energía; una cifra de batería exige medir las placas y teléfonos físicos.

## Preparación local

1. En `wokwi-unidad-3/cama` y `wokwi-unidad-3/comedero`, copia `secrets.example.h` como `secrets.h`.
2. Configura credenciales nuevas de HiveMQ. La Cama necesita publicar `comedero/solicitud` y leer `comedero/acuse`; Comedero necesita leer `comedero/solicitud` y publicar `comedero/acuse`.
3. Usa la misma clave HMAC MQTT de 32 bytes o más en ambos archivos. En Comedero genera otra clave aleatoria para `PANEL_HMAC_SECRET_VALUE`, de 32 bytes o más.
4. No subas `secrets.h`, el JSON de Firebase ni contraseñas. `.gitignore` mantiene esos archivos locales.
5. Inicia ambas simulaciones Wokwi. Con un teléfono conectado por USB, autoriza ADB y ejecuta `adb reverse tcp:5050 tcp:5050`. En la app entra como Operador, elige **ESP32 seguro**, indica IP `127.0.0.1` y escribe la clave del Panel que configuraste en Comedero.

Para dos teléfonos reales, usa **Teléfonos**: uno como Nodo y otro como Panel. Este modo cifra la conexión con AES-GCM y valida el rol del Panel en el Nodo mediante Firebase.

## Emulación en este PC

El 14-11-2026 se probó Hertzbench master en Windows. El chequeo del SDK confirmó WHPX utilizable y el lanzador encontró el AVD `Medium_Phone_API_37.0`. Android Emulator abortó antes del inicio: informó 4007 MiB de RAM del sistema frente a 4096 MiB mínimos. Se intentó temporalmente configurar 1536 MB al AVD; el requisito del host no cambió y la configuración original de 2048 MB se restauró. El Moto G35 sí fue reconocido por ADB, pero no arrancó ningún teléfono virtual.

Esta máquina no completó una ejecución de nodo Android con Hertzbench. Cada nodo Android de Hertzbench inicia un AVD separado, así que la prueba de dos nodos sigue pendiente en un equipo con más memoria. La comprobación de WHPX no cuenta como una ejecución Android exitosa.
## Verificación y resultados

| Verificación | Estado |
|---|---|
| Pruebas unitarias Android | 16 pruebas pasaron; cero fallas/errores (`:app:testDebugUnitTest`, 13-11-2026). |
| Compilación y empaquetado Android | `:app:assembleDebug` y `compileDebugKotlin` terminaron correctamente el 13-11-2026. APK debug en `app/build/outputs/apk/debug/app-debug.apk`. |
| Compilación Arduino de Cama y Comedero Unidad 3 | Los sketches copiados coinciden byte por byte con los de Formativa U3, compilados previamente; la copia dentro de este repositorio no se volvió a compilar. |
| Aceptación en Wokwi con Android Panel | Pendiente; requiere simulaciones activas, secreto local de Panel y teléfono autorizado en ADB. |
| Hertzbench Android en Windows | WHPX pasó; el único AVD no arrancó porque el host reportó 4007 MiB frente a 4096 MiB mínimos. No se ejecutaron nodos Android. |
| Un AVD + Moto G35 físico | No probado: el AVD se detuvo antes de arrancar por el mínimo de RAM del host. |
| Medición de consumo eléctrico y prueba con placas físicas | No ejecutadas: no hay placas físicas disponibles y Wokwi no mide consumo. |

Las referencias a ISO/IEC 27400 y controles ISO/IEC 27002 en `SEGURIDAD_ISO_OT.md` justifican decisiones de diseño; no equivalen a certificación ni auditoría formal. El gateway valida el secreto de sesión, pero no verifica directamente el rol Firebase: la app Operador aplica esa autorización.
