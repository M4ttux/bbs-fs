package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIVanillaParticleList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.tooltips.ITooltip;
import mchorse.bbs_mod.utils.Direction;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class UIParticleSettings extends UIElement
{
    public UIButton particle;
    public UITextbox arguments;

    private ParticleSettings settings;

    public UIParticleSettings()
    {
        this.particle = new UIButton(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_EDITOR_PICK, (b) ->
        {
            UIListOverlayPanel overlayPanel = new UIListOverlayPanel(UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_EDITOR_TITLE, (l) -> this.setParticle(Identifier.of(l)), UIVanillaParticleList::new);
            List<String> strings = new ArrayList<>();

            for (RegistryKey<ParticleType<?>> key : Registries.PARTICLE_TYPE.getKeys())
            {
                strings.add(key.getValue().toString());
            }

            overlayPanel.addValues(strings);
            overlayPanel.list.list.sort();
            overlayPanel.setValue(this.settings.particle.toString());

            UIOverlay.addOverlay(this.getContext(), overlayPanel);
        });

        this.arguments = new UITextbox(1000, this::setArguments);

        this.column().vertical().stretch();
        this.add(this.particle, this.arguments);
    }

    public void setSettings(ParticleSettings settings)
    {
        this.settings = settings;

        this.arguments.setText(settings.arguments);
        this.updateTooltip();
    }

    protected void setParticle(Identifier id)
    {
        this.settings.particle = id;
        this.updateTooltip();
    }

    protected void setArguments(String args)
    {
        this.settings.arguments = args;
    }

    public void updateTooltip()
    {
        if (this.settings == null || this.settings.particle == null)
        {
            this.arguments.tooltip((ITooltip) null);
            this.arguments.placeholder(IKey.EMPTY);
            return;
        }

        String path = this.settings.particle.getPath();
        IKey tooltipKey = getTooltipKey(path);
        IKey hintKey = getHintKey(path);

        this.arguments.tooltip(tooltipKey, 240, Direction.BOTTOM);
        this.arguments.placeholder(hintKey);
        this.particle.tooltip(IKey.raw(this.settings.particle.toString()));
    }

    public static boolean acceptsParameters(String name)
    {
        if (name == null || name.isEmpty())
        {
            return false;
        }

        try
        {
            ParticleType<?> type = Registries.PARTICLE_TYPE.get(Identifier.of(name));

            if (type != null)
            {
                return !(type instanceof net.minecraft.particle.SimpleParticleType);
            }
        }
        catch (Exception ignored)
        {}

        String path = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;

        return path.equals("block")
            || path.equals("block_marker")
            || path.equals("falling_dust")
            || path.equals("dust_pillar")
            || path.equals("block_crumble")
            || path.equals("item")
            || path.equals("dust")
            || path.equals("dust_color_transition")
            || path.equals("sculk_charge")
            || path.equals("shriek")
            || path.equals("entity_effect")
            || path.equals("tinted_leaves")
            || path.equals("flash")
            || path.equals("trail")
            || path.equals("vibration")
            || path.equals("dragon_breath")
            || path.equals("effect")
            || path.equals("instant_effect");
    }

    public static IKey getTooltipKey(String name)
    {
        if (name == null || name.isEmpty())
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_NONE;
        }

        String path = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;

        if (path.equals("block") || path.equals("block_marker") || path.equals("falling_dust")
            || path.equals("dust_pillar") || path.equals("block_crumble"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_BLOCK;
        }
        else if (path.equals("item"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_ITEM;
        }
        else if (path.equals("dust"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_DUST;
        }
        else if (path.equals("dust_color_transition"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_DUST_COLOR_TRANSITION;
        }
        else if (path.equals("sculk_charge"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_SCULK_CHARGE;
        }
        else if (path.equals("shriek"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_SHRIEK;
        }
        else if (path.equals("effect") || path.equals("instant_effect") || path.equals("entity_effect"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_EFFECT;
        }
        else if (path.equals("tinted_leaves") || path.equals("flash"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_COLOR;
        }
        else if (path.equals("trail"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_TRAIL;
        }
        else if (path.equals("vibration"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_VIBRATION;
        }

        return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_NONE;
    }

    public static IKey getHintKey(String name)
    {
        if (name == null || name.isEmpty())
        {
            return IKey.EMPTY;
        }

        String path = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;

        if (path.equals("block") || path.equals("block_marker") || path.equals("falling_dust")
            || path.equals("dust_pillar") || path.equals("block_crumble"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_BLOCK_HINT;
        }
        else if (path.equals("item"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_ITEM_HINT;
        }
        else if (path.equals("dust"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_DUST_HINT;
        }
        else if (path.equals("dust_color_transition"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_DUST_COLOR_TRANSITION_HINT;
        }
        else if (path.equals("sculk_charge"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_SCULK_CHARGE_HINT;
        }
        else if (path.equals("shriek"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_SHRIEK_HINT;
        }
        else if (path.equals("effect") || path.equals("instant_effect") || path.equals("entity_effect"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_EFFECT_HINT;
        }
        else if (path.equals("tinted_leaves") || path.equals("flash"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_COLOR_HINT;
        }
        else if (path.equals("trail"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_TRAIL_HINT;
        }
        else if (path.equals("vibration"))
        {
            return UIKeys.FORMS_EDITORS_VANILLA_PARTICLE_ARGS_VIBRATION_HINT;
        }

        return IKey.EMPTY;
    }
}