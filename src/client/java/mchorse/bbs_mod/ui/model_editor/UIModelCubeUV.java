package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.data.model.CubeFace;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelUV;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIChoiceButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.joml.Vector2f;

import java.util.function.Consumer;

/**
 * The unwrap of the picked cube, in numbers: which of its six sides is being worked on, whether
 * that side is drawn at all, the two corners it covers on the texture, the two mirrors and the
 * quarter turn, the box unwrap that lays all six sides out at once, and the size of the sheet all
 * of it is measured against.
 *
 * <p>A side is shown as its two CORNERS rather than as a corner and a size, because a mirrored side
 * is exactly what the format calls a NEGATIVE size: typing corners carries the mirror through an
 * edit, where typing a width would quietly straighten it out. A side with no unwrap at all isn't
 * drawn — that is what the toggle turns off and on.</p>
 *
 * <p>The sheet's size is the model's, not the cube's — the {@code texture} of the file rather than
 * the size of the PNG — so changing it re-reads every cube's unwrap against the new one. It sits
 * here because this is where the numbers it governs are.</p>
 */
public class UIModelCubeUV extends UIElement
{
    /** Which side the rows are on. Kept across picks and models: it is a mode of working. */
    private static CubeFace picked = CubeFace.FRONT;

    private final UIModelGeometryEditor editor;

    private final UIChoiceButton<CubeFace> face;
    private final UIToggle drawn;
    private final UITrackpad[] corners = new UITrackpad[4];
    private final UIElement cornerRows;
    private final UIElement flips;
    private final UITrackpad boxU;
    private final UITrackpad boxV;
    private final UIToggle boxMirror;
    private final UITrackpad sheetWidth;
    private final UITrackpad sheetHeight;

    public UIModelCubeUV(UIModelGeometryEditor editor)
    {
        this.editor = editor;

        /* Six sides don't fit as tabs in a pane that can be 160 wide, so they go in a dropdown. */
        this.face = new UIChoiceButton<>(ModelFaces.ALL, ModelFaces::icon, ModelFaces::label);
        this.face.callback(this::pickFace).setValue(picked);
        this.face.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_FACE);

        this.drawn = new UIToggle(UIKeys.MODEL_EDITOR_MODEL_UV_DRAWN, (t) -> this.setDrawn(t.getValue()));

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

        UIIcon flipX = new UIIcon(Icons.HORIZONTAL, (b) -> this.change(UIKeys.MODEL_EDITOR_MODEL_UNDO_UV_FLIP, ModelUV::flipX));
        UIIcon flipY = new UIIcon(Icons.VERTICAL, (b) -> this.change(UIKeys.MODEL_EDITOR_MODEL_UNDO_UV_FLIP, ModelUV::flipY));
        UIIcon rotate = new UIIcon(Icons.REFRESH, (b) -> this.change(UIKeys.MODEL_EDITOR_MODEL_UNDO_UV_ROTATE, ModelUV::rotate90));

        flipX.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_FLIP_X);
        flipY.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_FLIP_Y);
        rotate.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_ROTATE);

        this.cornerRows = UI.column(UI.row(this.corners[0], this.corners[1]), UI.row(this.corners[2], this.corners[3]));
        this.flips = UI.strip(flipX, flipY, rotate);

        /* The box unwrap is a button rather than a live field: it throws away all six sides, which
         * is not something a stray scroll over a pad should do. */
        this.boxU = new UITrackpad((v) -> {}).integer();
        this.boxV = new UITrackpad((v) -> {}).integer();
        this.boxU.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_BOX_U);
        this.boxV.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_BOX_V);
        this.boxMirror = new UIToggle(UIKeys.MODEL_EDITOR_MODEL_UV_BOX_MIRROR, (t) -> {});

        UIButton apply = new UIButton(UIKeys.MODEL_EDITOR_MODEL_UV_BOX_APPLY, (b) -> this.applyBoxUV());

        apply.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_BOX_TIP);

        this.sheetWidth = new UITrackpad((v) -> this.setSheet()).integer().limit(1);
        this.sheetHeight = new UITrackpad((v) -> this.setSheet()).integer().limit(1);
        this.sheetWidth.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_SHEET_WIDTH);
        this.sheetHeight.tooltip(UIKeys.MODEL_EDITOR_MODEL_UV_SHEET_HEIGHT);
        this.sheetWidth.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.editor.closeCubeEdit());
        this.sheetHeight.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.editor.closeCubeEdit());

        this.column(UIConstants.MARGIN).vertical().stretch();
        this.add(
            UI.label(UIKeys.MODEL_EDITOR_MODEL_UV_TITLE),
            this.face,
            this.drawn,
            this.cornerRows,
            this.flips,
            UI.label(UIKeys.MODEL_EDITOR_MODEL_UV_BOX),
            UI.row(this.boxU, this.boxV),
            this.boxMirror,
            apply,
            UI.label(UIKeys.MODEL_EDITOR_MODEL_UV_SHEET),
            UI.row(this.sheetWidth, this.sheetHeight)
        );
    }

    /* Filling */

    /** The rows take the picked cube, and the sheet row the model. */
    public void fill()
    {
        Model model = this.editor.pickedModel();

        UIUtils.setEnabledDeep(this, this.editor.pickedCube() != null);

        this.face.setValue(picked);
        this.sheetWidth.setValue(model == null ? 0D : model.textureWidth);
        this.sheetHeight.setValue(model == null ? 0D : model.textureHeight);

        this.fillFace();
    }

    /** The side's own rows; a side that isn't drawn has no corners to show, so they go dead. */
    private void fillFace()
    {
        ModelUV uv = this.uv();

        this.drawn.setValue(uv != null);

        for (int i = 0; i < this.corners.length; i++)
        {
            this.corners[i].setValue(uv == null ? 0D : this.corner(uv, i));
        }

        UIUtils.setEnabledDeep(this.cornerRows, uv != null);
        UIUtils.setEnabledDeep(this.flips, uv != null);
    }

    /** Whether one of the unwrap's pads is being dragged — the model settles when it is let go. */
    public boolean dragging()
    {
        for (UITrackpad pad : new UITrackpad[]{this.corners[0], this.corners[1], this.corners[2], this.corners[3], this.sheetWidth, this.sheetHeight})
        {
            if (pad.isDragging())
            {
                return true;
            }
        }

        return false;
    }

    /* Editing */

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

    private void pickFace(CubeFace face)
    {
        picked = face;

        this.fillFace();
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
        boolean mirror = this.boxMirror.getValue();

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
