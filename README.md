# 🎵 Rythmix

Rythmix es una aplicación web interactiva centrada en la música y el juego colaborativo. Su objetivo es poner a prueba el oído, la memoria musical y la creatividad de los jugadores a través de distintos modos de juego diseñados para jugar en grupo.

---

## 🎮 Modos de juego

### 🎵 Adivina la canción
Escucha por capas la canción diaria e intenta adivinarla antes de agotar los intentos.

**¿Cómo funciona el juego?**

- **Ronda 1: Pon la primera piedra**

Cada jugador empieza una canción desde cero. Crea una pista con tu instrumento y dale un ritmo inicial. Cuando todos terminen, ¡empieza la magia!

- **El Gran Relevo**

Al final de cada ronda, las canciones rotan. Recibirás la pista que creó uno de tus compañeros en la ronda anterior, y tu canción pasará al siguiente jugador.

- **Suma tu talento**

Escucha lo que ha grabado tu compañero y añade una nueva capa con un instrumento diferente. ¡Haz que la canción crezca!

- **El Gran Estreno**

Al final, tendréis tantas canciones como jugadores tengáis en la partida, ¡y cada una será una colaboración única!

✅ *Esta parte está implementada y 100% funcional.*

### 🎲 Canción sorpresa
Cada jugador aporta una parte sin conocer el resultado final, dando lugar a combinaciones inesperadas.  

**¿Cómo funciona el juego?**
- **Ronda 1: La base musical**

Todos los jugadores crean, de forma independiente, una pista inicial con el primer instrumento para arrancar la canción.
- **La Gran Votación**

Una vez que todos terminan de grabar, escucháis las propuestas de cada jugador y votáis por vuestra favorita. ¡La pista más votada se convierte en la base oficial de la canción!
- **Ronda a ronda: Construyendo el temazo**

En las siguientes rondas, todos escucharéis la canción tal y como va hasta ese momento. Vuestra misión será grabar una nueva capa que encaje y mejore lo que ya hay. Al terminar la ronda, se vuelve a votar y solo la pista ganadora se añade a la canción.
- **El Gran Estreno**

El juego termina tras un número fijo de rondas. Al final, podréis escuchar el resultado de vuestra obra maestra: una canción épica construida paso a paso con las mejores aportaciones de todo el grupo.

✅ *Esta parte está implementada y 100% funcional.*

### ▶️ Continuación de canción
Todos los jugadores construyen una misma canción por rondas y votan qué track pasa a formar parte del resultado final.  
✅ *Esta parte está implementada y 100% funcional.*

### ⭐ Funcionalidades adicionales ya implementadas

- Perfil de usuario con cambio de contraseña, foto e historial de partidas terminadas.
- Reproducción de melodías finales desde el perfil.
- Canciones favoritas con fecha de partida, autores e instrumentos usados.
- Panel de administración separado en usuarios, partidas jugadas y configuración del daily.
- Dashboard de observabilidad y vista de reportes del juego diario.

---

## ⚙️ Funcionamiento

Los jugadores acceden a un *lobby* desde el cual pueden crear una sala de juego o unirse a una existente, permitiendo una experiencia dinámica y social.  
La estructura del proyecto está pensada para facilitar la ampliación futura con nuevos modos de juego y funcionalidades adicionales.

### 🖥️ Vistas

| Ruta | Estado |
|------|--------|
| `/` (Índice) | Página de bienvenida de la aplicación |
| `/about` | Información general del proyecto |
| `/authors` | Página con los autores del proyecto |
| `/guess` (Adivina la canción) | Juego basado en adivinar mediante una serie de intentos el juego diario |
| `/games` (Elegir juego) | Pagina donde se elije los tipos de juegos en multijugador |
| `/gartic` (Canción sorpresa) | Modo multijugador con websockets donde las secuencias rotan entre jugadores en cada ronda |
| `/continue` (Continuación de canción) | Modo multijugador con websockets y votación para elegir el mejor track de cada ronda |
| `/favoriteSongs` | Página de canciones favoritas con reproducción y detalle de fecha, autores e instrumentos |
| `/leaderboard` | Se muestra un ranking de los jugadores con mejor puntuación del juego diario
| `/user/{id}`| Perfil de usuario con datos personales, cambio de contraseña, foto e historial de partidas con melodías finales reproducibles |
| `/admin/users` | Vista administrativa de usuarios, con activación/desactivación de cuentas |
| `/admin/games` | Vista administrativa de últimas partidas jugadas |
| `/admin/daily` | Vista para preparar la canción del día, ajustar intentos/capas y subir MP3 |
| `/admin/dashboard` | Vista con estadísticas globales y eventos de observabilidad |
| `/admin/reports` | Vista con reportes enviados por usuarios sobre el juego diario |
---

## 🛠️ Tecnologías utilizadas

- **Spring Boot**  
- **Thymeleaf**  
- **Bootstrap 5**  
- **Base de datos H2** (entorno de desarrollo)
- **Karate** para tests externos e internos

Fuera de la plantilla:
- **ABC.js** para renderizado de música con formato ABC para la creación de tracks en los juegos

---

## 🔊 Subida de audios MP3

Los administradores pueden subir o reemplazar MP3 de capas de canciones desde `/admin/daily`.

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

## Usuarios por defecto

| Usuario | Contraseña |
|---------|------------|
| a (admin) | aa |
| b | aa |

---

## 🎓 Contexto académico

Este proyecto ha sido desarrollado como parte de la asignatura *Ingeniería Web (IW)*, aplicando el patrón MVC, control de acceso con Spring Security y buenas prácticas de desarrollo web.

---

## 🤖 Uso de IA generativa y LLMs

- Debugging de trazas desconocidas
- Identificación de trazas de ejecución como medida para entender mejor las implementaciones de otros miembros del equipo
- Ayuda para escribir tests y consultar la documentación

---

## 📌 Estado del proyecto

Proyecto funcional con daily, modos multijugador, perfil enriquecido, favoritas y administración actualizada.
