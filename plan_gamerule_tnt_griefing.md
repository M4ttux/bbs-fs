# Plan de implementación: Gamerule `tnt_griefing` (BBS mod, Minecraft 1.21.11)

Repo: `M4ttux/bbs-fs`, rama `1.21.11`. Mappings: Yarn. Loader: Fabric (`fabric_version=0.141.4+1.21.11`).

---

## 0. Instrucciones para quien implemente esto (IA o persona)

1. **Leé antes de escribir.** Los archivos de la sección 2 son la referencia de estilo y de API.
2. **Diseñalo como si fuera un mod externo nuevo, pero viviendo dentro de este mod.** Eso significa: código en su **propio paquete**, **sin depender de nada interno de BBS** (ni `BBSMod.getActions()`, ni forms, ni film, ni networking), con su propio mixin y su propio entrypoint. Si algún día se quiere separar a otro mod, debe poder moverse el paquete y sus dos archivos de configuración sin tocar el resto.
3. **No refactorices ni toques lógica existente.** Los únicos cambios permitidos en archivos existentes están en la sección 4.
4. **Estilo del repo:** llaves en línea nueva (Allman), indentación de 4 espacios.
5. Lo marcado "(verificar)" está escrito de memoria o deducido, no compilado ni probado en el juego. Confirmalo con `./gradlew build` y probando en el juego. **No inventes métodos:** si algo no existe, generá los fuentes de Minecraft (`./gradlew genSources`) y buscá el equivalente.
6. Si algo de la sección 11 resulta distinto a lo asumido, **frená y avisá** en vez de improvisar.

---

## 1. Objetivo

Agregar una gamerule booleana que permita **desactivar el daño a bloques del TNT**, para poder hacer explotar TNT (con sus partículas y su sonido) sin que rompa estructuras.

### Comportamiento

| Valor de `bbs:tnt_griefing` | Resultado |
|---|---|
| `true` (por defecto) | El TNT se comporta como en vanilla. |
| `false` | El TNT explota (efectos visuales y sonido) pero **no destruye bloques**. |

La semántica copia a `mob_griefing`: `true` = permitido (por defecto, sin cambios para nadie), `false` = desactivado.

### Aclaración de alcance (importante)

La regla debe afectar al **TNT colocado y encendido de forma normal en el mundo, fuera de las funciones propias del mod**: es decir, TNT vanilla (bloque de TNT encendido con mechero, redstone, fuego, dispensador, etc. y minecart con TNT). El mod BBS en sí **no debe verse afectado ni depender de esta regla**.

Esto ya se cumple por cómo está el código hoy (verificado leyendo el repo):

- BBS **no crea entidades de TNT** en ningún lado (no hay referencias a `TntEntity` ni a `TntMinecartEntity`).
- El único lugar donde BBS provoca una explosión es el comando `/bbs ... explosion`, en `BBSCommands.java` (~línea 389), que usa `World.ExplosionSourceType.BLOCK`. La regla solo interviene sobre explosiones de tipo `TNT`, así que ese comando **queda intacto**.

Por eso la implementación se limita a explosiones de tipo `ExplosionSourceType.TNT` y no hace falta ningún mecanismo para "excluir" a BBS. (Ver la sección 9 por si en el futuro BBS llegara a crear su propio TNT.)

### Fuera de alcance

- Creepers, ghasts, camas, cristales del End y otras explosiones (siguen la lógica vanilla: `mob_griefing`, etc.).
- Daño a entidades o jugadores por la explosión (no se modifica).
- Reglas por mundo, por dimensión o por zona.
- Modificar el comando `/bbs ... explosion`.
- Interfaz de usuario propia (la regla ya aparece sola en la pantalla de gamerules).

---

## 2. Hallazgos verificados del repo (leer estos archivos primero)

Rutas relativas a `src/`.

| Tema | Archivo | Qué hay que saber |
|---|---|---|
| Gamerule existente (patrón a copiar) | `main/java/mchorse/bbs_mod/BBSMod.java` (~líneas 224-226) | `public static final GameRule<Boolean> BBS_EDITING_RULE = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MISC).buildAndRegister(Identifier.of(MOD_ID, "bbs_editing"));` |
| Lectura de la regla | `main/java/mchorse/bbs_mod/utils/PermissionUtils.java` | `server.getOverworld().getGameRules().getValue(BBSMod.BBS_EDITING_RULE)`. El nuevo sistema de 1.21.11 se lee con `getValue(constante)`. |
| Mixins del lado común | `main/java/mchorse/bbs_mod/mixin/` y `main/resources/bbs.mixins.json` | Config con `"package": "mchorse.bbs_mod.mixin"`, `"compatibilityLevel": "JAVA_21"` y **`"injectors": { "defaultRequire": 1 }`**: si un `@Inject` no encuentra su objetivo, el juego **falla al iniciar** (útil para detectar errores de nombre/firma de inmediato). |
| Mixin sobre `ServerWorld` ya existente | `main/java/mchorse/bbs_mod/mixin/ServerWorldMixin.java` | Ya hay uno con `@Mixin(ServerWorld.class)` (acciones de BBS). **No agregar el código de TNT ahí:** crear un mixin propio (ver 3). |
| Explosiones en el mod | `main/java/mchorse/bbs_mod/BBSCommands.java` (~línea 389) | `createExplosion(null, x, y, z, radius, fire, World.ExplosionSourceType.BLOCK)`. No debe cambiar. |
| Firma de `createExplosion` completa | `client/java/mchorse/bbs_mod/forms/structure/StructureWorld.java` (~línea 318) | Sobrescribe `createExplosion(Entity, DamageSource, ExplosionBehavior, double x, double y, double z, float power, boolean createFire, World.ExplosionSourceType, ParticleEffect, ParticleEffect, WeightedPool<BlockParticleEffect>, RegistryEntry<SoundEvent>)`. Es la sobrecarga "completa" de `World` en 1.21.11, a la que funnel-ean las demás (verificar). |
| Entrypoints | `main/resources/fabric.mod.json` (líneas ~37-44) | `"main": ["mchorse.bbs_mod.BBSMod"]` y `"client": [...]`. Se puede agregar un segundo `main`. |
| Textos | `main/resources/assets/bbs/lang/en_us.json` (línea 34) y `es_es.json` | Hoy solo `en_us.json` tiene `"gamerule.bbsEditing": "BBS mod's editing permission"`. Esa clave parece del formato viejo de gamerules (ver 5.3). |
| Tests | `build.gradle` (~líneas 112-141) | `src/test` contiene **mains manuales, no JUnit**. Esta función no es testeable sin el juego, así que se prueba en el juego (sección 8). |

Referencias externas:
- Registro de gamerules propias en 1.21.11 con Fabric (`GameRuleBuilder`, `GameRuleCategory`, textos, lectura con `getValue(...)`): <https://docs.fabricmc.net/1.21.11/develop/game-rules>
- Cambios de gamerules de 1.21.11 (nombres en `snake_case`, existe `tnt_explodes` y `tnt_explosion_drop_decay`): <https://nodecraft.com/support/games/minecraft/general/minecraft-update-1-21-11-gamerule-changes>

Nota: la gamerule vanilla `tnt_explodes` **no** sirve para este objetivo (parece controlar si el TNT explota o no, no si rompe bloques) y tampoco hay una regla vanilla de daño a bloques del TNT. Por eso se necesita el mixin.

---

## 3. Arquitectura

Todo en un paquete propio, con tres piezas:

```
mchorse.bbs_mod.tntrule
 ├── TntRule.java            -> constante GameRule<Boolean> + registro + helper de lectura
 ├── TntRuleInit.java        -> ModInitializer: dispara el registro (segundo entrypoint "main")
 └── mixin/ (en el paquete de mixins existente)
      └── TntGriefingMixin.java  -> intercepta explosiones de tipo TNT
```

Los mixins deben estar en el paquete declarado por `bbs.mixins.json` (`mchorse.bbs_mod.mixin`), así que `TntGriefingMixin` va en `main/java/mchorse/bbs_mod/mixin/`, aunque el resto de la lógica viva en `mchorse.bbs_mod.tntrule`. Es la única excepción a "todo en su paquete", y se debe a una restricción del config de mixins.

Regla de dependencias: `tntrule` **solo** importa clases de Minecraft y de Fabric. No importa nada de `mchorse.bbs_mod.*` aparte de lo suyo (usar un `MOD_ID` propio, ver 5.1).

---

## 4. Archivos nuevos y modificados

**Nuevos**
- `main/java/mchorse/bbs_mod/tntrule/TntRule.java`
- `main/java/mchorse/bbs_mod/tntrule/TntRuleInit.java`
- `main/java/mchorse/bbs_mod/mixin/TntGriefingMixin.java`

**Modificados (solo estas líneas)**
- `main/resources/fabric.mod.json`: agregar `"mchorse.bbs_mod.tntrule.TntRuleInit"` al arreglo `"main"` (un segundo entrypoint).
- `main/resources/bbs.mixins.json`: agregar `"TntGriefingMixin"` a `"mixins"`.
- `main/resources/assets/bbs/lang/en_us.json` y `es_es.json`: textos de la regla (ver 5.3).

**No tocar:** `BBSMod.java`, `BBSCommands.java`, `ServerWorldMixin.java`, ni ningún otro archivo.

---

## 5. Implementación

### 5.1 `TntRule.java`

```java
package mchorse.bbs_mod.tntrule;

public class TntRule
{
    /* Su propio id de mod: este paquete no depende de BBSMod.MOD_ID. Debe coincidir con el namespace de BBS ("bbs"), asi la regla queda como bbs:tnt_griefing. */
    public static final String NAMESPACE = "bbs";

    public static final GameRule<Boolean> TNT_GRIEFING = GameRuleBuilder.forBoolean(true)
        .category(GameRuleCategory.MISC)
        .buildAndRegister(Identifier.of(NAMESPACE, "tnt_griefing"));

    /** Llamado al iniciar: fuerza la carga de la clase para que la regla se registre. */
    public static void init()
    {}

    /** Si, para este mundo, el TNT debe romper bloques. */
    public static boolean isTntGriefingEnabled(ServerWorld world)
    {
        return world.getGameRules().getValue(TNT_GRIEFING);
    }
}
```

Notas:
- `GameRuleBuilder`, `GameRuleCategory` e `Identifier.of(...)`: tomar los imports y la firma exactos de cómo ya lo hace `BBSMod.java` en la rama (es el mismo código que compila hoy), no de los ejemplos de la documentación (que usan mappings oficiales distintos, por ejemplo `Identifier.fromNamespaceAndPath`).
- Categoría: `MISC`, igual que la regla existente. Si se prefiere otra, verificar los valores de `GameRuleCategory` en esta versión (no inventar).
- El registro de la gamerule debe ocurrir durante la inicialización del mod, **antes** de que se cargue cualquier mundo. Por eso se registra en un `ModInitializer` y no de forma perezosa.

### 5.2 `TntRuleInit.java`

```java
package mchorse.bbs_mod.tntrule;

public class TntRuleInit implements ModInitializer
{
    @Override
    public void onInitialize()
    {
        TntRule.init();
    }
}
```

Y en `fabric.mod.json`:

```json
"main": [
    "mchorse.bbs_mod.BBSMod",
    "mchorse.bbs_mod.tntrule.TntRuleInit"
],
```

Hay que confirmar que cargar `TntRule` desde un segundo entrypoint registra la regla una sola vez y que no hay problema de orden con el registro de `BBS_EDITING_RULE` (no deberían depender entre sí).

### 5.3 Textos (`lang`)

Agregar a `en_us.json` y `es_es.json`:

| Clave candidata | en_us | es_es |
|---|---|---|
| nombre de la regla | TNT griefing | Daño del TNT a bloques |
| descripción | Whether TNT explosions destroy blocks. If false, TNT still explodes but leaves blocks intact. | Si las explosiones de TNT destruyen bloques. Si es falso, el TNT explota pero deja los bloques intactos. |

**Verificar la clave exacta.** En 1.21.11 la clave depende del id de la regla. Las candidatas más probables son `gamerule.bbs.tnt_griefing` (nombre) y `gamerule.bbs.tnt_griefing.description` (descripción). La clave existente `gamerule.bbsEditing` parece del formato anterior y quizás no esté funcionando. Para comprobarlo:
1. Abrir un mundo, entrar a la pantalla de gamerules y mirar cómo se muestran `bbs_editing` y `tnt_griefing` (con y sin traducción).
2. Si aparece el id sin traducir, buscar en los fuentes de `GameRule` cómo arma su clave de traducción y ajustar las claves de los `lang`.
3. **No arreglar** la clave de `bbs_editing` en este cambio (fuera de alcance); solo anotarlo si resulta estar rota.

### 5.4 `TntGriefingMixin.java` (el efecto)

**Idea:** cuando se crea una explosión de tipo `TNT` y la regla está en `false`, cambiar su tipo a `NONE`. Con `NONE`, la explosión no destruye bloques (ni enciende fuego por esa vía), pero las partículas y el sonido se mantienen y el daño a entidades no se toca.

Opción recomendada (A): interceptar el parámetro de tipo de explosión de la sobrecarga completa de `createExplosion`.

```java
@Mixin(ServerWorld.class)   // o World.class, segun donde viva la implementacion (verificar)
public abstract class TntGriefingMixin
{
    @ModifyVariable(method = "createExplosion", at = @At("HEAD"), argsOnly = true)
    private World.ExplosionSourceType bbs$tntGriefing(World.ExplosionSourceType type)
    {
        if (type == World.ExplosionSourceType.TNT)
        {
            ServerWorld world = (ServerWorld) (Object) this;

            if (!TntRule.isTntGriefingEnabled(world))
            {
                return World.ExplosionSourceType.NONE;
            }
        }

        return type;
    }
}
```

Puntos críticos (verificar con los fuentes generados):

1. **Dónde está implementada la sobrecarga completa.** `World` tiene varias sobrecargas de `createExplosion`; algunas delegan en otras. Hay que apuntar a **la última de la cadena, la que contiene el parámetro `ExplosionSourceType` y hace el trabajo real**. Si está en `ServerWorld`, usar `@Mixin(ServerWorld.class)`; si está en `World`, usar `World.class` (y convertir con `instanceof ServerWorld`). Con `defaultRequire: 1`, si el nombre o la firma no coinciden el juego **no arranca** y el error lo dice.
2. **Si hay más de una sobrecarga con ese parámetro**, `@ModifyVariable` con `method = "createExplosion"` puede ser ambiguo. En ese caso especificar la firma completa en `method = "createExplosion(...)"` o usar `@ModifyVariable(..., ordinal = ...)` según corresponda.
3. **Que el TNT realmente use `ExplosionSourceType.TNT`.** En los fuentes de `TntEntity#explode` y de `AbstractMinecartEntity`/`TntMinecartEntity` (el minecart con TNT) confirmar que llaman a `createExplosion` con `ExplosionSourceType.TNT`. Si el minecart usa otro tipo, ver 5.5.
4. **Solo servidor.** Las explosiones se resuelven en el servidor. El mixin no necesita ningún cambio del lado cliente (`bbs.client.mixins.json`).

### 5.5 Opción alternativa (B), si A no funciona o es ambigua

Apuntar directamente a las clases del TNT vanilla, para que **solo** afecte al TNT encendido (bloque y minecart) y no a cualquier otra cosa que use el tipo `TNT`:

- `@Mixin(TntEntity.class)` (TNT primado): `@ModifyArg` sobre la llamada a `createExplosion` dentro de `explode()`, cambiando el argumento de tipo de explosión.
- `@Mixin(TntMinecartEntity.class)`: lo mismo en su método de explosión.

Es más precisa, pero requiere un `@At(value = "INVOKE", target = "...createExplosion...")` con la firma exacta (frágil entre versiones) y cubrir cada clase. Usarla solo si la opción A resulta ambigua o si algún otro mod/funcionalidad usa `ExplosionSourceType.TNT` y no debe verse afectado.

---

## 6. Fases y criterios de aceptación

### Fase 1: La regla existe (sin efecto todavía)

`TntRule`, `TntRuleInit`, entrypoint en `fabric.mod.json` y textos.

**Aceptación:**
- El juego arranca sin errores.
- `/gamerule bbs:tnt_griefing` existe, autocompleta, y devuelve `true` por defecto.
- Aparece en la pantalla de gamerules con su nombre y descripción traducidos (en español e inglés).
- Cambiarla con `/gamerule bbs:tnt_griefing false` y reabrir el mundo conserva el valor.
- `bbs:bbs_editing` sigue funcionando igual que antes.

### Fase 2: El efecto

`TntGriefingMixin` y su entrada en `bbs.mixins.json`.

**Aceptación:** pasar todas las pruebas de la sección 8.

---

## 7. Casos borde y comportamiento esperado

- **Reacción en cadena:** con la regla en `false`, no debería encenderse otro TNT cercano por la explosión (porque los bloques no se destruyen). Es lo esperado y no un bug, pero hay que comprobarlo en el juego y anotar lo que ocurra.
- **Daño a entidades:** no cambia. Jugadores, mobs e ítems siguen recibiendo daño y empuje de la explosión. Si se quisiera eliminarlo, es otra funcionalidad.
- **Fuego:** el TNT vanilla no genera fuego, así que no hay interacción relevante.
- **Drops de bloques:** como no se destruyen bloques, no hay drops por la explosión.
- **Valor por defecto `true`:** garantiza que ningún mundo existente cambia de comportamiento al actualizar el mod.
- **Mundos de otras dimensiones:** las gamerules son globales del servidor en vanilla; la regla se aplica a todas las dimensiones. Confirmar que `world.getGameRules()` devuelve el mismo valor desde cualquier `ServerWorld` (verificar).
- **Multijugador:** el mod debe estar instalado en el servidor. El cliente no necesita lógica extra.

---

## 8. Pruebas manuales en el juego (no hay forma de testearlo sin el juego)

Con un mundo de prueba con un bloque de piedra y TNT:

1. Regla en `true`: encender un TNT con mechero. **Rompe bloques** (vanilla).
2. Regla en `false`: encender un TNT. **Explota (sonido y partículas) pero no rompe bloques**.
3. Regla en `false`: TNT encendido con redstone y con un dispensador (si se usa un dispensador).
4. Regla en `false`: **minecart con TNT** (activarlo con un riel activador).
5. Regla en `false`: varios TNT juntos. Anotar si se encadenan.
6. Regla en `false`: parado cerca de la explosión, comprobar que el daño al jugador **sigue ocurriendo** (esperado).
7. Regla en `false`: ejecutar el comando de explosión de BBS (`/bbs ... explosion`, el de `BBSCommands.java`). **Debe seguir rompiendo bloques** (usa tipo `BLOCK`).
8. Regla en `false`: un creeper. Su comportamiento depende de `mob_griefing`, **no** de esta regla (debe seguir las reglas vanilla).
9. Volver a `true` sin reiniciar el mundo: el TNT vuelve a romper bloques (la regla se lee en cada explosión).
10. Cerrar y reabrir el mundo con la regla en `false`: sigue en `false`.
11. Servidor dedicado (si es posible): repetir 1 y 2.

---

## 9. Extensión futura (no implementar ahora)

Si en el futuro BBS creara TNT propio (por ejemplo desde acciones grabadas) y se quisiera que **ese** TNT ignorara la regla, la forma limpia sería marcar la entidad (por ejemplo con una etiqueta de comandos o un campo propio) y que el mixin deje la explosión intacta cuando la entidad origen esté marcada. Hoy no hay ningún TNT creado por BBS, así que no hace falta.

---

## 10. Fuera de alcance (resumen)

- Otras fuentes de explosión (creepers, camas, cristales del End, etc.).
- Daño a entidades.
- Reglas por mundo, dimensión o zona.
- Corregir la clave de texto de `bbs_editing`.
- Interfaz propia.

---

## 11. Cosas a verificar leyendo el código

1. En qué clase (`World` o `ServerWorld`) vive la sobrecarga completa de `createExplosion` con `ExplosionSourceType`, y si hay más de una con ese parámetro (para definir bien el `method` del mixin).
2. Que `TntEntity#explode` y el minecart con TNT llaman con `ExplosionSourceType.TNT`.
3. Que `ExplosionSourceType.NONE` existe en esta versión con el comportamiento esperado (no destruye bloques; mantiene partículas y sonido).
4. La clave de texto correcta del nombre y la descripción de una gamerule en 1.21.11 (y si `gamerule.bbsEditing` está rota).
5. Que un segundo entrypoint `main` registra la regla sin conflicto y antes de cargar mundos.
6. Que `getGameRules().getValue(regla)` es válido desde cualquier `ServerWorld` (la regla es global).
7. Los imports exactos de `GameRule`, `GameRuleBuilder`, `GameRuleCategory` e `Identifier.of` tal como ya compilan en `BBSMod.java`.
