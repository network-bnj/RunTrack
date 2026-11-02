# RunTrack – Gestión de sensores (acelerómetro + giroscopio)

App Android (Kotlin) que captura, filtra y muestra en tiempo real acelerómetro y giroscopio
dentro de un **Foreground Service**, detecta cambios de dirección y notifica aunque la app esté cerrada.

## Cómo ejecutar
1. Abrir la carpeta en Android Studio (Koala o superior) y sincronizar Gradle.
2. Ejecutar en dispositivo físico o emulador (Extended controls → Virtual sensors).
3. (Opcional) Colocar `app/google-services.json` de tu proyecto Firebase para activar la sincronización con Cloud Firestore.

## Arquitectura
`MainActivity` (UI) ⇄ `SensorRepository` (StateFlow) ⇄ `SensorService` (listeners) → `SensorFilter` (paso-bajo + throttling) → `AlertNotifier` (notificaciones) · `CloudSyncWorker` (WorkManager + Firestore)
