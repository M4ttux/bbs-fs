package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils.UIBezierHandles;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils.NumericValueGrab;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/**
 * Base class for numeric keyframe factories (Double, Float, Integer).
 */
public abstract class UINumericKeyframeFactory <T extends Number> extends UIKeyframeFactory<T>
{
    protected UITrackpad value;
    protected UIBezierHandles handles;

    private final NumericValueGrab grab;

    public UINumericKeyframeFactory(Keyframe<T> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.value = new UITrackpad((v) -> this.setValue(v));
        this.value.setValue(this.getNumericValue(keyframe.getValue()));
        this.handles = new UIBezierHandles(keyframe);

        this.grab = new NumericValueGrab(this, this.value, this::setValue, null, null);
        this.scroll.add(this.value, this.handles.createColumn());
    }

    /**
     * Convert typed value to double for trackpad display.
     */
    protected abstract double getNumericValue(T value);

    /**
     * Convert double value back to typed value and update the given keyframe.
     */
    protected abstract void setKeyframeValue(Keyframe<T> keyframe, double value);

    /**
     * Override parent's setValue to handle numeric conversion. With auto-keyframing on the edit
     * lands on the keyframe at the playhead instead of the one this panel was opened for.
     */
    private void setValue(double value)
    {
        Keyframe<T> target = this.getEditTarget();

        this.setKeyframeValue(target, value);
        this.editor.getGraph().setValue(target.getValue(), true, true);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        return this.grab.mouseClicked(context) || super.subMouseClicked(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        return this.grab.keyPressed(context) || super.subKeyPressed(context);
    }

    /** Nothing is refreshed under the user's hands: not while typing, dragging or grabbing. */
    private boolean isBusy()
    {
        return this.grab.isEditing() || this.value.isDragging() || this.value.textbox.isFocused();
    }

    @Override
    public void render(UIContext context)
    {
        if (this.followsPlayhead() && !this.isBusy())
        {
            this.value.setValue(this.getNumericValue(this.getDisplayValue()));
        }

        super.render(context);

        this.grab.render(context);
    }

    @Override
    public void update()
    {
        super.update();

        if (!this.isBusy())
        {
            this.value.setValue(this.getNumericValue(this.getDisplayValue()));
        }

        this.handles.update();
    }
}
