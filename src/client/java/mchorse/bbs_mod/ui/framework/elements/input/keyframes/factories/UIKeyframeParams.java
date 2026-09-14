package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils.UIBezierHandles;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/** Parameters of a selected key whose value is edited independently in the track panel. */
public class UIKeyframeParams extends UIKeyframeFactory<Object>
{
    private final UIBezierHandles handles;

    public UIKeyframeParams(Keyframe<Object> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);
        this.handles = new UIBezierHandles(keyframe);
        this.scroll.add(this.handles.createColumn());
    }

    @Override
    public void update()
    {
        super.update();
        this.handles.update();
    }
}
