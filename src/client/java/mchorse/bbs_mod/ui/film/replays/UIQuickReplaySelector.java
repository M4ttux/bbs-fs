package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating quick selector overlay to switch active replays in the current Film
 * from in-game using a keybind and mouse scroll.
 */
public class UIQuickReplaySelector extends UIBaseMenu
{
    private static final int ITEM_HEIGHT = 32;
    private static final int PANEL_WIDTH = 260;
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 20;
    private static final int MAX_VISIBLE_ITEMS = 7;

    private final Film film;
    private final KeyBinding selectorKey;
    private final List<Replay> replays = new ArrayList<>();
    private final Scroll scroll;

    private int selectedIndex = 0;
    private int initialReplayIndex = -1;

    private final Area panelArea = new Area();
    private final Area listArea = new Area();
    private final Area closeButtonArea = new Area();

    public UIQuickReplaySelector(Film film, KeyBinding selectorKey)
    {
        super();

        this.film = film;
        this.selectorKey = selectorKey;

        if (film != null)
        {
            this.replays.addAll(film.replays.getList());
        }

        this.scroll = new Scroll(this.listArea, ITEM_HEIGHT);

        UIFilmPanel filmPanel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);
        if (filmPanel != null && filmPanel.replayEditor != null)
        {
            Replay activeReplay = filmPanel.replayEditor.getReplay();
            if (activeReplay != null)
            {
                int index = this.replays.indexOf(activeReplay);
                if (index >= 0)
                {
                    this.initialReplayIndex = index;
                    this.selectedIndex = index;
                }
            }
        }

        this.updateScrollToSelected();
    }

    public void setIndex(int index)
    {
        if (this.replays.isEmpty())
        {
            return;
        }

        this.selectedIndex = MathUtils.clamp(index, 0, this.replays.size() - 1);
        this.updateScrollToSelected();
    }

    private void updateScrollToSelected()
    {
        if (this.selectedIndex >= 0 && this.selectedIndex < this.replays.size())
        {
            int targetY = this.selectedIndex * ITEM_HEIGHT;
            int viewHeight = this.listArea.h;

            if (targetY < this.scroll.getScroll())
            {
                this.scroll.setScroll(targetY);
            }
            else if (targetY + ITEM_HEIGHT > this.scroll.getScroll() + viewHeight)
            {
                this.scroll.setScroll(targetY + ITEM_HEIGHT - viewHeight);
            }
        }
    }

    public void applySelection()
    {
        if (this.selectedIndex >= 0 && this.selectedIndex < this.replays.size())
        {
            Replay selectedReplay = this.replays.get(this.selectedIndex);
            UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

            if (panel != null && panel.replayEditor != null)
            {
                panel.replayEditor.setReplay(selectedReplay);
            }
        }

        this.closeMenu();
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, double horizontal, double vertical)
    {
        if (vertical != 0)
        {
            if (vertical > 0)
            {
                this.setIndex(this.selectedIndex - 1);
            }
            else
            {
                this.setIndex(this.selectedIndex + 1);
            }

            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button)
    {
        if (button == 0)
        {
            if (this.closeButtonArea.isInside(mouseX, mouseY))
            {
                this.closeMenu();
                return true;
            }

            if (this.listArea.isInside(mouseX, mouseY))
            {
                int localY = (int) (mouseY - this.listArea.y + this.scroll.getScroll());
                int clickedIndex = localY / ITEM_HEIGHT;

                if (clickedIndex >= 0 && clickedIndex < this.replays.size())
                {
                    this.selectedIndex = clickedIndex;
                    this.applySelection();
                    return true;
                }
            }

            if (!this.panelArea.isInside(mouseX, mouseY))
            {
                this.closeMenu();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean handleKey(int key, int scanCode, int action, int mods)
    {
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)
        {
            if (this.selectorKey != null && this.selectorKey.matchesKey(new KeyInput(key, scanCode, 0)))
            {
                this.applySelection();
                return true;
            }

            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
            {
                this.applySelection();
                return true;
            }

            if (key == GLFW.GLFW_KEY_ESCAPE)
            {
                this.closeMenu();
                return true;
            }

            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W)
            {
                this.setIndex(this.selectedIndex - 1);
                return true;
            }

            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S)
            {
                this.setIndex(this.selectedIndex + 1);
                return true;
            }

            if (key == GLFW.GLFW_KEY_HOME)
            {
                this.setIndex(0);
                return true;
            }

            if (key == GLFW.GLFW_KEY_END)
            {
                this.setIndex(this.replays.size() - 1);
                return true;
            }

            if (key == GLFW.GLFW_KEY_PAGE_UP)
            {
                this.setIndex(this.selectedIndex - MAX_VISIBLE_ITEMS);
                return true;
            }

            if (key == GLFW.GLFW_KEY_PAGE_DOWN)
            {
                this.setIndex(this.selectedIndex + MAX_VISIBLE_ITEMS);
                return true;
            }
        }

        return super.handleKey(key, scanCode, action, mods);
    }

    private void updateDimensions()
    {
        int count = Math.max(1, this.replays.size());
        int visibleItems = Math.min(count, MAX_VISIBLE_ITEMS);
        int listHeight = visibleItems * ITEM_HEIGHT;
        int totalHeight = HEADER_HEIGHT + listHeight + FOOTER_HEIGHT;

        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - totalHeight) / 2;

        this.panelArea.set(px, py, PANEL_WIDTH, totalHeight);
        this.listArea.set(px, py + HEADER_HEIGHT, PANEL_WIDTH, listHeight);
        this.closeButtonArea.set(px + PANEL_WIDTH - HEADER_HEIGHT, py, HEADER_HEIGHT, HEADER_HEIGHT);

        this.scroll.clamp();
    }

    @Override
    public void renderMenu(UIRenderingContext renderingContext, int mouseX, int mouseY)
    {
        this.updateDimensions();

        UIContext context = this.context;
        Batcher2D batcher = context.batcher;
        FontRenderer font = batcher.getFont();

        /* Semi-transparent dark background over the game world */
        batcher.box(0, 0, this.width, this.height, Colors.setA(0, 0.65F));

        int px = this.panelArea.x;
        int py = this.panelArea.y;
        int pw = this.panelArea.w;
        int ph = this.panelArea.h;

        /* Floating panel background */
        batcher.box(px, py, px + pw, py + ph, BBSSettings.deepSurface());
        batcher.box(px, py, px + pw, py + HEADER_HEIGHT, BBSSettings.baseSurface());
        batcher.box(px, py + HEADER_HEIGHT - 1, px + pw, py + HEADER_HEIGHT, BBSSettings.dividerColor());

        /* Accent top line */
        batcher.box(px, py, px + pw, py + 2, BBSSettings.primaryColor(Colors.A100));

        /* Header title */
        String title = UIKeys.FILM_QUICK_REPLAY_TITLE.get();
        if (this.film != null)
        {
            title += " (" + this.replays.size() + ")";
        }
        batcher.textShadow(title, px + 8, py + (HEADER_HEIGHT - font.getHeight()) / 2 + 1, Colors.WHITE);

        /* Close button [X] */
        boolean closeHover = this.closeButtonArea.isInside(mouseX, mouseY);
        if (closeHover)
        {
            context.requestCursor(GLFW.GLFW_HAND_CURSOR);
            batcher.box(this.closeButtonArea.x, this.closeButtonArea.y, this.closeButtonArea.ex(), this.closeButtonArea.ey(), Colors.setA(Colors.RED, 0.4F));
        }
        int closeColor = closeHover ? Colors.WHITE : Colors.LIGHTER_GRAY;
        batcher.icon(Icons.CLOSE, closeColor, this.closeButtonArea.mx(), this.closeButtonArea.my(), 0.5F, 0.5F);

        /* Replays list */
        if (this.replays.isEmpty())
        {
            String empty = UIKeys.FILM_QUICK_REPLAY_EMPTY.get();
            batcher.textShadow(empty, px + (pw - font.getWidth(empty)) / 2, this.listArea.y + (this.listArea.h - font.getHeight()) / 2, Colors.GRAY);
        }
        else
        {
            this.scroll.setSize(this.replays.size() * ITEM_HEIGHT);

            batcher.clipBox(this.listArea.x, this.listArea.y, this.listArea.ex(), this.listArea.ey(), context);

            int startY = this.listArea.y - (int) this.scroll.getScroll();

            for (int i = 0; i < this.replays.size(); i++)
            {
                int itemY = startY + i * ITEM_HEIGHT;

                if (itemY + ITEM_HEIGHT < this.listArea.y || itemY > this.listArea.ey())
                {
                    continue;
                }

                Replay replay = this.replays.get(i);
                boolean isSelected = (i == this.selectedIndex);
                boolean isCurrentActive = (i == this.initialReplayIndex);
                boolean isHovered = this.listArea.isInside(mouseX, mouseY) && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;

                if (isHovered)
                {
                    context.requestCursor(GLFW.GLFW_HAND_CURSOR);
                }

                /* Row background */
                if (isSelected)
                {
                    batcher.gradientHBox(px, itemY, px + pw, itemY + ITEM_HEIGHT, BBSSettings.primaryColor(Colors.A50), BBSSettings.primaryColor(Colors.A12));
                    batcher.box(px, itemY, px + 3, itemY + ITEM_HEIGHT, BBSSettings.primaryColor(Colors.A100));
                }
                else if (isHovered)
                {
                    batcher.box(px, itemY, px + pw, itemY + ITEM_HEIGHT, Colors.setA(Colors.WHITE, 0.06F));
                }

                /* Divider line between items */
                if (i > 0)
                {
                    batcher.box(px + 4, itemY, px + pw - 4, itemY + 1, Colors.setA(Colors.WHITE, 0.05F));
                }

                /* 3D Model Preview */
                Form form = replay.form.get();
                int previewSize = 26;
                int previewX = px + 8;
                int previewY = itemY + (ITEM_HEIGHT - previewSize) / 2;

                if (form != null && BBSSettings.listModelPreview.get())
                {
                    batcher.clipBox(previewX, previewY, previewX + previewSize, previewY + previewSize, context);
                    FormUtilsClient.renderUI(form, context, previewX, previewY, previewX + previewSize, previewY + previewSize);
                    batcher.unclip(context);
                }
                else
                {
                    batcher.icon(Icons.POSE, Colors.LIGHTER_GRAY, previewX + (previewSize - 16) / 2, previewY + (previewSize - 16) / 2);
                }

                /* Replay label / name */
                int textX = previewX + previewSize + 6;
                int textY = itemY + (ITEM_HEIGHT - font.getHeight()) / 2;
                int maxTextWidth = pw - (textX - px) - (isCurrentActive ? 24 : 12);

                String name = (i + 1) + ". " + replay.getName();
                name = font.limitToWidth(name, Math.max(10, maxTextWidth));

                int textColor = isSelected ? Colors.WHITE : (isHovered ? Colors.LIGHTER_GRAY : Colors.setA(Colors.WHITE, 0.75F));
                batcher.textShadow(name, textX, textY, textColor);

                /* Active badge */
                if (isCurrentActive)
                {
                    int badgeW = font.getWidth("●") + 4;
                    batcher.textShadow("●", px + pw - badgeW - 6, textY, Colors.GREEN);
                }
            }

            batcher.unclip(context);
            this.scroll.renderScrollbar(batcher);
        }

        /* Footer instructions */
        int footerY = this.panelArea.ey() - FOOTER_HEIGHT;
        batcher.box(px, footerY, px + pw, footerY + 1, BBSSettings.dividerColor());
        batcher.box(px, footerY + 1, px + pw, this.panelArea.ey(), BBSSettings.baseSurface());

        String keyName = this.selectorKey != null ? this.selectorKey.getBoundKeyLocalizedText().getString() : "Key";
        String hint = UIKeys.FILM_QUICK_REPLAY_HINT.format(keyName).get();
        int hintColor = Colors.setA(Colors.WHITE, 0.5F);
        hint = font.limitToWidth(hint, pw - 12);
        batcher.textShadow(hint, px + (pw - font.getWidth(hint)) / 2, footerY + (FOOTER_HEIGHT - font.getHeight()) / 2 + 1, hintColor);

        super.renderMenu(renderingContext, mouseX, mouseY);
    }
}
