# Plan de Reimplementación de Features

Este documento detalla cómo implementar cada una de las siguientes features en un mod distinto al actual (`bbs-fs`).
El objetivo es que cada feature esté autocontenida en archivos **nuevos** siempre que sea posible, para minimizar conflictos de merge/fetch con el repositorio base.

---

## Convenciones generales

- El mod base al que se referencia es `bbs` (el juego base de macrosoft).
- El mod actual donde están implementadas las features es `bbs-fs` (este repositorio).
- El nuevo mod de destino se llamará aquí `[TARGET_MOD]`.
- Cada feature indica qué archivos son **nuevos** (solo en `[TARGET_MOD]`) y cuáles son **modificados** (archivos ya existentes en `bbs` que necesitan edición mínima).

---

## 1. Nametag (Toggle de comportamiento de agachado en bbs:label)

### Qué hace
Agrega un toggle llamado **"Nametag"** al form `bbs:label` (el `LabelForm`). Cuando ese toggle está activado **y** el `bbs:label` está como una `BodyPart` con el toggle **"Use target"** activo, el label reacciona al estado de agachado del replay (o entity) al que está atachado:

- **Cuando el entity está agachado (`isSneaking()` = true):**
  - El label se desplaza **-0.5 unidades en Y** (baja un poco)
  - La opacidad del label (todos sus colores: texto, sombra, fondo) se multiplica por **0.125** (queda casi transparente)
- **Cuando no está agachado:**
  - Comportamiento normal (opacidad = 1.0, posición sin offset)

> **Relación con "Use target":** En el sistema de `BodyPart`, el toggle `useTarget` hace que el body part use el entity "objetivo" (el replay al que está atachado) como su entity de contexto. Sin `useTarget = true`, el `context.entity` en el renderer sería el entity del propio body part y no el del replay, por lo que `isSneaking()` no devolvería el valor correcto.

### Archivos nuevos en `[TARGET_MOD]`
_Ninguno_. Esta feature se implementa modificando el `LabelForm` y su renderer, ambos en el mod base.

### Archivos a modificar en `[TARGET_MOD]`

#### `LabelForm.java`
Agregar el campo `nametag`:
```java
public final ValueBoolean nametag = new ValueBoolean("nametag", false);
```
Registrarlo en el constructor (después de `billboard`):
```java
this.add(this.nametag);
```

#### `LabelFormRenderer.java`
Agregar el campo de instancia para la alpha:
```java
private float nametagAlpha = 1F;
```

En el método `render3D(FormRenderingContext context)`, al inicio del bloque (después del push del stack y antes de aplicar billboard), agregar la lógica de sneak:
```java
this.nametagAlpha = 1F;

if (this.form.nametag.get() && context.entity != null && context.entity.isSneaking())
{
    context.stack.translate(0F, -0.5F, 0F);
    this.nametagAlpha = 0.125F;
}
```

En todos los puntos donde se calculan colores de texto, sombra o fondo para el render 3D, multiplicar la alpha por `nametagAlpha`:
```java
shadowColor.a *= this.nametagAlpha;
color.a *= this.nametagAlpha;
```

Estos multiplicadores deben aplicarse en todos los paths de render del label:
- `renderString()`: antes de dibujar texto y sombra
- `renderLimitedString()`: idem (si existe ese método en el renderer)
- Cualquier lugar donde se calcula el color del background quad

#### `UILabelFormPanel.java`
Agregar el campo de UI:
```java
public UIToggle nametag;
```

En el constructor:
```java
this.nametag = new UIToggle(UIKeys.FORMS_EDITORS_LABEL_NAMETAG, (b) -> this.form.nametag.set(b.getValue()));
this.nametag.tooltip(UIKeys.FORMS_EDITORS_LABEL_NAMETAG_TOOLTIP);
```

Agregarlo al layout de opciones (junto a `billboard`):
```java
this.options.add(UI.label(UIKeys.FORMS_EDITORS_LABEL_LABEL), this.text, this.billboard, this.nametag, this.color, this.max);
```

En `startEdit(LabelForm form)`:
```java
this.nametag.setValue(form.nametag.get());
```

#### L10n / Strings (UIKeys)
- `FORMS_EDITORS_LABEL_NAMETAG` → `"Nametag"` / `"Nametag"`
- `FORMS_EDITORS_LABEL_NAMETAG_TOOLTIP` → tooltip explicando que al agacharse el label baja de posición y reduce su opacidad (requiere "Use target" activo en el BodyPart)

---

## 2. Indicador de Replays (Selected Replay HUD)

### Qué hace
Muestra en la esquina superior izquierda de la pantalla (cuando el BBS está abierto en background) una pequeña tarjeta con:
- El número de replay y su nombre.
- Un thumbnail 3D del form del replay (respeta la configuración `BBSSettings.listModelPreview`).
- Una línea divisora en la parte inferior de la tarjeta.

Se desplaza hacia abajo automáticamente si el overlay de grabación está activo.
Tiene un toggle estático (`SelectedReplayHudRenderer.visible`) para mostrarlo/ocultarlo.

### Archivos nuevos en `[TARGET_MOD]`

#### `[NEW]` `SelectedReplayHudRenderer.java`
Clase completamente nueva. Renderiza el HUD desde el callback de HUD de Fabric.

```java
package [package].ui.film.replays;

public class SelectedReplayHudRenderer
{
    public static boolean visible = true;

    public static void toggle() { visible = !visible; }

    public static void render(Batcher2D batcher, float tickDelta)
    {
        if (!visible) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options.hudHidden || UIScreen.getCurrentMenu() != null) return;

        UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
        if (panel == null || panel.getData() == null) return;

        Film film = panel.getData();
        Replay replay = panel.replayEditor.getReplay();

        // Si no hay replay seleccionado, mostrar el primero
        if (replay == null)
        {
            if (!film.replays.getList().isEmpty()) replay = film.replays.getList().get(0);
            else return;
        }

        int replayIndex = film.replays.getList().indexOf(replay);
        String prefix = replayIndex >= 0 ? (replayIndex + 1) + ". " : "";
        String displayName = prefix + replay.getName();

        float textScale = 0.75F;
        FontRenderer font = batcher.getFont();
        int scaledTextWidth = (int) Math.ceil(font.getWidth(displayName) * textScale);
        int previewSize = 20;
        int padding = 4;
        int cardHeight = 22;
        int cardWidth = previewSize + scaledTextWidth + padding * 3;

        int x = 6;
        int y = 6;

        // Bajar si hay overlay de grabacion activo
        if (BBSModClient.getFilms().getRecorder() != null && BBSSettings.recordingOverlays.get())
        {
            y = 26;
        }

        batcher.box(x, y, x + cardWidth, y + cardHeight, Colors.setA(0, 0.75F));
        batcher.box(x, y + cardHeight - 1, x + cardWidth, y + cardHeight, BBSSettings.dividerColor());

        // Thumbnail 3D
        Form form = replay.form.get();
        int previewX = x + padding;
        int previewY = y + (cardHeight - previewSize) / 2;

        if (form != null && BBSSettings.listModelPreview.get())
        {
            batcher.clip(previewX, previewY, previewSize, previewSize, sw, sh);
            FormUtilsClient.renderUI(form, context, previewX, previewY, previewX + previewSize, previewY + previewSize);
            batcher.unclip(sw, sh);
        }
        else
        {
            batcher.icon(Icons.POSE, Colors.LIGHTER_GRAY, previewX + (previewSize - 16) / 2, previewY + (previewSize - 16) / 2);
        }

        // Texto escalado
        int textX = previewX + previewSize + padding;
        float textY = y + (cardHeight - font.getHeight() * textScale) / 2F;

        Matrix3x2fStack matrices = batcher.getContext().getMatrices();
        matrices.pushMatrix();
        matrices.translate(textX, textY);
        matrices.scale(textScale, textScale);
        batcher.textShadow(displayName, 0, 0, Colors.WHITE);
        matrices.popMatrix();
    }
}
```

### Archivos a modificar en `[TARGET_MOD]`

#### `BBSModClient.java`
1. Registrar el keybinding `keyToggleReplayIndicator` en el método de registro de keybindings.
2. En el tick handler del cliente, detectar si se presionó el keybinding y llamar `SelectedReplayHudRenderer.toggle()`.
3. Registrar el HUD: `HudRenderCallback.EVENT.register((drawContext, tickDeltaManager) -> SelectedReplayHudRenderer.render(...))`.

#### L10n / Strings
- `KEY_TOGGLE_REPLAY_INDICATOR` → `"Toggle Replay Indicator"` / `"Alternar indicador de replay"`

---

## 3. Selector rápido de Replays (Quick Replay Selector)

### Qué hace
Abre un overlay flotante (modal) que muestra la lista de todos los replays del film actual.
- Se abre con un keybinding configurable.
- Permite navegar con arriba/abajo o scroll del mouse.
- Muestra thumbnail 3D de cada replay.
- Seleccionar un replay (Enter, clic, o volver a presionar el keybinding) lo activa en el editor.
- Muestra un badge verde en el replay que estaba activo al abrir el selector.
- La lista tiene scroll y muestra hasta 7 items a la vez.

### Archivos nuevos en `[TARGET_MOD]`

#### `[NEW]` `UIQuickReplaySelector.java`
Clase completamente nueva que extiende `UIBaseMenu`.

**Estructura:**
```java
public class UIQuickReplaySelector extends UIBaseMenu
{
    private static final int ITEM_HEIGHT = 32;
    private static final int PANEL_WIDTH = 260;
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 20;
    private static final int MAX_VISIBLE_ITEMS = 7;

    private final Film film;
    private final KeyBinding selectorKey;
    private final List<Replay> replays;
    private final Scroll scroll;
    private int selectedIndex = 0;
    private int initialReplayIndex = -1; // Para mostrar el badge verde
}
```

**Métodos clave:**
- `setIndex(int)`: mueve la selección y hace scroll para que sea visible.
- `applySelection()`: activa el replay seleccionado llamando `panel.replayEditor.setReplay(selectedReplay)` y cierra el menú.
- `mouseScrolled(...)`: scroll cambia el índice seleccionado.
- `mouseClicked(...)`: clic en la lista selecciona y confirma; clic fuera cierra.
- `handleKey(...)`: Enter/keybinding = confirmar; Escape = cancelar; Up/Down/W/S = navegar; Home/End/PageUp/PageDown = navegación rápida.
- `renderMenu(...)`: dibuja el panel (header + lista + footer con hint).

**Render del panel:**
- Fondo semitransparente oscuro sobre toda la pantalla.
- Panel centrado: `deepSurface()` como fondo, `baseSurface()` para el header.
- Línea de acento en el top: `primaryColor(Colors.A100)`.
- Items con fondo degradado al estar seleccionados: `gradientHBox(primaryColor(A50), primaryColor(A12))`.
- Barra lateral izquierda en items seleccionados: `primaryColor(A100)`.
- Thumbnail 3D con `FormUtilsClient.renderUI(...)` si `BBSSettings.listModelPreview.get()`.
- Footer con instrucción de uso.

### Archivos a modificar en `[TARGET_MOD]`

#### `BBSModClient.java`
1. Declarar `private static KeyBinding keyQuickReplaySelector`.
2. Registrarlo en el método de keybindings.
3. En el tick handler, cuando se presione, llamar a `keyQuickReplaySelector()`:
```java
private void keyQuickReplaySelector()
{
    UIFilmPanel panel = getDashboard().getPanel(UIFilmPanel.class);
    if (panel != null && panel.getData() != null)
    {
        Film film = panel.getData();
        if (!film.replays.getList().isEmpty())
        {
            UIScreen.open(new UIQuickReplaySelector(film, keyQuickReplaySelector));
        }
    }
}
```

#### L10n / Strings (UIKeys)
- `FILM_QUICK_REPLAY_TITLE` → `"Select Replay"` / `"Seleccionar Replay"`
- `FILM_QUICK_REPLAY_EMPTY` → `"No replays"` / `"Sin replays"`
- `FILM_QUICK_REPLAY_HINT` → `"Press {0} or Enter to confirm"` / `"Presiona {0} o Enter para confirmar"`

---

## 4. Safe Margins (Margenes de seguridad)

### Qué hace
Muestra en el preview del editor de films dos rectángulos de guía superpuestos sobre la imagen:
- **Action Safe** (90%): margen de 5% por cada borde.
- **Title Safe** (80%): margen de 10% por cada borde.

El color de las guías es configurable independientemente del color de otras guías (rule of thirds, etc.).
Se activa con un toggle en los ajustes del viewport.

### Archivos nuevos en `[TARGET_MOD]`
_Ninguno_. Esta feature se implementa completamente editando archivos existentes.

### Archivos a modificar en `[TARGET_MOD]`

#### `BBSSettings.java`
En la sección de declaraciones estáticas, agregar:
```java
public static ValueBoolean editorSafeMargins;
public static ValueInt editorSafeMarginsColor;
```
En el método `register(SettingsBuilder builder)`, dentro de `builder.category("viewport", ...)`:
```java
editorSafeMargins = builder.getBoolean("safe_margins", false);
editorSafeMarginsColor = builder.getInt("safe_margins_color", 0xcccc0000).colorAlpha();
```

> **Nota:** Si ya existe un `editorGuidesColor` para otras guías (rule of thirds, center lines), los safe margins tienen su propio color separado (`editorSafeMarginsColor`).

#### `UIFilmPreview.java`
En el método de render, dentro del bloque `if (needGuides)`, agregar:

```java
if (BBSSettings.editorSafeMargins.get())
{
    int guidesColor = BBSSettings.editorSafeMarginsColor.get();

    // Action Safe - 90% (5% de margen en cada borde)
    int actionMarginX = Math.round(area.w * 0.05F);
    int actionMarginY = Math.round(area.h * 0.05F);
    int aL = area.x + actionMarginX, aR = area.x + area.w - actionMarginX;
    int aT = area.y + actionMarginY, aB = area.y + area.h - actionMarginY;

    context.batcher.box(aL, aT, aL + 1, aB, guidesColor);
    context.batcher.box(aR - 1, aT, aR, aB, guidesColor);
    context.batcher.box(aL, aT, aR, aT + 1, guidesColor);
    context.batcher.box(aL, aB - 1, aR, aB, guidesColor);

    // Title Safe - 80% (10% de margen en cada borde)
    int titleMarginX = Math.round(area.w * 0.10F);
    int titleMarginY = Math.round(area.h * 0.10F);
    int tL = area.x + titleMarginX, tR = area.x + area.w - titleMarginX;
    int tT = area.y + titleMarginY, tB = area.y + area.h - titleMarginY;

    context.batcher.box(tL, tT, tL + 1, tB, guidesColor);
    context.batcher.box(tR - 1, tT, tR, tB, guidesColor);
    context.batcher.box(tL, tT, tR, tT + 1, guidesColor);
    context.batcher.box(tL, tB - 1, tR, tB, guidesColor);
}
```

La condición `needGuides` también debe incluir `editorSafeMargins`:
```java
boolean needGuides = BBSSettings.editorRuleOfThirds.get()
    || BBSSettings.editorSafeMargins.get()
    || BBSSettings.editorCenterLines.get()
    || BBSSettings.editorCrosshair.get();
```

---

## 5. Drop Items On Death (Soltar items al morir)

### Qué hace
Cuando un replay tiene el toggle **Actor** activo y el toggle **Drop Items On Death** activo, al morir el actor, dropea al suelo los ítems que tenía en sus keyframes (hotbar 0-8, offhand, armor head/chest/legs/feet). También dropea los ítems que recogió dinámicamente durante la ejecución (ítems del suelo que pisó).

**Lógica de reconciliación para evitar duplicados:** Si el actor recogió ítems del suelo que coinciden con ítems de sus keyframes, no se dropean dos veces; se resta el solapamiento.

### Archivos a modificar en `[TARGET_MOD]`

#### `Replay.java`
Agregar los campos de datos:
```java
public final ValueBoolean dropItemsOnDeath = new ValueBoolean("drop_items_on_death", false);
public final ValueFloat dropVelocityMinX = new ValueFloat("drop_velocity_min_x", -0.1F);
public final ValueFloat dropVelocityMaxX = new ValueFloat("drop_velocity_max_x", 0.1F);
public final ValueFloat dropVelocityMinY = new ValueFloat("drop_velocity_min_y", 0.1F);
public final ValueFloat dropVelocityMaxY = new ValueFloat("drop_velocity_max_y", 0.25F);
public final ValueFloat dropVelocityMinZ = new ValueFloat("drop_velocity_min_z", -0.1F);
public final ValueFloat dropVelocityMaxZ = new ValueFloat("drop_velocity_max_z", 0.1F);
```
Registrarlos en el constructor:
```java
this.add(this.dropItemsOnDeath);
this.add(this.dropVelocityMinX); this.add(this.dropVelocityMaxX);
this.add(this.dropVelocityMinY); this.add(this.dropVelocityMaxY);
this.add(this.dropVelocityMinZ); this.add(this.dropVelocityMaxZ);
```

#### `ActorEntity.java`
Esta es la pieza central. Agregar:

**Campos:**
```java
private boolean replayItemsDropped;
private final List<ItemStack> runtimeInventory = new ArrayList<>();
private final Set<UUID> pickedUpEntityIds = new HashSet<>();
```

**En el método `tick()`** (solo server-side, solo si no está muerto):
```java
Box box = this.getBoundingBox().expand(1D, 0.5D, 1D);
for (Entity entity : this.getEntityWorld().getOtherEntities(this, box))
{
    if (entity instanceof ItemEntity itemEntity)
    {
        UUID entityId = itemEntity.getUuid();
        if (!entity.isRemoved() && !itemEntity.cannotPickup() && !this.pickedUpEntityIds.contains(entityId))
        {
            this.pickedUpEntityIds.add(entityId);
            this.runtimeInventory.add(itemEntity.getStack().copy());
            ((ServerWorld) this.getEntityWorld()).getChunkManager()
                .sendToOtherNearbyPlayers(entity, new ItemPickupAnimationS2CPacket(entity.getId(), this.getId(), itemEntity.getStack().getCount()));
            entity.discard();
        }
    }
}
```

**Override de `onDeath(DamageSource)`:**
```java
@Override
public void onDeath(DamageSource damageSource)
{
    // Fix: fijar velocidad para evitar saltos aleatorios durante animacion de muerte
    this.setVelocity(0D, Math.min(0D, this.getVelocity().y), 0D);

    super.onDeath(damageSource);

    this.findReplay(); // Asegurar que this.replay y this.currentTick esten actualizados

    boolean dropEnabled = this.replay != null && this.replay.dropItemsOnDeath.get();

    if (!this.getEntityWorld().isClient() && !this.replayItemsDropped && dropEnabled)
    {
        this.dropReplayItems();
        this.replayItemsDropped = true;
    }
}
```

**Override de `takeKnockback(double, double, double)`:**
```java
@Override
public void takeKnockback(double strength, double x, double z)
{
    // Actores de replay son dirigidos por keyframes; ignorar knockback de daño
    if (this.replay != null || this.findReplay() != null)
    {
        return;
    }

    super.takeKnockback(strength, x, z);
}
```

**Método `dropReplayItems()`:**
```java
private void dropReplayItems()
{
    List<ItemStack> toDrop = new ArrayList<>();
    List<ItemStack> matchedPool = new ArrayList<>();

    // 1. Items recogidos en runtime
    for (ItemStack stack : this.runtimeInventory)
    {
        if (stack != null && !stack.isEmpty())
        {
            ItemStack copy = stack.copy();
            toDrop.add(copy);
            matchedPool.add(copy.copy());
        }
    }

    // 2. Items de los keyframes al tick actual
    List<ItemStack> keyframeItems = new ArrayList<>();

    if (this.replay != null && this.replay.keyframes != null)
    {
        float tick = this.currentTick;

        for (int i = 0; i < this.replay.keyframes.hotbar.size(); i++)
        {
            ItemStack s = this.replay.keyframes.hotbar.get(i).interpolate(tick, ItemStack.EMPTY);
            if (!s.isEmpty()) keyframeItems.add(s.copy());
        }

        ItemStack offHand = this.replay.keyframes.offHand.interpolate(tick, ItemStack.EMPTY);
        if (!offHand.isEmpty()) keyframeItems.add(offHand.copy());

        ItemStack head  = this.replay.keyframes.armorHead.interpolate(tick, ItemStack.EMPTY);
        if (!head.isEmpty())  keyframeItems.add(head.copy());
        ItemStack chest = this.replay.keyframes.armorChest.interpolate(tick, ItemStack.EMPTY);
        if (!chest.isEmpty()) keyframeItems.add(chest.copy());
        ItemStack legs  = this.replay.keyframes.armorLegs.interpolate(tick, ItemStack.EMPTY);
        if (!legs.isEmpty())  keyframeItems.add(legs.copy());
        ItemStack feet  = this.replay.keyframes.armorFeet.interpolate(tick, ItemStack.EMPTY);
        if (!feet.isEmpty())  keyframeItems.add(feet.copy());
    }

    // Fallback: usar equipment slots si no hay keyframes
    if (keyframeItems.isEmpty())
    {
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            ItemStack equipped = this.getEquippedStack(slot);
            if (equipped != null && !equipped.isEmpty())
                keyframeItems.add(equipped.copy());
        }
    }

    // 3. Reconciliacion: no dropear duplicados
    for (ItemStack kStack : keyframeItems)
    {
        if (kStack.isEmpty()) continue;

        int needed = kStack.getCount();

        for (ItemStack matched : matchedPool)
        {
            if (!matched.isEmpty() && matched.isOf(kStack.getItem()) && ItemStack.areEqual(matched, kStack))
            {
                int take = Math.min(matched.getCount(), needed);
                matched.decrement(take);
                needed -= take;
                if (needed <= 0) break;
            }
        }

        if (needed > 0)
        {
            ItemStack add = kStack.copy();
            add.setCount(needed);
            toDrop.add(add);
        }
    }

    // 4. Spawnear drops
    for (ItemStack stack : toDrop)
    {
        if (stack != null && !stack.isEmpty())
            this.dropItemStack(stack);
    }

    this.runtimeInventory.clear();
    this.pickedUpEntityIds.clear();
}
```

**Método `dropItemStack(ItemStack)`:**
```java
private void dropItemStack(ItemStack stack)
{
    if (stack.isEmpty()) return;

    ItemEntity itemEntity = new ItemEntity(
        this.getEntityWorld(),
        this.getX(), this.getY() + 0.5D, this.getZ(),
        stack
    );

    float minX = this.replay != null ? this.replay.dropVelocityMinX.get() : -0.1F;
    float maxX = this.replay != null ? this.replay.dropVelocityMaxX.get() : 0.1F;
    float minY = this.replay != null ? this.replay.dropVelocityMinY.get() : 0.1F;
    float maxY = this.replay != null ? this.replay.dropVelocityMaxY.get() : 0.25F;
    float minZ = this.replay != null ? this.replay.dropVelocityMinZ.get() : -0.1F;
    float maxZ = this.replay != null ? this.replay.dropVelocityMaxZ.get() : 0.1F;

    itemEntity.setVelocity(
        minX + this.random.nextDouble() * (maxX - minX),
        minY + this.random.nextDouble() * (maxY - minY),
        minZ + this.random.nextDouble() * (maxZ - minZ)
    );
    itemEntity.setToDefaultPickupDelay();

    this.getEntityWorld().spawnEntity(itemEntity);
}
```

#### `UIReplayPropertiesPanel.java` (o el panel overlay equivalente)
Agregar los controles de UI en la seccion miscellaneous/other, despues del toggle `actor`:

```java
public UIToggle dropItemsOnDeath;
public UIElement dropVelocityGroup;
public UITrackpad dropVelocityMinX, dropVelocityMaxX;
public UITrackpad dropVelocityMinY, dropVelocityMaxY;
public UITrackpad dropVelocityMinZ, dropVelocityMaxZ;
```

En el constructor, el toggle controla la visibilidad del grupo:
```java
this.dropItemsOnDeath = new UIToggle(UIKeys.FILM_REPLAY_DROP_ITEMS_ON_DEATH, (b) ->
{
    this.edit((replay) -> BaseValue.edit(replay.dropItemsOnDeath, (v) -> v.set(b.getValue())));
    this.dropVelocityGroup.setVisible(b.getValue());
    this.properties.resize();
});

this.dropVelocityGroup = UI.column(5,
    UI.label(UIKeys.FILM_REPLAY_DROP_VELOCITY),
    UI.row(this.dropVelocityMinX, this.dropVelocityMaxX),
    UI.row(this.dropVelocityMinY, this.dropVelocityMaxY),
    UI.row(this.dropVelocityMinZ, this.dropVelocityMaxZ)
);
```

En `setReplay(Replay replay)`:
```java
this.dropItemsOnDeath.setValue(replay.dropItemsOnDeath.get());
this.dropVelocityMinX.setValue(replay.dropVelocityMinX.get());
this.dropVelocityMaxX.setValue(replay.dropVelocityMaxX.get());
this.dropVelocityMinY.setValue(replay.dropVelocityMinY.get());
this.dropVelocityMaxY.setValue(replay.dropVelocityMaxY.get());
this.dropVelocityMinZ.setValue(replay.dropVelocityMinZ.get());
this.dropVelocityMaxZ.setValue(replay.dropVelocityMaxZ.get());
this.dropVelocityGroup.setVisible(replay.dropItemsOnDeath.get()); // <- importante!
```

#### L10n / Strings
- `FILM_REPLAY_DROP_ITEMS_ON_DEATH` → `"Drop Items On Death"` / `"Soltar items al morir"`
- `FILM_REPLAY_DROP_ITEMS_ON_DEATH_TOOLTIP` → tooltip descriptivo
- `FILM_REPLAY_DROP_VELOCITY` → `"Drop Velocity"` / `"Velocidad de drop"`
- `FILM_REPLAY_DROP_VELOCITY_MIN_X`, `MAX_X`, `MIN_Y`, `MAX_Y`, `MIN_Z`, `MAX_Z`

---



---

## Orden de implementacion recomendado

| # | Feature | Prioridad | Dependencias |
|---|---------|-----------|--------------|
| 1 | **Nametag** | Alta | Requiere `LabelForm`, `LabelFormRenderer`, `UILabelFormPanel` |
| 2 | **Safe Margins** | Media | Solo `BBSSettings` + `UIFilmPreview` |
| 3 | **Drop Items On Death** | Alta | Requiere `Replay`, `ActorEntity`, UI panel |
| 4 | **Indicador de Replays** | Media | `UIFilmPanel`, `SelectedReplayHudRenderer` (nuevo) |
| 5 | **Selector rapido de Replays** | Media | `UIFilmPanel`, `UIQuickReplaySelector` (nuevo), keybinding |

---

## Resumen de archivos nuevos vs modificados

| Feature | Archivos Nuevos | Archivos Modificados |
|---------|----------------|----------------------|
| Nametag | — | `LabelForm.java`, `LabelFormRenderer.java`, `UILabelFormPanel.java`, strings |
| Indicador de Replays | `SelectedReplayHudRenderer.java` | `BBSModClient.java`, strings |
| Selector rapido | `UIQuickReplaySelector.java` | `BBSModClient.java`, strings |
| Safe Margins | — | `BBSSettings.java`, `UIFilmPreview.java` |
| Drop Items On Death | — | `Replay.java`, `ActorEntity.java`, `UIReplayPropertiesPanel.java`, strings |

---

## Fixes criticos a tener en cuenta

1. **Drop Items On Death — salto del actor al morir:**
   Override `takeKnockback()` en `ActorEntity` para ignorar knockback cuando el actor es un replay.
   Override `onDeath()` para fijar `setVelocity(0, Math.min(0, y), 0)` antes de llamar `super.onDeath()`.

2. **Drop Items On Death — duplicados al recoger items:**
   Implementar la logica de reconciliacion entre `runtimeInventory` y los keyframe items para evitar que un mismo item se dropee dos veces cuando el actor recoge un item del suelo que ya tenia en sus keyframes.

3. **Drop Items On Death — visibilidad del grupo de velocidades:**
   Siempre setear `dropVelocityGroup.setVisible(replay.dropItemsOnDeath.get())` en `setReplay()`, no solo en el callback del toggle. De lo contrario, el grupo no aparece hasta cerrar y reabrir el panel.

4. **Nametag — requiere `useTarget = true` en el BodyPart:**
   El toggle `nametag` solo tiene efecto si el `bbs:label` está como `BodyPart` con `useTarget = true`. De lo contrario, `context.entity` en el renderer no corresponde al replay sino al entity del body part mismo, y `isSneaking()` no devolverá el valor correcto del replay.

5. **Nametag — aplicar alpha a TODOS los colores del label:**
   El campo `nametagAlpha` debe multiplicarse por la alpha de todos los colores que se usan en el render 3D del label: texto principal, sombra del texto, y el fondo (background quad). Olvidar alguno resulta en inconsistencias visuales.
