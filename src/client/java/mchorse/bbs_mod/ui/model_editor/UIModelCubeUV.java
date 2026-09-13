package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.data.model.CubeFace;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelUV;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcons;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import org.joml.Vector2f;

import java.util.function.Consumer;

/**
 * The unwrap pane of the model editor: the texture with the picked cube's sides drawn over it, and
 * under the picture everything those sides are made of — which one is being worked on, whether it
 * is drawn at all, the two corners it covers, its mirrors and its quarter turn, the box unwrap that
 * lays all six out at once, and the size of the sheet they are all measured against.
 *
 * <p>It is one pane rather than a row here and a row there: the picture is the subject and the rows
 * under it say the same thing in numbers, so a side can be dragged into place or typed into place
 * and the other half follows either way.</p>
 *
 * <p>A side is shown as its two CORNERS rather than as a corner and a size, because a mirrored side
 * is exactly what the format calls a NEGATIVE size: carrying the corners through an edit keeps the
 * mirror, where carrying a width would quietly straighten it out. A side with no unwrap at all
 * isn't drawn — that is what the eye in the side's icons takes away and gives back.</p>
 *
 * <p>The sheet's size is the model's, not the cube's — the {@code texture} of the file rather than
 * the size of the PNG — so changing it re-reads every cube's unwrap against the new one.</p>
 */
public class UIModelCubeUV extends UIElement
{
    /** Which side the rows are on. Kept across picks and models: it is a mode of working. */
    private static CubeFace picked = CubeFace.FRONT;

    /** How tall the strip of sides over the picture stands: a row's height, like every strip of icons. */
    private static final int FACES_HEIGHT = UIConstants.CONTROL_HEIGHT;

    /**
     * The side's own icons stand at the size of an icon button rather than squeezed to a row's height
     * like a list's verbs: they are what the pane is worked with, and a glyph drawn at its own size
     * reads cramped in a box no bigger than itself.
     */
    private static final int ACTION_SIZE = 20;

    private final UIModelGeometryEditor editor;

    private final UIModelUVEditor canvas;
    private final UIScrollView rows;

    private final UIIcons faces;
    private final UITrackpad[] corners = new UITrackpad[4];
    private final UIElement cornerRows;

    /** What can be done to a drawn side — mirrored or turned; they go dead on a side that isn't drawn. */
    private final UIIcon[] sideActions;
    private final UITrackpad boxU;
    private final UITrackpad boxV;
    private final UITrackpad sheetWidth;
    private final UITrackpad sheetHeight;

    /** Whether the box unwrap lays the sides out mirrored — a switch on the box row, not a live edit. */
    private boolean boxMirror;

    public UIModelCubeUV(UIModelGeometryEditor editor)
    {
        this.editor = editor;
        this.canvas = new UIModelUVEditor(this);
        this.canvas.relative(this).x(0).w(1F);

        /* Six sides as six arrows, the way a weld names one: they all fit, and the lit one says
         * which side is being worked on without a dropdown to open. It heads the pane, over the
         * picture as a tab bar would, since the picture and the rows under it both follow it. */
        this.faces = new UIIcons((b) -> this.pickFace(ModelFaces.ALL.get(b.getValue())));

        for (CubeFace face : ModelFaces.ALL)
        {
            this.faces.add(ModelFaces.icon(face), ModelFaces.label(face));
        }

        this.faces.stretch();
        this.faces.relative(this).x(0).y(0).w(1F).h(FACES_HEIGHT);
        this.faces.setValue(picked.ordinal());

        IKey[] labels = {
            UIKeys.MODEL_EDITOR_MODEL_UV_X1, UIKeys.MODEL_EDITOR_MODEL_UV_Y1,
            UIKeys.MODEL_EDITOR_MODEL_UV_X2, UIKeys.MODEL_EDITOR_MODEL_UV_Y2
        };

        for (int i = 0; i < this.corners.length; i++)
        {
            int index = i;
            UITrackpad pad = new UITrackpad((v) -> this.setCorner(index, v.floatValue()));

            pad.tooltip(labels[i]);
            pad.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.editor.closeCubeEdit());
            this.corners[i] = pad;
        }

        /* Whether the side is drawn leads the side's own icons: the eye the rest of the editor shows
         * visibility with, open or shut as the side is. It stays live on a side that isn't drawn — it
         * is how that side comes back. */
        UIIcon drawn = new UIIcon(() -> this.uv() != null ? Icons.VISIBLE : Icons.INVISIBLE, (b) -> this.setDrawn(this.uv() == null));
        UIIcon flipX = new UIIcon(Icons.HORIZONTAL, (b) -> this.change(UIKeys.MODEL_EDITOR_MODEL_UNDO_UV_FLIP, ModelUV::flipX));
        UIIcon flipY = new UIIcon(Icons.VERTICAL, (b) -> this.change(UIKeys.MODEL_EDITOR_MODEL_UNDO_UV_FLIP, ModelUV::flipY));
        UIIcon rotate = new UIIcon(Icons.REFRESH, (b) -> this.change(UIKeys.MODEL_EDITOR_MODEL_UNDO_UV_ROTATE, ModelUV::rotate90));

        drawn.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_DRAWN);
        flipX.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_FLIP_X);
        flipY.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_FLIP_Y);
        rotate.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_ROTATE);

        this.cornerRows = UI.row(this.corners[0], this.corners[1], this.corners[2], this.corners[3]);
        this.sideActions = new UIIcon[]{flipX, flipY, rotate};

        /* The box unwrap: its name on a line of its own, and under it one row says the rest — from
         * where, mirrored or not, go. Going is a press rather than a live field: it throws all six
         * sides away, which is not something a stray scroll over a pad should do. */
        this.boxU = new UITrackpad((v) -> {}).integer();
        this.boxV = new UITrackpad((v) -> {}).integer();
        this.boxU.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_BOX_U);
        this.boxV.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_BOX_V);

        UIIcon mirror = new UIIcon(Icons.EXCHANGE, (b) -> this.boxMirror = !this.boxMirror);
        UIIcon apply = new UIIcon(Icons.CHECKMARK, (b) -> this.applyBoxUV());

        mirror.highlight(() -> this.boxMirror, Direction.BOTTOM);
        mirror.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_BOX_MIRROR);
        apply.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_BOX_TIP);

        UIElement box = UI.row(
            this.boxU,
            this.boxV,
            mirror.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT),
            apply.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT)
        );

        this.sheetWidth = new UITrackpad((v) -> this.setSheet()).integer().limit(1);
        this.sheetHeight = new UITrackpad((v) -> this.setSheet()).integer().limit(1);
        this.sheetWidth.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_SHEET_WIDTH);
        this.sheetHeight.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_SHEET_HEIGHT);
        this.sheetWidth.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.editor.closeCubeEdit());
        this.sheetHeight.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.editor.closeCubeEdit());

        UIElement actions = new UIElement();

        actions.row(0).height(ACTION_SIZE);
        actions.add(drawn.wh(ACTION_SIZE, ACTION_SIZE), flipX.wh(ACTION_SIZE, ACTION_SIZE), flipY.wh(ACTION_SIZE, ACTION_SIZE), rotate.wh(ACTION_SIZE, ACTION_SIZE));

        this.rows = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING,
            this.cornerRows,
            actions,
            UI.label(UIKeys.MODEL_EDITOR_MODEL_UV_BOX),
            box,
            UI.label(UIKeys.MODEL_EDITOR_MODEL_UV_SHEET),
            UI.row(this.sheetWidth, this.sheetHeight)
        );
        this.rows.relative(this).x(0).w(1F);

        this.add(this.faces, this.canvas, this.rows);
    }

    /**
     * The sides head the pane and the picture sits under them. The picture is square — a sheet
     * reads as the sheet it is — and takes up to half of what is left, so the rows under it are
     * never squeezed out on a short window.
     */
    @Override
    protected void afterResizeApplied()
    {
        super.afterResizeApplied();

        int side = Math.max(0, Math.min(this.area.w, (this.area.h - FACES_HEIGHT) / 2));

        this.canvas.y(FACES_HEIGHT).h(side);
        this.rows.y(FACES_HEIGHT + side).h(1F, -FACES_HEIGHT - side);
    }

    /* Filling */

    /** The rows take the picked cube, and the sheet row the model. */
    public void fill()
    {
        Model model = this.editor.pickedModel();

        this.faces.setValue(picked.ordinal());
        this.sheetWidth.setValue(model == null ? 0D : model.textureWidth);
        this.sheetHeight.setValue(model == null ? 0D : model.textureHeight);

        UIUtils.setEnabledDeep(this.rows, this.editor.pickedCube() != null);
        this.faces.setEnabled(this.editor.pickedCube() != null);

        this.fillFace();
    }

    /** The side's own rows; a side that isn't drawn has no corners to show, so they go dead. */
    private void fillFace()
    {
        ModelUV uv = this.uv();

        for (int i = 0; i < this.corners.length; i++)
        {
            this.corners[i].setValue(uv == null ? 0D : this.corner(uv, i));
        }

        UIUtils.setEnabledDeep(this.cornerRows, uv != null);

        for (UIIcon action : this.sideActions)
        {
            action.setEnabled(uv != null);
        }
    }

    /** Whether one of the pads is being dragged — the model settles when it is let go. */
    public boolean dragging()
    {
        for (UITrackpad pad : new UITrackpad[]{this.corners[0], this.corners[1], this.corners[2], this.corners[3], this.sheetWidth, this.sheetHeight})
        {
            if (pad.isDragging())
            {
                return true;
            }
        }

        return this.canvas.dragging;
    }

    /** The picture follows the pick every frame: a sheet resized elsewhere has to reach it too. */
    @Override
    public void render(UIContext context)
    {
        this.canvas.fill(this.editor.pickedInstance(), this.editor.pickedModel(), this.editor.pickedCube());

        super.render(context);
    }

    /* What the picture asks of the model */

    /** The side the rows and the picture are on. */
    CubeFace face()
    {
        return picked;
    }

    /** A side clicked on the picture, or picked from the arrows. */
    void pickFace(CubeFace face)
    {
        picked = face;

        this.faces.setValue(face.ordinal());
        this.fillFace();
    }

    /** A drag on the picture: the side's four numbers at once, merging into one undo step. */
    void dragFace(float x1, float y1, float x2, float y2)
    {
        ModelUV uv = this.uv();

        if (uv == null || (uv.sx() == x1 && uv.sy() == y1 && uv.ex() == x2 && uv.ey() == y2))
        {
            return;
        }

        this.editor.editCube(this.faceLabel(UIKeys.MODEL_EDITOR_MODEL_UNDO_UV), "uv:" + picked.name(), () -> uv.from(x1, y1, x2, y2));
        this.fillFace();
    }

    /** The drag is over: its undo step closes and the model settles. */
    void endFaceDrag()
    {
        this.editor.closeCubeEdit();
    }

    /* Editing from the rows */

    /** The unwrap of the side the rows are on, or null with no cube picked or the side not drawn. */
    private ModelUV uv()
    {
        ModelCube cube = this.editor.pickedCube();

        return cube == null ? null : cube.getUV(picked);
    }

    private double corner(ModelUV uv, int index)
    {
        return switch (index)
        {
            case 0 -> uv.sx();
            case 1 -> uv.sy();
            case 2 -> uv.ex();
            default -> uv.ey();
        };
    }

    /** One corner typed or dragged; the other three are written back as they stand, mirror and all. */
    private void setCorner(int index, float value)
    {
        ModelUV uv = this.uv();

        if (uv == null || this.corner(uv, index) == value)
        {
            return;
        }

        float[] c = {uv.sx(), uv.sy(), uv.ex(), uv.ey()};

        c[index] = value;

        this.editor.editCube(this.faceLabel(UIKeys.MODEL_EDITOR_MODEL_UNDO_UV), "uv:" + picked.name(), () -> uv.from(c[0], c[1], c[2], c[3]));
    }

    /** Whether the side is drawn at all: off takes its unwrap away, on gives it the cube's own size. */
    private void setDrawn(boolean on)
    {
        ModelCube cube = this.editor.pickedCube();

        if (cube == null || (cube.getUV(picked) != null) == on)
        {
            return;
        }

        this.editor.editCube(this.faceLabel(UIKeys.MODEL_EDITOR_MODEL_UNDO_UV_DRAWN), null, () ->
            cube.setUV(picked, on ? ModelUV.fromXY(0F, 0F, cube.size.x, cube.size.y) : null));
        this.fillFace();
        this.editor.closeCubeEdit();
    }

    /** A mirror or a quarter turn of the side, each its own undo step. */
    private void change(IKey label, Consumer<ModelUV> change)
    {
        ModelUV uv = this.uv();

        if (uv == null)
        {
            return;
        }

        this.editor.editCube(this.faceLabel(label), null, () -> change.accept(uv));
        this.fillFace();
        this.editor.closeCubeEdit();
    }

    /** All six sides laid out as a box from one corner of the sheet — what a new cube is given. */
    private void applyBoxUV()
    {
        ModelCube cube = this.editor.pickedCube();

        if (cube == null)
        {
            return;
        }

        Vector2f at = new Vector2f((float) this.boxU.getValue(), (float) this.boxV.getValue());
        boolean mirror = this.boxMirror;

        this.editor.editCube(UIKeys.MODEL_EDITOR_MODEL_UNDO_UV_BOX, null, () -> cube.setupBoxUV(at, mirror));
        this.fillFace();
        this.editor.closeCubeEdit();
    }

    private void setSheet()
    {
        this.editor.setTextureSize((int) this.sheetWidth.getValue(), (int) this.sheetHeight.getValue());
    }

    private IKey faceLabel(IKey label)
    {
        return label.format(ModelFaces.label(picked));
    }
}
