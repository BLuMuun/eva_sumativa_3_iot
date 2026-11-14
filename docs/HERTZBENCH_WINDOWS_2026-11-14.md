# Hertzbench en Windows 2026-11-14

## Resultado

La corrección de soporte de aceleración de Hertzbench para Windows pasó la comprobación de WHPX, pero Android Emulator no llegó a iniciar el AVD. Esta evidencia es un intento de arranque, no una prueba completada con un teléfono emulado.

## Entorno y pasos

- Windows 10, compilación 19045; memoria visible aproximada: 4008 MiB.
- Android Emulator 37.2.12 y AVD `Medium_Phone_API_37.0`.
- `emulator -accel-check`: `WHPX ... is installed and usable`.
- El código actual de Hertzbench encontró `emulator.exe`, `adb.exe` y el AVD.
- `hertzbench android prepare Medium_Phone_API_37.0` intentó arrancar el AVD con 2048 MB configurados.

## Salida relevante

```text
Available system RAM is not enough to run avd
Available: 4007 MiB, minimum required: 4096 MiB
```

Se probó temporalmente bajar `hw.ramSize` a 1536 MB y volver a preparar. El emulador mantuvo el mismo mínimo del host y volvió a abortar antes del arranque. Se restauró `hw.ramSize=2048`; no quedó ningún emulador abierto. El Moto G35 físico fue reconocido por ADB, pero no cuenta como AVD.

## Conclusión

WHPX y la detección de herramientas Windows funcionan en este host. No se validó el arranque de un AVD, ni un escenario de uno o dos nodos Android en Hertzbench. La ejecución de dos AVD requiere repetir en una máquina con más memoria.