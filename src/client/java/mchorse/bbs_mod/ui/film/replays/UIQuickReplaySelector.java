package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class UIQuickReplaySelector extends UIBaseMenu
{
    private static final int ITEM_HEIGHT = 32;
    private static final int PANEL_WIDTH = 260;
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 20;
    private static final int MAX_VISIBLE_ITEMS = 7;

    private final Film film;
    private final KeyBinding selectorKey;
    private final List<Replay> replays;
    private final Scroll scroll;
    private final UIIcon close;
    private int selectedIndex = 0;
    private int initialReplayIndex = -1;

    public UIQuickReplaySelector(Film film, KeyBinding selectorKey)
    {
        super();

        this.film = film;
        this.selectorKey = selectorKey;
        this.replays = new ArrayList<>(film.replays.getList());

        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        UIFilmPanel panel = dashboard != null ? dashboard.getPanel(UIFilmPanel.class) : null;
        Replay current = panel != null && panel.replayEditor != null ? panel.replayEditor.getReplay() : null;

        this.initialReplayIndex = current != null ? this.replays.indexOf(current) : -1;
        this.selectedIndex = this.initialReplayIndex >= 0 ? this.initialReplayIndex : 0;

        this.scroll = new Scroll(new Area(), ITEM_HEIGHT);
        this.scroll.setSize(this.replays.size());

        this.close = new UIIcon(Icons.CLOSE, (b) -> this.closeMenu());
        this.close.tooltip(UIKeys.GENERAL_CLOSE, Direction.LEFT);
        this.main.add(this.close);
    }

    public void setIndex(int index)
    {
        if (this.replays.isEmpty())
        {
            return;
        }

        this.selectedIndex = MathUtils.clamp(index, 0, this.replays.size() - 1);
        this.scroll.scrollIntoView(this.selectedIndex * ITEM_HEIGHT);
    }

    public void applySelection()
    {
        if (this.selectedIndex >= 0 && this.selectedIndex < this.replays.size())
        {
            Replay selected = this.replays.get(this.selectedIndex);
            UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

            if (dashboard != null)
            {
                UIFilmPanel panel = dashboard.getPanel(UIFilmPanel.class);

                if (panel != null && panel.replayEditor != null)
                {
                    panel.replayEditor.setReplay(selected);
                }
            }
        }

        this.closeMenu();
    }

    @Override
    public boolean canPause()
    {
        return false;
    }

    @Override
    public boolean canHideHUD()
    {
        return false;
    }

    private int getVisibleItemsCount()
    {
        return Math.min(Math.max(this.replays.size(), 1), MAX_VISIBLE_ITEMS);
    }

    private int getListHeight()
    {
        return this.getVisibleItemsCount() * ITEM_HEIGHT;
    }

    private int getTotalHeight()
    {
        return HEADER_HEIGHT + this.getListHeight() + FOOTER_HEIGHT;
    }

    private int getPanelX()
    {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int getPanelY()
    {
        return (this.height - this.getTotalHeight()) / 2;
    }

    @Override
    public void resize(int width, int height)
    {
        this.width = width;
        this.height = height;

        int panelX = this.getPanelX();
        int panelY = this.getPanelY();
        int listY = panelY + HEADER_HEIGHT;
        int listHeight = this.getListHeight();

        this.close.relative(this.viewport).xy(panelX + PANEL_WIDTH - HEADER_HEIGHT, panelY).wh(HEADER_HEIGHT, HEADER_HEIGHT);

        super.resize(width, height);

        this.scroll.area.set(panelX, listY, PANEL_WIDTH, listHeight);
        this.scroll.setSize(this.replays.size());
        this.scroll.clamp();
        this.scroll.scrollIntoView(this.selectedIndex * ITEM_HEIGHT);
    }

    @Override
    public boolean mouseScrolled(int x, int y, double h, double v)
    {
        if (v != 0)
        {
            if (v > 0)
            {
                this.setIndex(this.selectedIndex - 1);
            }
            else
            {
                this.setIndex(this.selectedIndex + 1);
            }

            return true;
        }

        return super.mouseScrolled(x, y, h, v);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        int panelX = this.getPanelX();
        int panelY = this.getPanelY();
        int listHeight = this.getListHeight();
        int totalHeight = this.getTotalHeight();
        int listY = panelY + HEADER_HEIGHT;

        if (this.scroll.mouseClicked(mouseX, mouseY))
        {
            return true;
        }

        if (mouseButton == 0)
        {
            // Click inside list
            if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= listY && mouseY < listY + listHeight)
            {
                int index = (mouseY - listY + (int) this.scroll.getScroll()) / ITEM_HEIGHT;

                if (index >= 0 && index < this.replays.size())
                {
                    this.selectedIndex = index;
                    this.applySelection();

                    return true;
                }
            }

            // Click outside panel closes menu
            if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH || mouseY < panelY || mouseY > panelY + totalHeight)
            {
                this.closeMenu();

                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int mouseButton)
    {
        this.scroll.mouseReleased(mouseX, mouseY);

        return super.mouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean handleKey(int key, int scanCode, int action, int mods)
    {
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)
        {
            if (this.selectorKey != null && this.selectorKey.matchesKey(new KeyInput(key, scanCode, mods)))
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

    @Override
    public void renderMenu(UIRenderingContext context, int mouseX, int mouseY)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // Dark dim backdrop
        context.batcher.box(0, 0, this.width, this.height, Colors.A75);

        int panelX = this.getPanelX();
        int panelY = this.getPanelY();
        int listHeight = this.getListHeight();
        int totalHeight = this.getTotalHeight();
        int listY = panelY + HEADER_HEIGHT;

        // Panel background
        context.batcher.box(panelX, panelY, panelX + PANEL_WIDTH, panelY + totalHeight, BBSSettings.deepSurface());

        // Header
        context.batcher.box(panelX, panelY, panelX + PANEL_WIDTH, panelY + HEADER_HEIGHT, BBSSettings.baseSurface());
        // Top accent line
        context.batcher.box(panelX, panelY, panelX + PANEL_WIDTH, panelY + 2, BBSSettings.primaryColor(Colors.A100));
        // Header divider
        context.batcher.box(panelX, panelY + HEADER_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + HEADER_HEIGHT, BBSSettings.dividerColor());

        FontRenderer font = context.batcher.getFont();
        String title = UIKeys.FILM_QUICK_REPLAY_TITLE.get();
        context.batcher.textShadow(title, panelX + 8, panelY + (HEADER_HEIGHT - font.getHeight()) / 2 + 1, Colors.WHITE);

        // Footer
        int footerY = panelY + totalHeight - FOOTER_HEIGHT;
        context.batcher.box(panelX, footerY, panelX + PANEL_WIDTH, panelY + totalHeight, BBSSettings.baseSurface());
        context.batcher.box(panelX, footerY, panelX + PANEL_WIDTH, footerY + 1, BBSSettings.dividerColor());

        String keyName = this.selectorKey != null ? this.selectorKey.getBoundKeyLocalizedText().getString() : "Tab";
        String hint = UIKeys.FILM_QUICK_REPLAY_HINT.format(keyName).get();
        int hintW = font.getWidth(hint);
        context.batcher.textShadow(hint, panelX + (PANEL_WIDTH - hintW) / 2, footerY + (FOOTER_HEIGHT - font.getHeight()) / 2 + 1, Colors.GRAY);

        // List items
        this.scroll.area.set(panelX, listY, PANEL_WIDTH, listHeight);
        this.scroll.drag(this.context);
        this.scroll.clamp();

        if (this.replays.isEmpty())
        {
            String empty = UIKeys.FILM_QUICK_REPLAY_EMPTY.get();
            int emptyW = font.getWidth(empty);
            context.batcher.textShadow(empty, panelX + (PANEL_WIDTH - emptyW) / 2, listY + (listHeight - font.getHeight()) / 2, Colors.GRAY);
        }
        else
        {
            context.batcher.clip(panelX, listY, PANEL_WIDTH, listHeight, sw, sh);

            for (int i = 0; i < this.replays.size(); i++)
            {
                int itemY = listY + i * ITEM_HEIGHT - (int) this.scroll.getScroll();

                if (itemY + ITEM_HEIGHT < listY || itemY > listY + listHeight)
                {
                    continue;
                }

                Replay replay = this.replays.get(i);
                boolean selected = (i == this.selectedIndex);
                boolean hover = (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT);

                if (selected)
                {
                    context.batcher.gradientHBox(panelX, itemY, panelX + PANEL_WIDTH, itemY + ITEM_HEIGHT, BBSSettings.primaryColor(Colors.A50), BBSSettings.primaryColor(Colors.A12));
                    context.batcher.box(panelX, itemY, panelX + 3, itemY + ITEM_HEIGHT, BBSSettings.primaryColor(Colors.A100));
                }
                else if (hover)
                {
                    context.batcher.box(panelX, itemY, panelX + PANEL_WIDTH, itemY + ITEM_HEIGHT, Colors.A12);
                }

                // Thumbnail 3D / Form
                int previewSize = 24;
                int previewX = panelX + 8;
                int previewY = itemY + (ITEM_HEIGHT - previewSize) / 2;
                Form form = replay.form.get();

                if (form != null && BBSSettings.listModelPreview.get())
                {
                    context.batcher.clip(previewX, previewY, previewSize, previewSize, sw, sh);
                    FormUtilsClient.renderUI(form, this.context, previewX, previewY, previewX + previewSize, previewY + previewSize);
                    context.batcher.unclip(sw, sh);
                }
                else
                {
                    context.batcher.icon(Icons.POSE, Colors.LIGHTER_GRAY, previewX + 4, previewY + 4);
                }

                // Name
                String name = (i + 1) + ". " + replay.getName();
                int textX = previewX + previewSize + 6;
                int textY = itemY + (ITEM_HEIGHT - font.getHeight()) / 2 + 1;
                int maxTextWidth = PANEL_WIDTH - (textX - panelX) - 20;
                name = font.limitToWidth(name, maxTextWidth);
                context.batcher.textShadow(name, textX, textY, selected ? Colors.WHITE : Colors.LIGHTEST_GRAY);

                // Initial active replay badge (green dot)
                if (i == this.initialReplayIndex)
                {
                    int badgeX = panelX + PANEL_WIDTH - 16;
                    int badgeY = itemY + (ITEM_HEIGHT - 16) / 2;
                    context.batcher.icon(Icons.SPHERE, Colors.GREEN | Colors.A100, badgeX, badgeY);
                }
            }

            context.batcher.unclip(sw, sh);
            this.scroll.renderScrollbar(context.batcher);
        }

        super.renderMenu(context, mouseX, mouseY);
    }
}
