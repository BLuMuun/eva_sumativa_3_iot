# Comedero IoT — Evaluación Sumativa Unidad 3

Aplicación Android y firmware ESP32 para integrar la detección de rasguños de Cama, el control del servo del Comedero y el Panel Android. El enlace entre placas conserva MQTT/TLS y HMAC; el Panel se autentica con el gateway y cifra sus tramas con AES-GCM.

## Contenido

- `app/`: aplicación Android con Login, perfiles, monitoreo, control e historial.
- `wokwi-unidad-3/cama/` y `wokwi-unidad-3/comedero/`: sketches y diagramas de la integración de Unidad 3.
- `firestore.rules`, `firestore.indexes.json` y `firebase.json`: configuración de Firestore y Firebase.
- `docs/SUMATIVA_UNIDAD_3.md`: correspondencia con la guía y estado.
- `docs/PRUEBAS_SUMATIVA.md`: matriz de pruebas Android + Wokwi y evidencias disponibles.
- `docs/INTEGRACION_ANDROID_WOKWI.md`, `docs/SEGURIDAD_ISO_OT.md` y `docs/evidencia-sumativa/`: integración, seguridad y registros.

## Preparación local

1. Abre la raíz en Android Studio. Descarga tu `google-services.json` de Firebase y guárdalo localmente en `app/`.
2. En cada carpeta Wokwi copia `secrets.example.h` como `secrets.h` y completa localmente HiveMQ, HMAC MQTT y la clave del Panel. No publiques secretos.
3. Compila la app con Gradle. Para Wokwi, inicia Cama y Comedero; con el Moto conectado por USB, ejecuta `adb reverse tcp:5050 tcp:5050`.
4. En Android inicia sesión con una cuenta configurada y conecta el Panel al gateway. Los detalles y las comprobaciones pendientes están en la documentación.

## Resultados registrados

- La documentación registra 16 pruebas unitarias y compilación Android aprobadas el 13-11-2026.
- PL1–PL8 se registraron como validadas en simulación Wokwi el 23-10-2026; no había placas ESP32 físicas para la validación.
- El 14-11-2026 Hertzbench reconoció WHPX en Windows, pero el Android Emulator no arrancó el AVD: reportó 4007 MiB disponibles frente a 4096 MiB mínimos. No se afirma que haya arrancado un teléfono virtual. El registro está en `docs/HERTZBENCH_WINDOWS_2026-11-14.md`.

La entrega formal de sketches también debe cargarse en AAI según las instrucciones de la asignatura. No se incluyen `google-services.json`, `local.properties` ni `secrets.h`; el `.gitignore` los excluye.