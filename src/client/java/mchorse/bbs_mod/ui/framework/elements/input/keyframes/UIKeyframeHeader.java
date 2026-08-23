package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icon;

/**
 * A non-keyframe section header/divider displaying the model name in the dope sheet timeline.
 */
public class UIKeyframeHeader extends UIKeyframeElement
{
    public Icon icon;
    public Form form;

    public UIKeyframeHeader(IKey title, int color)
    {
        super(title, color);
    }

    public UIKeyframeHeader icon(Icon icon)
    {
        this.icon = icon;
        return this;
    }

    public UIKeyframeHeader form(Form form)
    {
        this.form = form;
        return this;
    }
}
