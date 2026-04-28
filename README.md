# 🎵 Rythmix

Rythmix es una aplicación web interactiva centrada en la música y el juego colaborativo. Su objetivo es poner a prueba el oído, la memoria musical y la creatividad de los jugadores a través de distintos modos de juego diseñados para jugar en grupo.

---

## 🎮 Modos de juego

### 🎵 Adivina la canción
Escucha un fragmento musical y demuestra que sabes reconocer la canción antes que nadie.  
✅ *Esta parte está implementada y 100% funcional.*

### 🎲 Canción sorpresa
Cada jugador aporta una parte sin conocer el resultado final, dando lugar a combinaciones inesperadas.

### ▶️ Continuación de canción
Escucha el inicio de una canción e intenta continuar correctamente la letra o la melodía.

---

## ⚙️ Funcionamiento

Los jugadores acceden a un *lobby* desde el cual pueden crear una sala de juego o unirse a una existente, permitiendo una experiencia dinámica y social.  
La estructura del proyecto está pensada para facilitar la ampliación futura con nuevos modos de juego y funcionalidades adicionales.

### 🖥️ Vistas

| Ruta | Estado |
|------|--------|
| `/` (Índice) | Falta estilizar la página |
| `/guess` (Adivina la canción) | Lógica implementada, falta estilo |
| `/games` (Elegir juego) | Falta estilizar |
| `/gartic` (Canción sorpresa) | Lógica implementada desde la vista de un jugador, falta implementar WebSockets y añadir estilo |
| `/continue` | En desarrollo, por ahora inaccesible |

---

## 🛠️ Tecnologías utilizadas

- **Spring Boot**  
- **Thymeleaf**  
- **Bootstrap 5**  
- **Base de datos H2** (entorno de desarrollo)

---

## 🔊 Subida de audios MP3

Los administradores pueden subir/reemplazar MP3 de capas de canciones desde `/admin/`.

- Los ficheros se guardan fuera de `src/main/resources/static` (en `iwdata/music/layer/`).
- El audio se sirve por endpoint (`/song-layer/{id}/audio`), sin recompilar para cambiar canciones.
- Si falta audio para una capa, el juego muestra aviso en pantalla.

### Procesado opcional con FFmpeg

Si se activa `app.audio.trim.enabled=true`, al subir un MP3 se aplica:

- recorte a máximo `app.audio.trim.max-seconds` (por defecto 60s), centrado en el audio,
- recompresión MP3 con bitrate `app.audio.compress.bitrate` (por defecto `192k`).

Si FFmpeg no está instalado y el recorte está activado, la subida falla con mensaje para instalarlo o desactivar el recorte.

### Instalar FFmpeg

- Windows (Chocolatey): `choco install ffmpeg`
- Windows (winget): `winget install Gyan.FFmpeg`
- macOS (Homebrew): `brew install ffmpeg`
- Ubuntu/Debian: `sudo apt install ffmpeg`

Verificación:

- `ffmpeg -version`
- `ffprobe -version`

### Configuración relevante

En `src/main/resources/application.properties`:

- `app.storage.music-dir=music/layer`
- `app.audio.trim.enabled=false`
- `app.audio.trim.max-seconds=60`
- `app.audio.compress.bitrate=192k`

---

## 🎓 Contexto académico

Este proyecto ha sido desarrollado como parte de la asignatura *Ingeniería Web (IW)*, aplicando el patrón MVC, control de acceso con Spring Security y buenas prácticas de desarrollo web.

---

## 📌 Estado del proyecto

🚧 En desarrollo activo. Próximamente se completarán los estilos y la funcionalidad en tiempo real con WebSockets.
