package mchorse.bbs_mod.ui.film.replays.kits;

import mchorse.bbs_mod.data.GameRegistries;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageBarOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.Label;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public class UIRandomKitOverlayPanel extends UIMessageBarOverlayPanel
{
    private final List<Replay> replays;
    private final Film film;
    private final Map<String, Boolean> folds;
    private final Consumer<KitConfig> callback;
    private final KitConfig config;

    private UIScrollView scroll;

    private UICirculate modeCirculate;
    private UIButton savePresetBtn;
    private UIStringList presetList;

    private UISection previewSection;
    private UIButton rerollPreviewBtn;
    private UISection armorSection;
    private UISection trimSection;
    private UIElement trimFields;
    private UISection weaponSection;
    private UISection offhandSection;
    private UISection enchantsSection;
    private UIElement enchantFields;
    private UIElement subsetFields;
    private UISection variationSection;
    private UIElement variationFields;
    private UISection seedSection;
    private UIElement seedFields;

    private final ItemStack[] previewStacks = new ItemStack[6];
    private UIItemStack headSlot;
    private UIItemStack chestSlot;
    private UIItemStack legsSlot;
    private UIItemStack feetSlot;
    private UIItemStack mainHandSlot;
    private UIItemStack offHandSlot;

    private static <T extends UIElement> T tip(T element, IKey key)
    {
        if (element != null && key != null)
        {
            element.tooltip(key);
        }

        return element;
    }

    public static String formatDisplayName(String id)
    {
        if (id == null || id.isEmpty())
        {
            return "";
        }

        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String spaced = path.replace('_', ' ');

        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public UIRandomKitOverlayPanel(List<Replay> replays, Film film, Consumer<KitConfig> callback)
    {
        this(replays, film, new HashMap<>(), callback);
    }

    public UIRandomKitOverlayPanel(
        List<Replay> replays,
        Film film,
        Map<String, Boolean> folds,
        Consumer<KitConfig> callback
    )
    {
        super(UIKeys.SCENE_REPLAYS_KITS_TITLE, IKey.EMPTY);

        this.replays = replays == null ? new ArrayList<>() : replays;
        this.film = film;
        this.folds = folds == null ? new HashMap<>() : folds;
        this.callback = callback;
        this.config = RandomKitHelper.loadConfig().copy();

        this.message.removeFromParent();
        this.confirm.label = UIKeys.SCENE_REPLAYS_KITS_APPLY;
        this.confirm.w(100);
        tip(this.confirm, UIKeys.SCENE_REPLAYS_KITS_APPLY_TOOLTIP);

        this.initPreviewSlots();

        this.scroll = new UIScrollView();
        this.scroll.column(UIConstants.MARGIN).vertical().stretch().scroll().padding(UIConstants.SCROLL_PADDING);
        this.scroll.relative(this.content).xy(6, 6).w(1F, -12).h(1F, -40);
        this.content.add(this.scroll);

        this.buildUI();
        this.fillsOverlay();

        this.updateModeVisibility();
        this.rerollPreview();
    }

    private void refreshSlotTooltip(UIItemStack slot, ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            tip(slot, UIKeys.SCENE_REPLAYS_KITS_PREVIEW_SLOT_TOOLTIP);
            return;
        }

        String name = stack.getName().getString();

        slot.tooltip(IKey.raw(name + "\n" + UIKeys.SCENE_REPLAYS_KITS_PREVIEW_SLOT_TOOLTIP.get()));
    }

    private void initPreviewSlots()
    {
        for (int i = 0; i < 6; i++)
        {
            this.previewStacks[i] = ItemStack.EMPTY;
        }

        this.headSlot = new UIItemStack((s) ->
        {
            this.previewStacks[0] = s;
            this.refreshSlotTooltip(this.headSlot, s);
        });
        this.headSlot.placeholder(Icons.ARMOR_HELMET);
        this.refreshSlotTooltip(this.headSlot, ItemStack.EMPTY);

        this.chestSlot = new UIItemStack((s) ->
        {
            this.previewStacks[1] = s;
            this.refreshSlotTooltip(this.chestSlot, s);
        });
        this.chestSlot.placeholder(Icons.ARMOR_CHESTPLATE);
        this.refreshSlotTooltip(this.chestSlot, ItemStack.EMPTY);

        this.legsSlot = new UIItemStack((s) ->
        {
            this.previewStacks[2] = s;
            this.refreshSlotTooltip(this.legsSlot, s);
        });
        this.legsSlot.placeholder(Icons.ARMOR_LEGGINGS);
        this.refreshSlotTooltip(this.legsSlot, ItemStack.EMPTY);

        this.feetSlot = new UIItemStack((s) ->
        {
            this.previewStacks[3] = s;
            this.refreshSlotTooltip(this.feetSlot, s);
        });
        this.feetSlot.placeholder(Icons.ARMOR_BOOTS);
        this.refreshSlotTooltip(this.feetSlot, ItemStack.EMPTY);

        this.mainHandSlot = new UIItemStack((s) ->
        {
            this.previewStacks[4] = s;
            this.refreshSlotTooltip(this.mainHandSlot, s);
        });
        this.mainHandSlot.placeholder(Icons.HOTBAR);
        this.refreshSlotTooltip(this.mainHandSlot, ItemStack.EMPTY);

        this.offHandSlot = new UIItemStack((s) ->
        {
            this.previewStacks[5] = s;
            this.refreshSlotTooltip(this.offHandSlot, s);
        });
        this.refreshSlotTooltip(this.offHandSlot, ItemStack.EMPTY);
    }

    private void buildUI()
    {
        /* Mode & Preset controls outside sections */
        this.modeCirculate = new UICirculate((c) ->
        {
            this.config.mode = c.getValue() == 1 ? KitConfig.Mode.PRESET : KitConfig.Mode.RANDOM;
            this.updateModeVisibility();
            this.rerollPreview();
        });
        this.modeCirculate.addLabel(UIKeys.SCENE_REPLAYS_KITS_MODE_RANDOM);
        this.modeCirculate.addLabel(UIKeys.SCENE_REPLAYS_KITS_MODE_PRESET);
        this.modeCirculate.setValue(this.config.mode == KitConfig.Mode.PRESET ? 1 : 0);
        tip(this.modeCirculate, UIKeys.SCENE_REPLAYS_KITS_MODE_TOOLTIP);

        this.savePresetBtn = new UIButton(UIKeys.SCENE_REPLAYS_KITS_PRESET_SAVE, (b) -> this.saveCurrentPreviewAsPreset());
        tip(this.savePresetBtn, UIKeys.SCENE_REPLAYS_KITS_PRESET_SAVE_TOOLTIP);

        this.presetList = new UIStringList((list) ->
        {
            if (!list.isEmpty())
            {
                this.config.presetId = list.get(0);
                this.rerollPreview();
            }
        });
        this.presetList.background().h(90);
        this.presetList.add(KitPresets.list());
        tip(this.presetList, UIKeys.SCENE_REPLAYS_KITS_PRESET_SELECT_TOOLTIP);

        if (this.config.presetId != null && KitPresets.exists(this.config.presetId))
        {
            this.presetList.setCurrent(this.config.presetId);
        }
        else if (!KitPresets.list().isEmpty())
        {
            this.config.presetId = KitPresets.list().get(0);
            this.presetList.setCurrent(this.config.presetId);
        }

        this.scroll.add(
            UI.row(this.modeCirculate, this.savePresetBtn),
            this.presetList
        );

        /* 1. Preview section */
        this.previewSection = new UISection(UIKeys.SCENE_REPLAYS_KITS_PREVIEW)
            .remember(this.folds, "preview", true);

        UIElement previewGrid = new UIElement();

        previewGrid.grid(UIConstants.MARGIN).items(3);
        previewGrid.h(2 * 20 + UIConstants.MARGIN);

        previewGrid.add(
            this.headSlot,
            this.chestSlot,
            this.mainHandSlot,
            this.legsSlot,
            this.feetSlot,
            this.offHandSlot
        );
        this.rerollPreviewBtn = new UIButton(UIKeys.SCENE_REPLAYS_KITS_PREVIEW_REROLL, (b) -> this.rerollPreview());
        tip(this.rerollPreviewBtn, UIKeys.SCENE_REPLAYS_KITS_REROLL_TOOLTIP);

        this.previewSection.fields.add(previewGrid, this.rerollPreviewBtn);

        /* 2. Armor section */
        this.armorSection = new UISection(UIKeys.SCENE_REPLAYS_KITS_ARMOR)
            .remember(this.folds, "armor", true);

        List<UIToggle> armorToggles = new ArrayList<>();

        for (String material : KitRoller.DEFAULT_MATERIALS)
        {
            String displayName = Character.toUpperCase(material.charAt(0)) + material.substring(1);
            boolean active = this.config.armorPool.contains(material);

            UIToggle toggle = new UIToggle(IKey.raw(displayName), active, (t) ->
            {
                if (t.getValue())
                {
                    if (!this.config.armorPool.contains(material))
                    {
                        this.config.armorPool.add(material);
                    }
                }
                else
                {
                    if (this.config.armorPool.size() <= 1)
                    {
                        t.setValue(true);
                        return;
                    }

                    this.config.armorPool.remove(material);
                }

                this.rerollPreview();
            });
            tip(toggle, UIKeys.SCENE_REPLAYS_KITS_ARMOR_POOL_TOOLTIP);
            armorToggles.add(toggle);
        }

        UIElement armorRow1 = UI.row(armorToggles.get(0), armorToggles.get(1), armorToggles.get(2));
        UIElement armorRow2 = UI.row(armorToggles.get(3), armorToggles.get(4), armorToggles.get(5));
        this.armorSection.fields.add(armorRow1, armorRow2);

        /* 3. Trims section */
        this.trimSection = new UISection(UIKeys.SCENE_REPLAYS_KITS_TRIM)
            .remember(this.folds, "trim", true);

        UIToggle trimToggle = new UIToggle(UIKeys.SCENE_REPLAYS_KITS_TRIM_ENABLE, this.config.trimEnabled, (t) ->
        {
            this.config.trimEnabled = t.getValue();
            this.trimFields.setVisible(this.config.trimEnabled);
            this.scroll.resize();
            this.rerollPreview();
        });
        tip(trimToggle, UIKeys.SCENE_REPLAYS_KITS_TRIMS_ENABLE_TOOLTIP);

        this.trimFields = UI.column(4);

        UITrackpad trimChanceTrackpad = new UITrackpad((val) ->
        {
            this.config.trimChance = val / 100.0;
            this.rerollPreview();
        });
        trimChanceTrackpad.values(5.0, 0.0, 100.0).integer().setValue(this.config.trimChance * 100.0);
        tip(trimChanceTrackpad, UIKeys.SCENE_REPLAYS_KITS_TRIM_CHANCE_TOOLTIP);

        UIKitListButton patternBtn = new UIKitListButton(
            UIKeys.SCENE_REPLAYS_KITS_TRIM_PATTERN,
            this::getPatternEntries,
            () -> this.config.trimPattern,
            KitTrimIcons::pattern,
            (val) ->
            {
                this.config.trimPattern = val;
                this.rerollPreview();
            }
        );
        tip(patternBtn, UIKeys.SCENE_REPLAYS_KITS_TRIM_PATTERN_TOOLTIP);

        UIKitListButton materialBtn = new UIKitListButton(
            UIKeys.SCENE_REPLAYS_KITS_TRIM_MATERIAL,
            this::getMaterialEntries,
            () -> this.config.trimMaterial,
            KitTrimIcons::material,
            (val) ->
            {
                this.config.trimMaterial = val;
                this.rerollPreview();
            }
        );
        tip(materialBtn, UIKeys.SCENE_REPLAYS_KITS_TRIM_MATERIAL_TOOLTIP);

        UIElement trimChanceLabel = tip(UI.label(UIKeys.SCENE_REPLAYS_KITS_TRIM_CHANCE), UIKeys.SCENE_REPLAYS_KITS_TRIM_CHANCE_TOOLTIP);
        UIElement patternLabel = tip(UI.label(UIKeys.SCENE_REPLAYS_KITS_TRIM_PATTERN), UIKeys.SCENE_REPLAYS_KITS_TRIM_PATTERN_TOOLTIP);
        UIElement materialLabel = tip(UI.label(UIKeys.SCENE_REPLAYS_KITS_TRIM_MATERIAL), UIKeys.SCENE_REPLAYS_KITS_TRIM_MATERIAL_TOOLTIP);

        this.trimFields.add(
            trimChanceLabel,
            trimChanceTrackpad,
            UI.row(
                UI.column(patternLabel, patternBtn),
                UI.column(materialLabel, materialBtn)
            )
        );
        this.trimFields.setVisible(this.config.trimEnabled);

        this.trimSection.fields.add(trimToggle, this.trimFields);

        /* 4. Weapon section */
        this.weaponSection = new UISection(UIKeys.SCENE_REPLAYS_KITS_WEAPON)
            .remember(this.folds, "weapon", true);

        List<String[]> weaponDefs = List.of(
            new String[]{"minecraft:netherite_sword", "Netherite Sword"},
            new String[]{"minecraft:diamond_sword", "Diamond Sword"},
            new String[]{"minecraft:iron_sword", "Iron Sword"},
            new String[]{"minecraft:netherite_axe", "Axe"},
            new String[]{"minecraft:mace", "Mace"},
            new String[]{"minecraft:trident", "Trident"},
            new String[]{"minecraft:bow", "Bow"},
            new String[]{"minecraft:crossbow", "Crossbow"}
        );

        List<UIToggle> weaponToggles = new ArrayList<>();

        for (String[] def : weaponDefs)
        {
            String id = def[0];
            String name = def[1];
            boolean active = this.config.weaponPool.contains(id);

            UIToggle toggle = new UIToggle(IKey.raw(name), active, (t) ->
            {
                if (t.getValue())
                {
                    if (!this.config.weaponPool.contains(id))
                    {
                        this.config.weaponPool.add(id);
                    }
                }
                else
                {
                    this.config.weaponPool.remove(id);
                }

                this.rerollPreview();
            });
            tip(toggle, UIKeys.SCENE_REPLAYS_KITS_WEAPON_POOL_TOOLTIP);
            weaponToggles.add(toggle);
        }

        UIToggle noWeaponToggle = new UIToggle(UIKeys.SCENE_REPLAYS_KITS_NONE, this.config.weaponPool.isEmpty(), (t) ->
        {
            if (t.getValue())
            {
                this.config.weaponPool.clear();

                for (UIToggle wt : weaponToggles)
                {
                    wt.setValue(false);
                }
            }

            this.rerollPreview();
        });
        tip(noWeaponToggle, UIKeys.SCENE_REPLAYS_KITS_WEAPON_NONE_TOOLTIP);

        this.weaponSection.fields.add(
            UI.row(weaponToggles.get(0), weaponToggles.get(1), weaponToggles.get(2)),
            UI.row(weaponToggles.get(3), weaponToggles.get(4), weaponToggles.get(5)),
            UI.row(weaponToggles.get(6), weaponToggles.get(7), noWeaponToggle)
        );

        /* 5. Offhand section */
        this.offhandSection = new UISection(UIKeys.SCENE_REPLAYS_KITS_OFFHAND)
            .remember(this.folds, "offhand", true);

        List<String[]> offHandDefs = List.of(
            new String[]{"minecraft:shield", "Shield"},
            new String[]{"minecraft:totem_of_undying", "Totem"},
            new String[]{"minecraft:firework_rocket", "Firework"}
        );

        List<UIToggle> offHandToggles = new ArrayList<>();

        for (String[] def : offHandDefs)
        {
            String id = def[0];
            String name = def[1];
            boolean active = this.config.offHandPool.contains(id);

            UIToggle toggle = new UIToggle(IKey.raw(name), active, (t) ->
            {
                if (t.getValue())
                {
                    if (!this.config.offHandPool.contains(id))
                    {
                        this.config.offHandPool.add(id);
                    }
                }
                else
                {
                    this.config.offHandPool.remove(id);
                }

                this.rerollPreview();
            });

            if (id.equals("minecraft:firework_rocket"))
            {
                tip(toggle, UIKeys.SCENE_REPLAYS_KITS_OFFHAND_FIREWORK_TOOLTIP);
            }
            else
            {
                tip(toggle, UIKeys.SCENE_REPLAYS_KITS_OFFHAND_POOL_TOOLTIP);
            }

            offHandToggles.add(toggle);
        }

        UIToggle noOffHandToggle = new UIToggle(UIKeys.SCENE_REPLAYS_KITS_NONE, this.config.offHandPool.isEmpty(), (t) ->
        {
            if (t.getValue())
            {
                this.config.offHandPool.clear();

                for (UIToggle ot : offHandToggles)
                {
                    ot.setValue(false);
                }
            }

            this.rerollPreview();
        });
        tip(noOffHandToggle, UIKeys.SCENE_REPLAYS_KITS_OFFHAND_NONE_TOOLTIP);

        this.offhandSection.fields.add(
            UI.row(offHandToggles.get(0), offHandToggles.get(1), offHandToggles.get(2), noOffHandToggle)
        );

        /* 6. Enchants section */
        this.enchantsSection = new UISection(UIKeys.SCENE_REPLAYS_KITS_ENCHANTS)
            .remember(this.folds, "enchants", false);

        UIToggle enchantToggle = new UIToggle(UIKeys.SCENE_REPLAYS_KITS_ENCHANT_ENABLE, this.config.enchantEnabled, (t) ->
        {
            this.config.enchantEnabled = t.getValue();
            this.enchantFields.setVisible(this.config.enchantEnabled);
            this.scroll.resize();
            this.rerollPreview();
        });
        tip(enchantToggle, UIKeys.SCENE_REPLAYS_KITS_ENCHANTS_ENABLE_TOOLTIP);

        this.enchantFields = UI.column(4);

        UIToggle enchArmorToggle = new UIToggle(UIKeys.SCENE_REPLAYS_KITS_ENCHANT_ARMOR, this.config.enchantArmor, (t) ->
        {
            this.config.enchantArmor = t.getValue();
            this.rerollPreview();
        });
        tip(enchArmorToggle, UIKeys.SCENE_REPLAYS_KITS_ENCHANTS_ARMOR_TOOLTIP);

        UIToggle enchWeaponToggle = new UIToggle(UIKeys.SCENE_REPLAYS_KITS_ENCHANT_WEAPON, this.config.enchantWeapon, (t) ->
        {
            this.config.enchantWeapon = t.getValue();
            this.rerollPreview();
        });
        tip(enchWeaponToggle, UIKeys.SCENE_REPLAYS_KITS_ENCHANTS_WEAPON_TOOLTIP);

        List<Label<String>> levelEntries = List.of(
            new Label<>(UIKeys.SCENE_REPLAYS_KITS_ENCHANT_LEVEL_LOW, KitConfig.LevelMode.LOW.name()),
            new Label<>(UIKeys.SCENE_REPLAYS_KITS_ENCHANT_LEVEL_MID, KitConfig.LevelMode.MID.name()),
            new Label<>(UIKeys.SCENE_REPLAYS_KITS_ENCHANT_LEVEL_MAX, KitConfig.LevelMode.MAX.name()),
            new Label<>(UIKeys.SCENE_REPLAYS_KITS_ENCHANT_LEVEL_RANDOM, KitConfig.LevelMode.RANDOM.name())
        );

        UIKitListButton levelBtn = new UIKitListButton(
            UIKeys.SCENE_REPLAYS_KITS_ENCHANT_LEVEL,
            () -> levelEntries,
            () -> this.config.levelMode.name(),
            (val) ->
            {
                try
                {
                    this.config.levelMode = KitConfig.LevelMode.valueOf(val);
                }
                catch (Exception e)
                {
                    this.config.levelMode = KitConfig.LevelMode.MAX;
                }
                this.rerollPreview();
            }
        );
        tip(levelBtn, UIKeys.SCENE_REPLAYS_KITS_ENCHANTS_LEVEL_TOOLTIP);
        UIElement levelLabel = tip(UI.label(UIKeys.SCENE_REPLAYS_KITS_ENCHANT_LEVEL), UIKeys.SCENE_REPLAYS_KITS_ENCHANTS_LEVEL_TOOLTIP);

        UIToggle subsetToggle = new UIToggle(UIKeys.SCENE_REPLAYS_KITS_ENCHANT_SUBSET, this.config.enchantSubset, (t) ->
        {
            this.config.enchantSubset = t.getValue();
            this.subsetFields.setVisible(this.config.enchantSubset);
            this.scroll.resize();
            this.rerollPreview();
        });
        tip(subsetToggle, UIKeys.SCENE_REPLAYS_KITS_ENCHANTS_SUBSET_TOOLTIP);

        this.subsetFields = UI.column(4);
        UITrackpad subsetChanceTrackpad = new UITrackpad((val) ->
        {
            this.config.enchantSubsetChance = val / 100.0;
            this.rerollPreview();
        });
        subsetChanceTrackpad.values(5.0, 0.0, 100.0).integer().setValue(this.config.enchantSubsetChance * 100.0);
        tip(subsetChanceTrackpad, UIKeys.SCENE_REPLAYS_KITS_ENCHANTS_SUBSET_CHANCE_TOOLTIP);
        UIElement subsetChanceLabel = tip(UI.label(UIKeys.SCENE_REPLAYS_KITS_ENCHANT_SUBSET_CHANCE), UIKeys.SCENE_REPLAYS_KITS_ENCHANTS_SUBSET_CHANCE_TOOLTIP);

        this.subsetFields.add(subsetChanceLabel, subsetChanceTrackpad);
        this.subsetFields.setVisible(this.config.enchantSubset);

        this.enchantFields.add(
            UI.row(enchArmorToggle, enchWeaponToggle),
            UI.row(levelLabel, levelBtn),
            subsetToggle,
            this.subsetFields
        );
        this.enchantFields.setVisible(this.config.enchantEnabled);

        this.enchantsSection.fields.add(enchantToggle, this.enchantFields);

        /* 7. Variation section */
        this.variationSection = new UISection(UIKeys.SCENE_REPLAYS_KITS_VARIATION)
            .remember(this.folds, "variation", false);

        UIToggle varToggle = new UIToggle(UIKeys.SCENE_REPLAYS_KITS_VARIATION_ENABLE, this.config.variationEnabled, (t) ->
        {
            this.config.variationEnabled = t.getValue();
            this.variationFields.setVisible(this.config.variationEnabled);
            this.scroll.resize();
            this.rerollPreview();
        });
        tip(varToggle, UIKeys.SCENE_REPLAYS_KITS_VARIATION_ENABLE_TOOLTIP);

        this.variationFields = UI.column(4);

        UITrackpad varChanceTrackpad = new UITrackpad((val) ->
        {
            this.config.variationChance = val / 100.0;
            this.rerollPreview();
        });
        varChanceTrackpad.values(5.0, 0.0, 100.0).integer().setValue(this.config.variationChance * 100.0);
        tip(varChanceTrackpad, UIKeys.SCENE_REPLAYS_KITS_VARIATION_CHANCE_TOOLTIP);
        UIElement varChanceLabel = tip(UI.label(UIKeys.SCENE_REPLAYS_KITS_VARIATION_CHANCE), UIKeys.SCENE_REPLAYS_KITS_VARIATION_CHANCE_TOOLTIP);

        UITrackpad maxPiecesTrackpad = new UITrackpad((val) ->
        {
            this.config.variationMaxPieces = (int) Math.round(val);
            this.rerollPreview();
        });
        maxPiecesTrackpad.values(1.0, 0.0, 4.0).integer().setValue(this.config.variationMaxPieces);
        tip(maxPiecesTrackpad, UIKeys.SCENE_REPLAYS_KITS_VARIATION_MAX_TOOLTIP);
        UIElement maxPiecesLabel = tip(UI.label(UIKeys.SCENE_REPLAYS_KITS_VARIATION_MAX_PIECES), UIKeys.SCENE_REPLAYS_KITS_VARIATION_MAX_TOOLTIP);

        UIToggle changeMatToggle = new UIToggle(UIKeys.SCENE_REPLAYS_KITS_VARIATION_CHANGES_MATERIAL, this.config.variationChangesMaterial, (t) ->
        {
            this.config.variationChangesMaterial = t.getValue();
            this.rerollPreview();
        });
        tip(changeMatToggle, UIKeys.SCENE_REPLAYS_KITS_VARIATION_MATERIAL_TOOLTIP);

        UIToggle changeTrimToggle = new UIToggle(UIKeys.SCENE_REPLAYS_KITS_VARIATION_CHANGES_TRIM, this.config.variationChangesTrim, (t) ->
        {
            this.config.variationChangesTrim = t.getValue();
            this.rerollPreview();
        });
        tip(changeTrimToggle, UIKeys.SCENE_REPLAYS_KITS_VARIATION_TRIM_TOOLTIP);

        this.variationFields.add(
            UI.row(
                UI.column(varChanceLabel, varChanceTrackpad),
                UI.column(maxPiecesLabel, maxPiecesTrackpad)
            ),
            UI.row(changeMatToggle, changeTrimToggle)
        );
        this.variationFields.setVisible(this.config.variationEnabled);

        this.variationSection.fields.add(varToggle, this.variationFields);

        /* 8. Seed section */
        this.seedSection = new UISection(UIKeys.SCENE_REPLAYS_KITS_SEED)
            .remember(this.folds, "seed", false);

        UIToggle fixedSeedToggle = new UIToggle(UIKeys.SCENE_REPLAYS_KITS_SEED_FIXED, this.config.fixedSeed, (t) ->
        {
            this.config.fixedSeed = t.getValue();
            this.seedFields.setVisible(this.config.fixedSeed);
            this.scroll.resize();
        });
        tip(fixedSeedToggle, UIKeys.SCENE_REPLAYS_KITS_SEED_FIXED_TOOLTIP);

        this.seedFields = UI.column(4);
        UITrackpad seedTrackpad = new UITrackpad((val) -> this.config.seed = (long) Math.round(val));
        seedTrackpad.integer().setValue((double) this.config.seed);
        tip(seedTrackpad, UIKeys.SCENE_REPLAYS_KITS_SEED_VALUE_TOOLTIP);

        UIButton rerollSeedBtn = new UIButton(UIKeys.SCENE_REPLAYS_KITS_SEED_REROLL, (b) ->
        {
            this.config.seed = new Random().nextLong();
            seedTrackpad.setValue((double) this.config.seed);
        });
        tip(rerollSeedBtn, UIKeys.SCENE_REPLAYS_KITS_SEED_RANDOM_TOOLTIP);

        this.seedFields.add(UI.row(seedTrackpad, rerollSeedBtn));
        this.seedFields.setVisible(this.config.fixedSeed);

        this.seedSection.fields.add(fixedSeedToggle, this.seedFields);

        /* Add all sections */
        this.scroll.add(
            this.previewSection,
            this.armorSection,
            this.trimSection,
            this.weaponSection,
            this.offhandSection,
            this.enchantsSection,
            this.variationSection,
            this.seedSection
        );
    }

    private List<Label<String>> getPatternEntries()
    {
        List<Label<String>> entries = new ArrayList<>();
        entries.add(new Label<>(UIKeys.SCENE_REPLAYS_KITS_RANDOM, ""));

        RegistryWrapper.WrapperLookup lookup = GameRegistries.lookup();
        List<String> rawPatterns = KitBuilder.getAvailablePatterns(lookup);
        List<Label<String>> dynamic = new ArrayList<>();

        for (String id : rawPatterns)
        {
            dynamic.add(new Label<>(IKey.raw(formatDisplayName(id)), id));
        }

        dynamic.sort((a, b) -> NaturalOrderComparator.compare(true, a.title.get(), b.title.get()));
        entries.addAll(dynamic);

        return entries;
    }

    private List<Label<String>> getMaterialEntries()
    {
        List<Label<String>> entries = new ArrayList<>();
        entries.add(new Label<>(UIKeys.SCENE_REPLAYS_KITS_RANDOM, ""));

        RegistryWrapper.WrapperLookup lookup = GameRegistries.lookup();
        List<String> rawMaterials = KitBuilder.getAvailableTrimMaterials(lookup);
        List<Label<String>> dynamic = new ArrayList<>();

        for (String id : rawMaterials)
        {
            dynamic.add(new Label<>(IKey.raw(formatDisplayName(id)), id));
        }

        dynamic.sort((a, b) -> NaturalOrderComparator.compare(true, a.title.get(), b.title.get()));
        entries.addAll(dynamic);

        return entries;
    }

    private void updateModeVisibility()
    {
        boolean isRandom = this.config.mode == KitConfig.Mode.RANDOM;

        this.presetList.setVisible(!isRandom);
        this.rerollPreviewBtn.setVisible(isRandom);

        this.armorSection.setVisible(isRandom);
        this.trimSection.setVisible(isRandom);
        this.weaponSection.setVisible(isRandom);
        this.offhandSection.setVisible(isRandom);
        this.enchantsSection.setVisible(isRandom);
        this.variationSection.setVisible(isRandom);
        this.seedSection.setVisible(isRandom);

        this.scroll.resize();
    }

    private void setSampleKit(ResolvedKit kit)
    {
        if (kit == null)
        {
            return;
        }

        this.previewStacks[0] = kit.head();
        this.previewStacks[1] = kit.chest();
        this.previewStacks[2] = kit.legs();
        this.previewStacks[3] = kit.feet();
        this.previewStacks[4] = kit.mainHand();
        this.previewStacks[5] = kit.offHand();

        this.headSlot.setStack(this.previewStacks[0]);
        this.chestSlot.setStack(this.previewStacks[1]);
        this.legsSlot.setStack(this.previewStacks[2]);
        this.feetSlot.setStack(this.previewStacks[3]);
        this.mainHandSlot.setStack(this.previewStacks[4]);
        this.offHandSlot.setStack(this.previewStacks[5]);

        this.refreshSlotTooltip(this.headSlot, this.previewStacks[0]);
        this.refreshSlotTooltip(this.chestSlot, this.previewStacks[1]);
        this.refreshSlotTooltip(this.legsSlot, this.previewStacks[2]);
        this.refreshSlotTooltip(this.feetSlot, this.previewStacks[3]);
        this.refreshSlotTooltip(this.mainHandSlot, this.previewStacks[4]);
        this.refreshSlotTooltip(this.offHandSlot, this.previewStacks[5]);
    }

    private void rerollPreview()
    {
        if (this.config.mode == KitConfig.Mode.PRESET)
        {
            if (this.config.presetId != null && !this.config.presetId.isEmpty() && KitPresets.exists(this.config.presetId))
            {
                try
                {
                    this.setSampleKit(KitPresets.load(this.config.presetId));
                }
                catch (Exception e)
                {
                    RandomKitHelper.logError("Failed to preview preset: " + this.config.presetId, e);
                }
            }
            return;
        }

        RegistryWrapper.WrapperLookup lookup = GameRegistries.lookup();
        List<String> patterns = KitBuilder.getAvailablePatterns(lookup);
        List<String> materials = KitBuilder.getAvailableTrimMaterials(lookup);

        KitSpec spec = KitRoller.roll(this.config, new Random(), patterns, materials);
        ResolvedKit kit = KitBuilder.build(spec, this.config, new Random(), lookup);

        this.setSampleKit(kit);
    }

    private ResolvedKit getCurrentSampleKit()
    {
        return new ResolvedKit(
            this.previewStacks[0] != null ? this.previewStacks[0].copy() : ItemStack.EMPTY,
            this.previewStacks[1] != null ? this.previewStacks[1].copy() : ItemStack.EMPTY,
            this.previewStacks[2] != null ? this.previewStacks[2].copy() : ItemStack.EMPTY,
            this.previewStacks[3] != null ? this.previewStacks[3].copy() : ItemStack.EMPTY,
            this.previewStacks[4] != null ? this.previewStacks[4].copy() : ItemStack.EMPTY,
            this.previewStacks[5] != null ? this.previewStacks[5].copy() : ItemStack.EMPTY
        );
    }

    private void saveCurrentPreviewAsPreset()
    {
        UIContext context = this.getContext();

        UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(
            UIKeys.SCENE_REPLAYS_KITS_PRESET_NAME,
            UIKeys.SCENE_REPLAYS_KITS_PRESET_NAME_DESC,
            (name) ->
            {
                if (name == null || name.trim().isEmpty())
                {
                    return;
                }

                String cleanId = KitPresets.sanitizeId(name.trim());
                ResolvedKit kitToSave = this.getCurrentSampleKit();

                if (kitToSave == null && !this.replays.isEmpty())
                {
                    kitToSave = KitPresets.captureFromReplay(this.replays.get(0));
                }

                final ResolvedKit finalKit = kitToSave;

                if (KitPresets.exists(cleanId))
                {
                    UIConfirmOverlayPanel confirm = new UIConfirmOverlayPanel(
                        UIKeys.SCENE_REPLAYS_KITS_PRESET_OVERWRITE,
                        UIKeys.SCENE_REPLAYS_KITS_PRESET_OVERWRITE_DESC,
                        (confirmed) ->
                        {
                            if (confirmed)
                            {
                                this.executeSavePreset(cleanId, name.trim(), finalKit, context);
                            }
                        }
                    );

                    UIOverlay.addOverlay(context, confirm, 280, 140);
                }
                else
                {
                    this.executeSavePreset(cleanId, name.trim(), finalKit, context);
                }
            }
        );

        UIOverlay.addOverlay(context, prompt, 280, 140);
    }

    private void executeSavePreset(String id, String displayName, ResolvedKit kit, UIContext context)
    {
        try
        {
            KitPresets.save(id, displayName, kit);
            this.config.presetId = id;
            context.notifySuccess(UIKeys.SCENE_REPLAYS_KITS_PRESET_SAVED);
            this.presetList.clear();
            this.presetList.add(KitPresets.list());
            this.presetList.setCurrent(id);
            this.updateModeVisibility();
        }
        catch (Exception e)
        {
            RandomKitHelper.logError("Failed to save preset: " + id, e);
            context.notifyError(UIKeys.SCENE_REPLAYS_KITS_ERROR_PRESET_FAILED);
        }
    }

    @Override
    public void confirm()
    {
        if (this.callback != null)
        {
            this.callback.accept(this.config);
        }

        super.confirm();
    }
}
