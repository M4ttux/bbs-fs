package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeHeader;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;
import java.util.Objects;

/**
 * Organizes dope sheet / timeline tracks by inserting model headers when a replay contains secondary models,
 * preserving the exact order of sheets and pose tabs while clearly displaying model names.
 */
public class ReplayFormGroupBuilder
{
    public static void buildElements(Replay replay, List<UIKeyframeSheet> sheets, UIKeyframes view)
    {
        if (replay == null || sheets == null || view == null)
        {
            return;
        }

        Form rootForm = replay.form.get();
        boolean hasSecondaryForms = false;

        for (UIKeyframeSheet sheet : sheets)
        {
            Form form = UIReplaysEditor.getSheetForm(sheet);

            if (form != null && form != rootForm)
            {
                hasSecondaryForms = true;
                break;
            }
        }

        if (!hasSecondaryForms)
        {
            for (UIKeyframeSheet sheet : sheets)
            {
                view.addSheet(sheet);
            }

            return;
        }

        Form lastForm = null;

        for (UIKeyframeSheet sheet : sheets)
        {
            Form form = UIReplaysEditor.getSheetForm(sheet);

            if (form != null && !Objects.equals(form, lastForm))
            {
                String title = getFormTitle(form, form == rootForm ? "Model" : "Secondary Model");
                UIKeyframeHeader header = new UIKeyframeHeader(IKey.constant(title), Colors.WHITE)
                    .icon(Icons.POSE)
                    .form(form);

                view.addElement(header);
                lastForm = form;
            }

            view.addSheet(sheet);
        }
    }

    public static String getFormTitle(Form form, String defaultTitle)
    {
        if (form == null)
        {
            return defaultTitle;
        }

        String trackName = form.trackName.get();

        if (!trackName.isEmpty())
        {
            return trackName;
        }

        String customName = form.name.get();

        if (!customName.isEmpty())
        {
            return customName;
        }

        String displayName = form.getDisplayName();

        if (displayName != null && !displayName.isEmpty())
        {
            return displayName;
        }

        return defaultTitle;
    }
}
