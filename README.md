# Sumptus

MVP de app Android para gestionar gastos personales.

## Stack elegido

- Kotlin + Jetpack Compose
- Material 3
- Persistencia local con DataStore
- Arquitectura sencilla con `ViewModel`

## Lo que ya hace

- Registrar gastos con concepto, importe, categoria y notas
- Guardar el historial en local
- Ver resumen del mes, gasto medio y ultimos movimientos
- Borrar entradas del historial

## Para abrirlo

1. Abre `/home/cris/Workspace/Sumptus` en Android Studio.
2. Instala o configura el Android SDK si Studio te lo pide.
3. Si quieres configurarlo a mano, crea `local.properties` con `sdk.dir=/ruta/a/Android/Sdk`.
4. Tambien puedes usar la variable de entorno `ANDROID_HOME`.
5. Sincroniza Gradle.
6. Ejecuta la app en un emulador o movil.

## Estado actual

- `./gradlew help --no-daemon` funciona
- `./gradlew :app:assembleDebug --no-daemon` falla solo porque falta configurar el Android SDK

## Siguiente iteracion sugerida

- Presupuestos por categoria
- Filtros por rango de fechas
- Exportacion CSV
- Copia de seguridad en la nube
