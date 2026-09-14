package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils;

import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.function.DoubleConsumer;

/** The G gesture shared by numeric keyframe and track value panels. */
public class NumericValueGrab
{
    private final UIElement owner;
    private final UITrackpad value;
    private final DoubleConsumer write;
    private final Runnable begin;
    private final Runnable cancel;
    private int lastMouseX;
    private boolean editing;
    private double initialValue;

    public NumericValueGrab(UIElement owner, UITrackpad value, DoubleConsumer write, Runnable begin, Runnable cancel)
    {
        this.owner = owner;
        this.value = value;
        this.write = write;
        this.begin = begin;
        this.cancel = cancel;
        owner.keys().register(Keys.TRANSFORMATIONS_TRANSLATE, this::start).category(UIKeys.TRANSFORMS_KEYS_CATEGORY);
    }

    public boolean isEditing()
    {
        return this.editing;
    }

    private void start()
    {
        UIContext context = this.owner.getContext();

        if (context == null || this.editing)
        {
            return;
        }

        this.initialValue = this.value.getValue();
        this.lastMouseX = context.mouseX;
        this.editing = true;

        if (this.begin != null)
        {
            this.begin.run();
        }
    }

    private void stop(boolean accept)
    {
        this.editing = false;

        if (!accept)
        {
            this.value.setValue(this.initialValue);

            if (this.cancel == null)
            {
                this.write.accept(this.initialValue);
            }
            else
            {
                this.cancel.run();
            }
        }
    }

    public boolean mouseClicked(UIContext context)
    {
        if (this.editing && (context.mouseButton == 0 || context.mouseButton == 1))
        {
            this.stop(context.mouseButton == 0);

            return true;
        }

        return false;
    }

    public boolean keyPressed(UIContext context)
    {
        if (this.editing && (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_ESCAPE)))
        {
            this.stop(context.isPressed(GLFW.GLFW_KEY_ENTER));

            return true;
        }

        return false;
    }

    public void render(UIContext context)
    {
        if (!this.editing)
        {
            return;
        }

        int dx = context.mouseX - this.lastMouseX;

        if (dx != 0)
        {
            double number = MathUtils.clamp(this.value.getValue() + dx * this.value.getValueModifier(), this.value.min, this.value.max);

            if (this.value.integer)
            {
                number = (int) number;
            }

            this.value.setValue(number);
            this.write.accept(number);
            this.lastMouseX = context.mouseX;
        }

        String label = UIKeys.TRANSFORMS_EDITING.get();
        FontRenderer font = context.batcher.getFont();

        context.batcher.textCard(label, this.owner.area.mx(font.getWidth(label)), this.owner.area.my(font.getHeight()), Colors.WHITE, Colors.A50);
    }
}
