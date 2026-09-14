package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils.NumericValueGrab;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/** The value of an active numeric track; the selected key's parameters have their own area. */
public class UINumericTrackEditor extends UIElement
{
    private final UIKeyframeSheet sheet;
    private final UIKeyframes timeline;
    private final UITrackpad value;
    private final UIElement fields;
    private final NumericValueGrab grab;
    private BaseType beforeGesture;
    private KeyframeState beforeSelection;

    public static boolean supports(UIKeyframeSheet sheet)
    {
        return sheet != null && !sheet.header
            && (sheet.channel.getFactory() == KeyframeFactories.DOUBLE
                || sheet.channel.getFactory() == KeyframeFactories.FLOAT
                || sheet.channel.getFactory() == KeyframeFactories.INTEGER);
    }

    public UINumericTrackEditor(UIKeyframeSheet sheet, UIKeyframes timeline)
    {
        this.sheet = sheet;
        this.timeline = timeline;
        this.value = new UITrackpad(this::write)
        {
            @Override
            public boolean subMouseClicked(UIContext context)
            {
                boolean cancel = context.mouseButton == 1 && this.isDragging();
                boolean handled = super.subMouseClicked(context);

                if (cancel)
                {
                    UINumericTrackEditor.this.cancelGesture();
                }

                return handled;
            }

            @Override
            public boolean subMouseReleased(UIContext context)
            {
                boolean cancel = context.mouseButton == 1 && this.isDragging();
                boolean handled = super.subMouseReleased(context);

                if (cancel)
                {
                    UINumericTrackEditor.this.cancelGesture();
                }

                return handled;
            }
        };
        this.value.getEvents().register(UITrackpadDragStartEvent.class, (event) -> this.beginGesture());
        this.grab = new NumericValueGrab(this, this.value, this::write, this::beginGesture, this::cancelGesture);

        if (sheet.channel.getFactory() == KeyframeFactories.INTEGER)
        {
            this.value.integer();
        }

        this.fields = UI.column(UIConstants.MARGIN, UI.label(() -> sheet.title.get()), this.value);
        this.fields.relative(this).x(UIConstants.MARGIN).y(UIConstants.MARGIN).w(1F, -2 * UIConstants.MARGIN);
        this.add(this.fields);
        this.refresh();
    }

    public String getTrackId()
    {
        return this.sheet.id;
    }

    public void parameters(UIKeyframeFactory editor)
    {
        editor.relative(this.fields).x(-UIConstants.MARGIN).y(1F, UIConstants.MARGIN)
            .w(1F, 2 * UIConstants.MARGIN).hTo(this.area, 1F);
        this.add(editor);
    }

    private void beginGesture()
    {
        this.beforeGesture = this.sheet.channel.toData();
        this.beforeSelection = this.timeline.cacheState();
    }

    private void cancelGesture()
    {
        if (this.beforeGesture != null && !this.beforeGesture.equals(this.sheet.channel.toData()))
        {
            this.sheet.channel.preNotify();
            this.sheet.channel.fromData(this.beforeGesture);
            this.sheet.channel.postNotify();
            this.timeline.applyState(this.beforeSelection);
            this.timeline.triggerChange();
        }

        this.beforeGesture = null;
        this.beforeSelection = null;
    }

    private void write(double number)
    {
        Object typed;

        if (this.sheet.channel.getFactory() == KeyframeFactories.INTEGER)
        {
            typed = (int) number;
        }
        else if (this.sheet.channel.getFactory() == KeyframeFactories.FLOAT)
        {
            typed = (float) number;
        }
        else
        {
            typed = number;
        }

        this.sheet.setValueAt(this.timeline.getTick(), typed);
        this.timeline.triggerChange();
    }

    private void refresh()
    {
        if (!this.grab.isEditing() && !this.value.isDragging() && !this.value.textbox.isFocused())
        {
            this.value.setValue(((Number) this.sheet.valueAt(this.timeline.getTick())).doubleValue());
        }
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

    @Override
    public void render(UIContext context)
    {
        this.refresh();
        super.render(context);
        this.grab.render(context);
    }
}
