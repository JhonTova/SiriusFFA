# SiriusFFA

FFA para Minecraft **1.21.11**, construido sobre **SiriusCore**, pensado para un servidor
**survival** donde perder o duplicar un objeto es un problema serio.

Dos sistemas separados a propósito:

```
                    SIRIUSFFA
                       │
             ┌─────────┴─────────┐
             │                   │
       FFA ARENAS          FFA EVENTS
       permanentes          temporales
             │                   │
       ┌─────┼─────┐       ┌─────┼───────┬──────────┐
       │     │     │       │     │       │          │
     con   sin   varias  classic jugger gun-game  one in
     kit   kit   arenas                            the chamber
```

- **Arenas**: abiertas todo el día. `/ffa join arena-1`, se entra y se sale cuando uno
  quiere, no hay partida que empiece.
- **Eventos**: los lanza un administrador. `/ffa start juggernaut` → los jugadores entran
  con `/ffa join event` → cuenta atrás, rondas, ganador y recompensas.

Un jugador solo puede estar en uno de los dos. Nunca en los dos a la vez.

---

## Índice

- [Instalación](#instalación)
- [La regla de oro: los inventarios](#la-regla-de-oro-los-inventarios)
- [Crear una arena](#crear-una-arena)
- [Crear un kit](#crear-un-kit)
- [Eventos y modos](#eventos-y-modos)
- [Comandos](#comandos)
- [Permisos](#permisos)
- [Política de comandos](#política-de-comandos)
- [Integraciones](#integraciones)
- [Eventos automáticos](#eventos-automáticos)
- [Placeholders](#placeholders)
- [Añadir un modo nuevo](#añadir-un-modo-nuevo)
- [Rendimiento](#rendimiento)
- [Solución de problemas](#solución-de-problemas)

---

## Instalación

**Requisitos**

| | |
|---|---|
| Servidor | Spigot, Paper, Purpur o Folia · 1.21.11 |
| Java | 21 |
| Obligatorio | `SiriusCore 1.1.0+` |
| Opcional | `WorldGuard`, `DeluxeCombat`, `PlaceholderAPI`, `MMOItems` |

1. Pon `SiriusCore-1.1.0.jar` en `plugins/`.
2. Pon `SiriusFFA-1.0.0.jar` en `plugins/`.
3. Arranca. Se generan `config.yml`, `messages.yml`, `arenas.yml`, `kits.yml` y
   `events.yml`.

Sin SiriusCore, Bukkit ni intenta cargar el plugin.

**Compilar desde el código**

```bash
cd SiriusCore && mvn clean install      # instala el core en tu repositorio local
cd ../SiriusFFA && mvn clean package    # genera target/SiriusFFA-1.0.0.jar
```

---

## La regla de oro: los inventarios

Esto es lo primero que se diseñó y lo que manda sobre todo lo demás.

Cuando alguien entra a un FFA **con kit**, el orden nunca cambia:

1. se comprueba todo lo comprobable (sesión, arena, aforo, combate, inventario vacío);
2. se fotografía al jugador entero —ítems, armadura, mano secundaria, efectos, vida, vida
   máxima, comida, experiencia, fuego, vuelo, modo de juego y posición— y se **escribe en
   disco**;
3. **solo cuando el disco confirma** se le vacía el inventario y se le entrega el kit.

Si el paso 2 falla, no entra y se le dice por qué. Nunca al revés.

El respaldo vive en `plugins/SiriusFFA/inventarios/<uuid>.yml`, se escribe de forma
atómica (fichero temporal y `move`) y se consume **exactamente una vez**: al salir, al
terminar el evento, al desconectarse o al volver tras un reinicio. Si el servidor se cae
con gente dentro, al arrancar de nuevo el plugin avisa por consola de cuántos inventarios
quedaron pendientes y se los devuelve en cuanto esos jugadores vuelvan a entrar.

Los ítems se guardan con la serialización de Bukkit y en Base64, sin reconstruirlos campo
a campo. Por eso vuelven **idénticos**: datos de MMOItems y MythicLib, encantamientos
personalizados, `CustomModelData`, PDC, atributos, texturas, nombre y lore. Copiar campo a
campo es justo lo que hace perder lo que el plugin no conoce.

Además, mientras un jugador lleva un kit prestado no puede tirarlo al suelo, meterlo en un
cofre, en su enderchest, en una shulker ni en la mochila de otro plugin, y al morir no lo
suelta.

En una arena **sin kit** no se toca nada: el jugador pelea con lo suyo, lo gasta, lo pierde
y lo deja caer si muere, como en cualquier otro sitio del survival. De eso se encarga
WorldGuard con sus banderas de siempre.

---

## Crear una arena

```
/ffa create arena-1           crea la arena y la deja seleccionada
/ffa pos1                     en una esquina
/ffa pos2                     en la opuesta  → aquí se crea la región de WorldGuard
/ffa setspawn arena-1         punto de aparición
/ffa addspawn arena-1         tantos como quieras: con varios no se reaparece encima
/ffa setkit arena-1 pvp       o déjala sin kit
/ffa setlobby arena-1         sala de espera, fuera del cuboide (arenas de eventos)
```

El orden de los dos primeros da igual: si marcas las esquinas antes, `/ffa create` las usa.
Para volver a editar otra arena más tarde, `/ffa select <arena>`.

**La arena y la región son la misma cosa.** Al marcar la segunda esquina se guardan los
límites y se crea `siriusffa_arena-1` con esas mismas coordenadas y `PVP: ALLOW`. No hay
un segundo comando que se pueda olvidar, y si la región falta al arrancar —porque
WorldGuard se instaló después o porque alguien la borró— se vuelve a crear sola y se dice
por consola.

A partir de ahí **manda WorldGuard**: las banderas, los miembros y la prioridad son tuyas
(`/rg flag siriusffa_arena-1 pvp allow`, `build deny`, lo que necesites) y SiriusFFA no las
vuelve a tocar. Si redefines los límites, la región se redefine conservando lo que
hubieras configurado.

Y si aun así el PVP está denegado ahí —por una región global o por el mundo— el plugin lo
avisa por consola al arrancar y al empezar un evento, con el comando exacto que lo arregla.

Los límites también sirven de frontera: quien intente salirse vuelve dentro
(`boundary.action`), y los teletransportes que sacarían al jugador de la arena se cancelan
salvo los que autorices.

---

## Crear un kit

```
/ffa kitcreate ffa-normal     reserva el nombre
                              (ponte en el inventario exactamente lo que quieres)
/ffa kitsave ffa-normal       lo guarda tal cual
```

Tus cosas no se tocan: el kit se guarda como copia. En `kits.yml` puedes ajustar a mano
tres cosas de cada kit:

```yaml
effects:                # 'efecto;segundos;nivel' — 0 segundos = toda la partida
  - 'resistance;0;1'
  - 'strength;0;0'
health-boost: 5         # corazones extra: cada nivel son 2, así que esto son 20 en total
clear-before: true      # vaciar antes de entregarlo
```

Un kit son **ítems**. Lo que hace fuerte a un rol —más vida, más daño, resistencia— no va
en el kit sino en los efectos del rol, en `events.yml`: ver *Los roles*, más abajo.

### Nada de atributos

**SiriusFFA no escribe ni un solo atributo del jugador.** La vida extra de un kit es un
efecto `health_boost` y el daño extra es `strength`, nunca `MAX_HEALTH` ni
`ATTACK_DAMAGE`.

El motivo es que un atributo es un valor **permanente**: se queda escrito en el jugador
hasta que alguien lo devuelva a su sitio. Basta un apagón a destiempo, o un plugin de
sincronización entre servidores —**HuskSync** y similares— que copie al jugador antes de
que la sesión se cierre, para que se quede con veinte corazones para siempre y sin rastro
de dónde salieron. Un efecto de poción caduca solo, se ve en pantalla y se va con la
sesión.

Por lo mismo, al entrar tampoco se le "resetea" la vida máxima a 20: si otro plugin de tu
servidor le ha dado corazones, son suyos y siguen ahí.

Si usas HuskSync, sube `general.recovery-delay-ticks` (por ejemplo a `40`) para que la
recuperación de inventarios tras una caída ocurra **después** de que HuskSync haya
aplicado sus datos al entrar.

---

## Eventos y modos

Todo en `events.yml`. Un evento apunta a un modo, a una arena (márcala con
`for-events: true`) y a sus kits.

| Modo | Qué hace | Ajustes propios |
|---|---|---|
| `classic` | Todos contra todos | `kills-to-win`, `max-time` |
| `juggernaut` | Uno contra todos, por rondas | `rounds.survival-time`, `rounds.max-rounds`, `prevent-consecutive-selection` |
| `gun-game` | Un kit por nivel, cada kill sube uno | `levels`, `lose-level-on-death` |
| `one-in-the-chamber` | Una flecha, una muerte | `lives`, `starting-ammo`, `ammo-per-kill`, `instant-kill-projectile`, `melee-damage` |

**Ciclo de vida**

```
IDLE → /ffa start juggernaut → WAITING → (min-players) → STARTING → cuenta atrás
     → ACTIVE → condición de victoria → ENDING → recompensas → FINISHED → IDLE
```

**La sala de espera**: al entrar con `/ffa join event` los jugadores van a la sala de
espera, **fuera de la arena**, y ahí aguardan mientras corre la cuenta atrás. No hay PVP ni
frontera que los empuje. Al llegar a cero se les lleva a los puntos de aparición con su kit
puesto. Se define con `/ffa setlobby` (global) o `/ffa setlobby <arena>` (propia de esa
arena); sin ninguna, esperan en el propio punto de aparición.

**Los roles**: cada modo reparte papeles —el juggernaut tiene `juggernaut` y `normal`, los
demás solo `normal`—, y **un rol no es un kit: son efectos**. El juggernaut pelea con el
mismo equipo que sus cazadores; lo que le hace duro se escribe en `events.yml`:

```yaml
kits:
  normal: ffa-normal      # el único kit del modo: lo lleva todo el mundo
effects:
  juggernaut:
    - 'resistance;0;0'    # 'efecto;segundos;nivel' — 0 segundos = toda la ronda
    - 'health_boost;0;4'  # +10 corazones, en poción y no en atributo
    - 'strength;0;0'
    - 'regeneration;0;0'
    - 'speed;0;0'
glow:
  juggernaut: true        # para que todos sepan a quién van
keep-food:
  juggernaut: true        # su ronda es larga y sin comida deja de poder correr
```

Se aplican encima del kit y desaparecen con el rol: cuando el juggernaut cae, el siguiente
recibe todo eso y el anterior lo pierde en el mismo momento.

Escribir `kits.juggernaut` ya no sirve de nada y la consola te lo dice al arrancar. El
motivo de quitarlo: un kit por rol se queda a medias en cuanto el kit no existe o no
coincide con el papel, y el jugador aparece en mitad del mapa con el equipo que no era.
Un efecto se ve en su barra, caduca solo y se cambia sin tocar ningún kit.

Si copias el evento con un id propio (`jagger`, `juggernaut-navidad`), ese evento **no
hereda** nada del `events.yml` del jar: cópiate también sus `effects`, su `glow` y su
`push`. Si no lo haces, el modo pone unos efectos de serie —resistencia, fuerza,
regeneración, velocidad y 10 corazones— y lo avisa por consola, para que el rol no se
quede en un jugador normal al que persiguen todos.

**Juggernaut**, que es el que más lógica tiene:

- se elige un juggernaut al azar y se le da su kit;
- tiene que aguantar `survival-time`; si lo consigue, gana el evento;
- si cae antes, se cierra su ronda, **el rol pasa a otro** y el reloj vuelve a empezar;
- quien muere **sigue jugando** como uno más: no se le saca del evento;
- con `prevent-consecutive-selection` no le toca al mismo dos rondas seguidas, salvo que
  no quede nadie más a quien elegir;
- si se acaban las rondas, el tiempo o los jugadores sin que nadie complete la suya, el
  evento termina **sin ganador**: aguantar más que otro no es aguantar;
- lleva la **vara de empuje** (`push` en `events.yml`): golpeando manda lejos, y con clic
  derecho suelta una onda que aparta a todo el que tenga cerca.

Solo puede haber un evento en marcha a la vez, para que `/ffa join event` no sea ambiguo.

---

## Eventos automáticos

Para que el servidor tenga vida sin acordarse de escribir `/ffa start`:

```yaml
scheduler:
  enabled: true
  interval: 2h          # se cuenta desde que termina el anterior
  first-delay: 30m
  min-online: 6
  hours: [16, 17, 18, 19, 20, 21, 22]   # vacío = a cualquier hora
  random: false
  rotation: [juggernaut, gun-game, classic, one-in-the-chamber]
```

Si no hay bastante gente o estamos fuera de horas **no se pierde el turno**: el evento
sale en cuanto se cumplan las condiciones. Y mientras haya un evento en marcha —lo haya
lanzado esto o un administrador— la cuenta se aplaza, para no encadenar dos seguidos.

| | |
|---|---|
| `/ffa next` | cuánto queda para el próximo (lo ve cualquiera) |
| `/ffa schedule now` | lanzarlo ya |
| `/ffa schedule skip` | saltar el turno |

Es una sola tarea, que además solo existe si `enabled: true`.

---

## Placeholders

Con PlaceholderAPI instalado se registra la expansión `siriusffa`. Todos responden desde
memoria: ninguno consulta la base de datos, porque un placeholder se resuelve en el hilo
principal y muchas veces por segundo.

```
%siriusffa_in_ffa%            %siriusffa_in_arena%        %siriusffa_in_event%
%siriusffa_arena%             %siriusffa_arena_id%
%siriusffa_kills%             %siriusffa_deaths%          %siriusffa_streak%

%siriusffa_total_kills%       %siriusffa_total_deaths%    %siriusffa_ratio%
%siriusffa_best_streak%       %siriusffa_events%          %siriusffa_wins%
%siriusffa_juggernaut_wins%   %siriusffa_playtime%        %siriusffa_stat_<nombre>%

%siriusffa_event%             %siriusffa_event_mode%      %siriusffa_event_state%
%siriusffa_event_players%     %siriusffa_event_round%     %siriusffa_event_time%
%siriusffa_event_countdown%   %siriusffa_arena_players_<arena>%

%siriusffa_next_event%        %siriusffa_next_time%
%siriusffa_top_name_kills_1%  %siriusffa_top_value_kills_1%
```

Las clasificaciones se refrescan en segundo plano como mucho una vez por minuto, así que
puedes poner diez puestos en un scoreboard sin coste.

---

## Comandos

**Jugador**

| Comando | |
|---|---|
| `/ffa` | selector |
| `/ffa join <arena>` | entrar a una arena |
| `/ffa join event` | entrar al evento en marcha |
| `/ffa leave` | salir |
| `/ffa list` · `/ffa info <arena>` | qué hay |
| `/ffa next` | cuándo sale el próximo evento automático |
| `/ffa stats [jugador]` · `/ffa top [dato]` | estadísticas |

**Administración**

| Comando | |
|---|---|
| `/ffa start <evento>` · `/ffa stop` | lanzar y parar eventos |
| `/ffa schedule now` · `/ffa schedule skip` | eventos automáticos |
| `/ffa create <arena>` · `/ffa delete <arena>` | arenas |
| `/ffa pos1` · `/ffa pos2` | esquinas (y región de WorldGuard) |
| `/ffa select <arena>` · `/ffa bounds <arena>` | elegir arena en edición y aplicarle la selección |
| `/ffa setspawn [arena]` · `/ffa addspawn [arena]` · `/ffa clearspawns [arena]` | apariciones |
| `/ffa setkit <arena> <kit>` · `/ffa setlobby [arena]` | kit y sala de espera |
| `/ffa kitcreate <id>` · `/ffa kitsave <id>` · `/ffa kitdelete <id>` · `/ffa kitlist` | kits |
| `/ffa reload` | recargar |

Sin argumento de arena, `setspawn`, `addspawn` y `clearspawns` usan aquella en la que
estés parado.

---

## Permisos

```
siriusffa.join              entrar          (por defecto: todos)
siriusffa.leave             salir           (por defecto: todos)

siriusffa.admin             todo lo de abajo
siriusffa.create            crear arenas
siriusffa.delete            borrar arenas
siriusffa.edit              editar arenas
siriusffa.kit               kits
siriusffa.event             gestionar eventos
siriusffa.start             lanzar un evento
siriusffa.stop              pararlo
siriusffa.reload            recargar

siriusffa.bypass.inventory  entrar con cosas encima a un ffa con kit
siriusffa.bypass.commands   usar cualquier comando dentro
siriusffa.bypass.teleport   teletransportarse fuera (cierra la sesión y devuelve todo)
```

---

## Política de comandos

SiriusFFA **no ejecuta, no reemplaza ni reescribe** ningún comando ajeno: solo lo deja
pasar o lo corta. Si permites `/repair`, lo atiende el plugin de siempre, con sus permisos
y sus tiempos de espera.

```
la del evento o la arena  →  la de su tipo (con kit / sin kit / eventos)  →  la global
```

Cuatro modos: `WHITELIST`, `BLACKLIST`, `ALLOW_ALL`, `DENY_ALL`. Los alias cuentan como el
comando real (`/casa` se resuelve a `/home`), y el prefijo del plugin tampoco cuela
(`/essentials:home` es `home`). `/ffa` siempre está permitido.

**Salir con `/spawn`** funciona así: el comando se deja pasar y la sesión **no** se cierra
todavía. Si el teletransporte llega a ocurrir, ahí se cierra y se le devuelve todo. Si otro
plugin lo cancela —DeluxeCombat porque está en combate, por ejemplo— el jugador se queda
dentro como si nada. No hay un `/spawn` alternativo.

---

## Integraciones

| Plugin | Qué hace SiriusFFA |
|---|---|
| **SiriusCore** | textos, menús, planificador, config, base de datos, ítems. No se reimplementa nada de esto |
| **WorldGuard** | crea la región de cada arena y se aparta |
| **DeluxeCombat** | le pregunta si un jugador está en combate. No lleva su propio sistema de combate |
| **MMOItems / MythicLib** | nada que hacer: los kits guardan el ítem entero, así que sus datos viajan solos |
| **PlaceholderAPI** | registra la expansión `siriusffa` y se usa para consultar a DeluxeCombat |
| **HuskSync** y sincronizadores | no hay atributos que sincronizar, y `general.recovery-delay-ticks` deja que escriban ellos primero |

La consulta a DeluxeCombat va por PlaceholderAPI y el marcador se configura
(`deluxecombat.combat-placeholder`), en vez de atarse a una versión concreta de su API
interna. Ajústalo al que exponga tu versión.

---

## Añadir un modo nuevo

Un modo decide tres cosas: qué kit lleva cada uno, qué pasa al matar o morir, y cuándo se
ha ganado. Entrar, salir, inventarios, cuenta atrás, recompensas y HUD ya están hechos.

```java
public final class SumoMode extends FFAMode {

    public SumoMode(FFAEvent event) {
        super(event);
    }

    @Override
    public String id() {
        return "sumo";
    }

    @Override
    public void onDeath(Player victim) {
        event.eliminate(victim.getUniqueId());
    }
}
```

```java
plugin.modes().register("sumo", SumoMode::new);
```

Y en `messages.yml`, una lista `hud.sumo` con lo que quieras enseñar. Cada partida crea su
propia instancia del modo, así que su estado se va con el evento: no hay mapas globales
que limpiar.

---

## Rendimiento

- **Un solo reloj** de un segundo para todo el plugin: cuenta atrás, rondas, modos y HUD.
  Ni una tarea por evento, ni mucho menos por jugador. Se enciende cuando alguien entra al
  FFA y se cancela solo cuando no queda nadie.
- **Ningún bucle global por tick.** La frontera de la arena se comprueba en el movimiento
  del jugador, y solo si ha cambiado de bloque: son seis comparaciones de enteros.
- **Ninguna consulta por movimiento.** Las estadísticas se suman en memoria y se vuelcan en
  lotes cada pocos minutos, al desconectar y al apagar.
- **Dos escrituras por partida** en el respaldo de inventario: una al entrar y un borrado al
  salir.
- **Los eventos automáticos** son una tarea de veinte segundos que solo existe si están
  activados, y los placeholders de clasificación salen de una caché de un minuto.
- Todo son mapas `UUID → sesión` y `arena → jugadores`: O(1).

---

## Solución de problemas

**«no puedes entrar con cosas encima»** — el FFA presta un kit, así que exige el
inventario, la armadura y la mano secundaria vacíos. Se puede quitar por arena
(`require-empty-inventory: false`) o por evento, pero es la protección que evita que se
usen las arenas para colar objetos.

**«no se pudo guardar tu inventario»** — el respaldo no llegó a disco y por eso no se
entra. Mira los permisos de `plugins/SiriusFFA/inventarios/` y la consola.

**Un jugador perdió sus cosas** — no debería poder pasar, pero si el fichero
`plugins/SiriusFFA/inventarios/<uuid>.yml` sigue ahí, sus cosas siguen ahí: al entrar se
las devuelve. Ese fichero solo se borra cuando se le ha devuelto todo.

**No se pueden pegar dentro de la arena** — casi siempre es WorldGuard: la región no
existe o tiene `pvp deny`. Mira la consola al arrancar, que lo dice con el comando exacto,
y comprueba `/rg info siriusffa_<arena>`. Recuerda también que durante la cuenta atrás el
PVP está apagado a propósito: solo se pega cuando el evento pasa a **en vivo**.

**El evento no empieza nunca** — le falta gente: necesita `min-players` para arrancar la
cuenta atrás, y se cancela solo pasado `wait-timeout`. El estado se ve en el marcador y en
`/ffa list`.

**Se sale de la arena teletransportándose** — revisa `teleport.allowed-causes` (vacío por
defecto) y que la arena tenga límites definidos con `pos1`/`pos2`.

**El marcador pisa al de otro plugin** — pon `hud.type: ACTIONBAR` o `NONE`. El tablero
anterior de cada jugador se guarda y se le devuelve al salir.

---

Hecho por **Sirius** · [github.com/JhonTova](https://github.com/JhonTova)
