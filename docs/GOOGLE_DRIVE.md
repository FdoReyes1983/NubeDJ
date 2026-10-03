# Google Drive en Nube DJ v0.2.0

Nube DJ usa autorización de Google Play Services con alcance de solo lectura:

`https://www.googleapis.com/auth/drive.readonly`

Para que el botón **Biblioteca > Conectar Google Drive** funcione en la APK de desarrollo, registra un cliente OAuth de tipo **Android** en el mismo proyecto de Google Cloud que ya uses para Drive.

- Package name: `cl.fernando.nubedj`
- SHA-1 de la clave debug incluida en el proyecto: `5B:55:D0:A6:5D:D3:11:1D:0B:B7:15:FC:EA:68:9A:B4:60:AD:52:1A`
- Activa **Google Drive API**.
- Si la pantalla de consentimiento está en modo Testing, añade la cuenta de Google usada en el teléfono como usuario de prueba.

La app solicita solo lectura. Los tokens no se guardan en archivos ni se incorporan a las URL de las canciones.
