# Verde a Mano Android

Frontend académico de una tienda de jardinería urbana para la Actividad 4 de Desarrollo de aplicaciones móviles nativas. La interfaz usa Kotlin, Jetpack Compose, Material 3 y el tema Botánica Moderna.

- **ADMIN**. Panel, usuarios, supervisión de publicaciones y consulta de pedidos.
- **BUYER**. Inicio, catálogo, ficha, carrito, pedidos, perfil y acceso a Wompi Sandbox cuando hay un pedido propio compatible.
- **SELLER**. Panel, publicaciones propias, formularios, historial y perfil comercial.

| Carpeta | Contenido |
|---|---|
| `android/` | Proyecto que se abre en Android Studio. Incluye wrapper y módulo app. |
| `contracts/` | DTOs y validaciones compartidas requeridas por Gradle. |
| `gradle/` | Catálogo compartido de versiones. |
| `capturas/` | Selección de evidencias físicas con datos personales ocultos. |

## Apertura y compilación

1. Conserva juntas las carpetas hermanas y abre `android/` en Android Studio.
2. Instala Android SDK 37 y usa un Android Studio compatible con el AGP declarado en `gradle/libs.versions.toml`. El wrapper usa Gradle 9.6.0, el daemon requiere JDK 25 y el módulo de contratos usa toolchain JDK 21. La app requiere Android 10 (API 29) o superior.
3. En un proyecto Firebase autorizado, registra la aplicación Android `com.verdeamano` y descarga su archivo cliente. Colócalo en `android/app/google-services.json`. El plugin Google Services lo requiere para compilar. Esta copia lo omite para separar el proyecto Firebase activo de la publicación. Mantén privado cualquier archivo de cuenta de servicio.
4. Sincroniza Gradle. Desde PowerShell, dentro de `android/`, ejecuta:

```powershell
.\gradlew.bat :app:assembleDebug
```

Para usar un backend Ktor compatible en el equipo, puedes pasar `"-PapiBaseUrl=http://10.0.2.2:8081/"` en emulador. En teléfono, usa `"-PapiBaseUrl=http://127.0.0.1:8081/"` con el reverse ADB correspondiente. El backend es externo a esta copia. El acceso integrado requiere que Firebase cliente y backend pertenezcan al mismo proyecto autorizado, perfiles habilitados y red funcional. El APK Debug queda en `android/app/build/outputs/apk/debug/`.

## Evidencia y alcance

[Panel ADMIN](capturas/admin/ADMIN-01-dashboard.png) · [Alta ADMIN](capturas/admin/ADMIN-03-usuarios-creacion.png) · [Filtros BUYER](capturas/buyer/BUYER-03-filtros.png) · [Perfil BUYER](capturas/buyer/BUYER-07-perfil.png) · [Panel SELLER](capturas/seller/SELLER-01-panel.png) · [Listado SELLER](capturas/seller/SELLER-02-productos.png)

El QA previo en Xiaomi produjo 24 capturas por rol. ADMIN aprobó 7 recorridos de lectura y cancelación. BUYER aprobó 5, con 1 parcial y 1 bloqueado. SELLER aprobó 4, con 1 parcial y 2 bloqueados. Las 119 pruebas JVM aprobadas corresponden al proyecto original y al build previo. Esta copia conserva las pruebas JVM y una prueba instrumentada de renderizado. Los recorridos instrumentados ligados a cuentas y servicios privados quedaron fuera.

El reporte ADMIN muestra pedidos y valores por pedido. Los ingresos netos no están disponibles. La edición administrativa cubre rol y estado. El editor de perfil falló en BUYER y SELLER. La cuenta BUYER no tenía un pedido compatible para abrir pago y SELLER no tenía productos propios para edición o baja. Se observó contraste bajo en la barra del sistema.
