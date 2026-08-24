package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix3x2fStack;

/**
 * In-game HUD overlay that displays the currently selected Replay
 * with a 3D model preview and smaller replay name in the top-left corner.
 */
public class SelectedReplayHudRenderer
{
    public static boolean visible = true;

    public static void toggle()
    {
        visible = !visible;
    }

    public static void render(Batcher2D batcher, float tickDelta)
    {
        if (!visible)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.options.hudHidden || UIScreen.getCurrentMenu() != null)
        {
            return;
        }

        try
        {
            UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

            if (panel == null || panel.getData() == null || panel.replayEditor == null)
            {
                return;
            }

            Film film = panel.getData();
            Replay replay = panel.replayEditor.getReplay();

            if (replay == null)
            {
                if (!film.replays.getList().isEmpty())
                {
                    replay = film.replays.getList().get(0);
                }
                else
                {
                    return;
                }
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

            /* Offset down if recording overlay is active in the top-left */
            if (BBSModClient.getFilms().getRecorder() != null && BBSSettings.recordingOverlays.get())
            {
                y = 26;
            }

            /* Clean card background without red lines */
            batcher.box(x, y, x + cardWidth, y + cardHeight, Colors.setA(0, 0.75F));
            batcher.box(x, y + cardHeight - 1, x + cardWidth, y + cardHeight, BBSSettings.dividerColor());

            /* 3D Model thumbnail */
            Form form = replay.form.get();
            int previewX = x + padding;
            int previewY = y + (cardHeight - previewSize) / 2;

            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();

            UIContext context = BBSModClient.getDashboard().context;
            context.batcher = batcher;

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

            /* Smaller scaled replay name text */
            int textX = previewX + previewSize + padding;
            float textY = y + (cardHeight - font.getHeight() * textScale) / 2F;

            Matrix3x2fStack matrices = batcher.getContext().getMatrices();
            matrices.pushMatrix();
            matrices.translate(textX, textY);
            matrices.scale(textScale, textScale);
            batcher.textShadow(displayName, 0, 0, Colors.WHITE);
            matrices.popMatrix();
        }
        catch (Exception e)
        {
            /* Defensive: prevent any HUD render exceptions from crashing the game */
        }
    }
}
