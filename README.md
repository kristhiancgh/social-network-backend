# social-network-backend

Backend de una red social con arquitectura de microservicios: autenticación, perfiles,
publicaciones y likes en tiempo real.

Java 21 · Spring Boot 3.5.16 · PostgreSQL 17 · Gradle 8.14.3 · Docker Compose

---

## Arranque

```bash
docker compose up --build
```

Levanta todo, ejecuta las migraciones y siembra los datos de prueba.

Funciona igual en macOS, Linux y Windows. Notas por sistema:

- **Windows** — Docker Desktop tiene que estar arrancado; compruébalo con `docker version`,
  que debe mostrar la sección `Server:`. Clona con Git 2.10 o superior, para que respete el
  `.gitattributes`. Desde PowerShell o CMD, el wrapper de Gradle es `.\gradlew.bat`.
- **Linux** — tu usuario debe pertenecer al grupo `docker`
  (`sudo usermod -aG docker $USER`, y volver a iniciar sesión) o tendrás que usar `sudo`.
  En Fedora, RHEL y derivados el montaje de los scripts de inicialización lleva la etiqueta
  SELinux necesaria.

| | |
|---|---|
| API | http://localhost:8080 |
| Swagger UI (los cuatro servicios) | http://localhost:8080/docs |
| PostgreSQL | localhost:5432 |

Cuentas demo: `jdoe`, `mgarcia`, `lchen`, `arossi`, `kcamilo` — todas con `Password123!`.

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"jdoe","password":"Password123!"}'
```

---

## Servicios

| Servicio | Puerto | Base | Responsabilidad |
|---|---|---|---|
| `api-gateway` | 8080 | – | Enrutamiento, CORS, Swagger agregado, proxy WebSocket |
| `auth-service` | 8081 | `authdb` | Login, registro, emisión de JWT, auditoría |
| `profile-service` | 8082 | `profiledb` | Nombres, apellidos, fecha de nacimiento, alias |
| `post-service` | 8083 | `postdb` | Timeline, publicación y difusión de nuevas notas |
| `like-service` | 8084 | `likedb` | Likes, totales y difusión de cambios |
| `shared-kernel` | – | – | Librería: contrato de errores, JWT, OpenAPI |

Cada servicio es dueño de su base y ninguno lee la de otro. Un solo contenedor PostgreSQL
aloja las cuatro, cada una con su rol de mínimo privilegio.

Estructura **por feature**, con capas dentro de cada una:
`controllers/ · services/ · repositories/ · domain/ · dto/ · mappers/ · exceptions/`

---

## Decisiones clave

- **Las referencias entre bases son lógicas, nunca claves foráneas.** Una FK real acoplaría
  los servicios en la capa de almacenamiento.
- **El gateway no valida tokens.** Reenvía la cabecera `Authorization` y cada servicio decide.
- **Errores en formato RFC 7807 ampliado**, iguales en los cuatro servicios, con `errorCode`
  (el contrato estable para los clientes), `traceId` y `service`.
- **Siete procedimientos PL/pgSQL.** El central es `sp_toggle_post_like`, que resuelve el
  ciclo del like en una llamada tomando `FOR UPDATE` sobre la fila del contador: dos likes
  simultáneos sobre la misma publicación no pueden perderse, ni con varias réplicas.
- **`published_at` lo pone el servidor** dentro de `sp_create_post`, nunca el cliente.
- **El borrado es lógico**, para no dejar huérfanos los likes que viven en otra base.

---

## Tiempo real

STOMP sobre WebSocket. Dos puntos de conexión, porque dos servicios producen eventos y cada
uno es dueño del suyo:

| Endpoint | Servicio | Tema |
|---|---|---|
| `/ws` | like-service | `/topic/likes` |
| `/ws-posts` | post-service | `/topic/posts` |

Ambos difunden **después del commit** (`@TransactionalEventListener(AFTER_COMMIT)`), y el
token viaja en el frame STOMP CONNECT, no en el handshake.

---

## API

Todas las rutas pasan por el gateway. Documentación interactiva en `/docs`.

| | |
|---|---|
| Autenticación | `POST /api/auth/login` · `POST /api/auth/register` · `GET /api/auth/me` |
| Perfiles | `GET`/`PUT /api/profiles/me` · `GET /api/profiles/{userId}` |
| Publicaciones | `GET`/`POST /api/posts` · `DELETE /api/posts/{id}` |
| Likes | `POST /api/likes` · `GET /api/likes/counts?postIds=…` |

> `GET /api/auth/login` existe porque el enunciado lo pide, y está **marcado como obsoleto**:
> la contraseña en la query string acaba en el historial y en los logs de cada proxy. Usa
> `POST`.

---

## Pruebas

```bash
./gradlew testAll         # 88 pruebas          (macOS, Linux, Git Bash)
.\gradlew.bat testAll     #                     (PowerShell, CMD)
```

```bash
./gradlew coverageAll     # informes JaCoCo
```

Se ejecutan contra un PostgreSQL 17 real vía Testcontainers, no contra una base en memoria:
H2 no tiene `CREATE PROCEDURE ... LANGUAGE plpgsql` ni `FOR UPDATE` con semántica útil.
Cobertura del 57 al 72 % en instrucciones.

---

## Configuración

Copia `.env.example` a `.env`. Todo tiene un valor por defecto funcional.

`JWT_SECRET` debe ser **idéntico en los cuatro servicios**: auth-service firma y los demás
verifican.

---

## Solución de problemas

**Los contenedores arrancan y mueren.** Falta memoria en la VM de Docker: cinco JVM más
Postgres necesitan unos 4 GB.

**`database "authdb" does not exist`.** Los scripts de inicialización de Postgres se ejecutan
una sola vez, sobre un directorio de datos vacío. Tras cambiarlos hay que borrar el volumen con
`docker compose down -v`.

**401 en todo tras un login correcto.** Los cuatro servicios tienen que recibir el mismo
`JWT_SECRET`.

**Windows: `./gradlew: not found` al construir.** El archivo llegó con CRLF y el shebang quedó
`#!/bin/sh\r`, así que falta el intérprete, no el script. Normaliza la copia con
`git rm --cached -r . && git reset --hard`.

**Windows: `open //./pipe/dockerDesktopLinuxEngine`.** Docker Desktop no está arrancado.
