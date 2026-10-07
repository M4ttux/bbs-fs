package mchorse.bbs_mod.cubic.data.animation;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangParser;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Animations implements IMapSerializable
{
    public MolangParser parser;
    public Map<String, Animation> animations = new HashMap<>();

    public Map<String, Animation> aliases = new HashMap<>();

    public Animations(MolangParser parser)
    {
        this.parser = parser;
    }

    public Collection<Animation> getAll()
    {
        return this.animations.values();
    }

    public void add(Animation animation)
    {
        this.animations.put(animation.id, animation);
    }

    public void addAlias(String alias, Animation animation)
    {
        this.aliases.put(alias, animation);
    }

    public Animation get(String id)
    {
        if (id == null)
        {
            return null;
        }

        Animation anim = this.animations.get(id);

        if (anim != null)
        {
            return anim;
        }

        anim = this.aliases.get(id);

        if (anim != null)
        {
            return anim;
        }

        if (id.startsWith("animation."))
        {
            String stripped = id.substring("animation.".length());

            anim = this.animations.get(stripped);

            if (anim != null)
            {
                return anim;
            }

            anim = this.aliases.get(stripped);

            if (anim != null)
            {
                return anim;
            }
        }
        else
        {
            String prefixed = "animation." + id;

            anim = this.animations.get(prefixed);

            if (anim != null)
            {
                return anim;
            }

            anim = this.aliases.get(prefixed);

            if (anim != null)
            {
                return anim;
            }
        }

        return null;
    }

    @Override
    public void fromData(MapType data)
    {
        for (String key : data.keys())
        {
            Animation animation = new Animation(key, this.parser);

            animation.fromData(data.getMap(key));

            this.add(animation);
        }
    }

    @Override
    public void toData(MapType data)
    {
        for (Map.Entry<String, Animation> entry : this.animations.entrySet())
        {
            data.put(entry.getKey(), entry.getValue().toData());
        }
    }
}