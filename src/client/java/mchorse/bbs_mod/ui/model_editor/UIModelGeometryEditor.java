package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformOp;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.bones.UIBonePickerContextMenu;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The model editor of the model panel: the model itself rather than its configuration. Its groups
 * and their cubes as one tree ({@link UIModelTree}); the groups added, duplicated, removed,
 * renamed, dragged among their siblings or into another group — and for the picked one its rest,
 * the pivot it turns about and the rotation it rests at, on the viewport gizmo and in a transform
 * editor. For a picked cube, its numbers: where it starts, how big it is, the pivot it turns
 * about, its rotation and its inflate. Edits land in the live model (the preview shows them at
 * once), go on the panel's undo stack as snapshots of the model ({@link ModelEditUndo}), and the
 * panel writes the file on save.
 *
 * <p>The transform editors work in radians on stand-in transforms; the model rests in degrees.
 * A stand-in is loaded from the group or the cube on every fill and pushed back after every edit
 * — and every frame while it's picked, since a gizmo drag's sampling nudges the stand-in and
 * re-evaluates the model through it. The cube's is read as STEPS: what the stand-in moved by
 * since it was last carried into the cube, so a move of the cube's corner takes its pivot along
 * (the cube moves as a whole), and the pivot on its own row moves the point alone.</p>
 *
 * <p>Several rows can be picked at once (ctrl / shift, as in every list here): the verbs — copy,
 * remove — then work on all the picked groups as one undo step, and so does moving them. The
 * fields and the gizmo sit on the FIRST of the pick, and what it is moved by is added to every
 * other picked row — pivots and cubes travel together, keeping the distances between them. Only
 * moving: a name, a size and a rotation are each row's own, so those go dead while more than one
 * is picked rather than pretending to edit the first of them. The picked cubes light up in the
 * viewport ({@link #outlines}).</p>
 *
 * <p>Changed numbers leave their groups' quads and the model's bake behind; they are rebuilt once
 * the numbers have settled ({@link #settle}) — after a typed edit at once, after a gesture at its
 * end.</p>
 *
 * <p>Bound per fill: the tree keeps its pick across one by address, since a save reloads the
 * model and every group object with it — and so does every edit of the structure, which settles
 * the model again through the panel.</p>
 */
public class UIModelGeometryEditor extends UIElement
{
    /** How close a rest already is to the stand-in's numbers to be left alone — the round trip through radians isn't exact. */
    private static final float EPSILON = 1E-4F;

    /** How faintly the cubes under a picked group are outlined, next to a picked cube's full outline. */
    private static final float UNDER_GROUP_ALPHA = 0.35F;

    /** What a group's rest can take: moving and turning, no scale. */
    private static final Gizmo.HandleMask ANCHOR_MASK = Gizmo.HandleMask.of(
        EnumSet.of(Gizmo.Op.MOVE, Gizmo.Op.SCREEN, Gizmo.Op.ROTATE, Gizmo.Op.VIEW, Gizmo.Op.TRACKBALL),
        EnumSet.allOf(Axis.class)
    );

    /**
     * What a pick of several can take: moving only. Pivots are points, and one step added to all of
     * them is exact; a rest rotation is each bone's own, and a ring dragged over a pick of them has
     * no one answer — so those handles aren't offered rather than quietly turning only the first.
     */
    private static final Gizmo.HandleMask ANCHOR_MANY_MASK = Gizmo.HandleMask.of(
        EnumSet.of(Gizmo.Op.MOVE, Gizmo.Op.SCREEN),
        EnumSet.noneOf(Axis.class)
    );

    private final UIModelEditorPanel modelPanel;

    private final UIScrollView page;
    private final UIModelTree tree;
    private final UISearchList<ModelNode> search;
    private final UIIcon dupe;
    private final UIIcon remove;
    private final UIIcon ikBones;
    private final UIElement body;
    private final UITextbox name;

    /** The picked group's rest, edited through the stand-in. */
    private final UIPropTransform transform;
    private final Transform anchor = new Transform();

    /** The picked cube's position, size and rotation, edited through a stand-in of their own. */
    private final UIPropTransform cubeTransform;
    private final Transform standin = new Transform();

    /** What of the stand-in has already been carried into the cube, so a change reads as a step. */
    private final Transform applied = new Transform();

    /** The cube's pivot and inflate, on rows of their own. */
    private final UIElement pivotRow;
    private final UITrackpad[] pivotFields = new UITrackpad[3];
    private final UITrackpad inflate;
    private final UIElement inflateRow;

    /** Groups whose cubes changed numbers, waiting for their quads and their bake to be rebuilt. */
    private final Set<ModelGroup> dirty = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Whether changed numbers were baked group by group and the model as a whole still owes a settling. */
    private boolean unsettled;

    /** The model as it stood before the edit in progress, for the undo step it makes. */
    private MapType before;

    /** The live model the tree is bound to; null with no model open, or one that isn't cubic. */
    private Model model;

    /** The instance behind the model, whose bake changed numbers invalidate. */
    private ModelInstance instance;

    public UIModelGeometryEditor(UIModelEditorPanel panel)
    {
        this.modelPanel = panel;

        this.tree = new UIModelTree((list) -> this.fillSelection())
            .onReorder(this::moveNode)
            .onDrop(this::dropNode);
        this.tree.context(this::fillGroupMenu);
        this.search = new UISearchList<>(this.tree);
        this.search.label(UIKeys.GENERAL_SEARCH);
        this.search.h(20 + UIModelTree.ROW * 6).expand();

        /* The verbs over the tree, the list idiom of the panel: add goes under the picked group */
        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.addGroup());

        add.tooltip(UIKeys.MODEL_EDITOR_MODEL_GROUP_ADD);
        this.dupe = new UIIcon(Icons.DUPE, (b) -> this.duplicateGroups(this.pickedGroups()));
        this.dupe.tooltip(UIKeys.MODEL_EDITOR_MODEL_GROUP_DUPLICATE);
        this.remove = new UIIcon(Icons.REMOVE, (b) -> this.askRemoveGroups(this.pickedGroups()));
        this.remove.tooltip(UIKeys.MODEL_EDITOR_MODEL_GROUP_REMOVE);
        this.ikBones = new UIIcon(Icons.IK, (b) -> this.pickIKParent());
        this.ikBones.tooltip(UIKeys.MODEL_EDITOR_MODEL_GROUP_IK_BONES);

        /* The name is committed as a whole (enter, leaving the field): every keystroke would be a rename. */
        this.name = new UITextbox(64, this::rename);
        this.name.delayedInput();

        /* A group's rest has no scale. G/R start a gesture on the picked group without touching a
         * handle, the way every transform editor of the panel does. */
        this.transform = new UIPropTransform().noScale();
        this.transform.callbacks(this::beginEdit, this::commitEdit, this::endEdit);
        this.transform.hotkeyDrag(() ->
        {
            ModelSlotTarget target = this.shownTarget();

            return target == null ? null : this.modelPanel.renderer.buildGizmoDrag(target);
        });
        /* The hotkeys answer to the same rule as the gizmo's handles: a rest never scales, and it
         * only turns while one group is picked. */
        this.transform.enableHotkeys(() -> this.shownTarget() != null, (op) -> op == TransformOp.TRANSLATE || (op == TransformOp.ROTATE && this.singleGroup()));
        this.transform.translateAction(UIKeys.MODEL_EDITOR_MODEL_GROUP_CENTER_ANCHOR, this::centerAnchor);

        /* A cube's rows: its position (the corner it starts from), its size and its rotation, in
         * the same editor a group's rest sits in — the pads and the write path are the same — plus
         * the pivot it turns about on a row of its own, and the inflate below. The sizes stay three
         * numbers: a cube square on every side is the common case, not a reason to fold the row. */
        this.cubeTransform = new UIPropTransform().noUniformScale();
        this.cubeTransform.labels(UIKeys.MODEL_EDITOR_MODEL_CUBE_POSITION, UIKeys.MODEL_EDITOR_MODEL_CUBE_SIZE, UIKeys.MODEL_EDITOR_MODEL_CUBE_ROTATION);
        this.cubeTransform.callbacks(this::beginEdit, this::commitEdit, this::endEdit);

        IKey raw = IKey.constant("%s (%s)");
        IKey[] axes = {UIKeys.GENERAL_X, UIKeys.GENERAL_Y, UIKeys.GENERAL_Z};
        int[] colors = {Colors.RED, Colors.GREEN, Colors.BLUE};

        for (int i = 0; i < 3; i++)
        {
            int axis = i;
            UITrackpad field = new UITrackpad((v) -> this.setCubePivot(axis, v.floatValue())).block().onlyNumbers();

            field.tooltip(raw.format(UIKeys.MODEL_EDITOR_MODEL_CUBE_PIVOT, axes[i]));
            field.textbox.setColor(colors[i]);
            /* A finished drag of the pad closes its undo step, as the transform's own pads do, and settles the model. */
            field.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.endEdit());
            this.pivotFields[i] = field;
        }

        /* Decorative, like the icons of the rows above it. */
        UIIcon pivotIcon = new UIIcon(Icons.SPHERE, null);

        pivotIcon.disabledColor = pivotIcon.hoverColor = Colors.WHITE;
        pivotIcon.setEnabled(false);
        this.pivotRow = this.cubeTransform.addRow(pivotIcon, this.pivotFields[0], this.pivotFields[1], this.pivotFields[2]);

        this.inflate = new UITrackpad((v) -> this.setInflate(v.floatValue()));
        this.inflate.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.endEdit());
        this.inflateRow = UI.labelRow(UIKeys.MODEL_EDITOR_MODEL_CUBE_INFLATE, this.inflate);

        this.body = new UIElement();
        this.body.column(UIConstants.MARGIN).vertical().stretch();
        this.body.add(UI.labelRow(UIKeys.MODEL_EDITOR_MODEL_GROUP_NAME, this.name), this.transform, this.cubeTransform, this.inflateRow);

        this.page = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING, UI.strip(add, this.dupe, this.remove, this.ikBones), this.search, this.body);
        this.page.full(this);
        this.add(this.page);

        this.registerKeybinds();
    }

    /**
     * The tree's verbs on the keyboard. Registered on the tree rather than on the editor, and only
     * while the cursor is over it — the same keys mean other things elsewhere in the panel, and
     * Delete would otherwise reach the tree from anywhere. Each key answers to the same rule as its
     * icon, so a verb with nothing to act on simply isn't there.
     */
    private void registerKeybinds()
    {
        IKey category = UIKeys.MODEL_EDITOR_TITLE;
        Supplier<Boolean> open = () -> this.model != null;
        Supplier<Boolean> any = () -> !this.pickedGroups().isEmpty();

        this.tree.keys().register(Keys.MODEL_EDITOR_GROUP_ADD, this::addGroup).inside().active(open).category(category);
        this.tree.keys().register(Keys.MODEL_EDITOR_GROUP_DUPE, () -> this.duplicateGroups(this.pickedGroups())).inside().active(any).category(category);
        this.tree.keys().register(Keys.DELETE, () -> this.askRemoveGroups(this.pickedGroups())).inside().active(any).category(category);
        this.tree.keys().register(Keys.MODEL_EDITOR_GROUP_RENAME, this::editName).inside().active(this::single).category(category);
        this.tree.keys().register(Keys.MODEL_EDITOR_GROUP_IK_BONES, this::pickIKParent).inside().active(this::singleGroup).category(category);
    }

    /** F2: the name field takes the caret, since the tree renames through it rather than in place. */
    private void editName()
    {
        this.getContext().focus(this.name);
    }

    /* The pick */

    /** The first of the pick — what the fields, the gizmo and the verbs start from; null with nothing picked. */
    private ModelNode leaderNode()
    {
        return this.model == null ? null : this.tree.getCurrentFirst();
    }

    /**
     * The picked group, by name — what the viewport marks and the fields edit; null with nothing
     * picked, with a cube, and with several rows, which belong to no single group.
     */
    public String getSelected()
    {
        ModelNode node = this.leaderNode();

        return node != null && node.isGroup() && this.tree.getCurrent().size() == 1 ? node.group() : null;
    }

    /** What the viewport gizmo is on: the leading group's rest, through the stand-in. */
    public ModelSlotTarget shownTarget()
    {
        String id = this.leader();

        if (id == null)
        {
            return null;
        }

        return new ModelSlotTarget(id, ModelSlotKind.ANCHOR, this.transform, this::applyAnchor, this.single() ? ANCHOR_MASK : ANCHOR_MANY_MASK);
    }

    /** The group the fields and the gizmo sit on: the first of the pick, when it is a group, which the rest follows. */
    private String leader()
    {
        ModelNode node = this.leaderNode();

        return node != null && node.isGroup() ? node.group() : null;
    }

    /** Whether the pick is one row, of either kind — what a name, a size and a rotation need to mean anything. */
    private boolean single()
    {
        return this.model != null && this.tree.getCurrent().size() == 1;
    }

    /** Whether the pick is one group — what a rest rotation and the IK verbs need. */
    private boolean singleGroup()
    {
        return this.getSelected() != null;
    }

    private ModelGroup leadGroup()
    {
        String id = this.leader();

        return id == null ? null : this.model.getGroup(id);
    }

    /** The cube the fields sit on: the first of the pick, when it is a cube; null otherwise. */
    private ModelCube leadCube()
    {
        ModelNode node = this.leaderNode();
        ModelGroup group = node != null && node.isCube() ? this.model.getGroup(node.group()) : null;

        return group != null && node.cube() < group.cubes.size() ? group.cubes.get(node.cube()) : null;
    }

    /** Bind to a model (null for none); the tree keeps its pick by address. */
    public void fill(ModelInstance instance)
    {
        this.instance = instance;
        this.model = instance != null && instance.getModel() instanceof Model model ? model : null;
        this.dirty.clear();
        this.unsettled = false;

        List<ModelNode> picked = new ArrayList<>(this.tree.getCurrent());

        this.tree.fill(this.model);
        this.tree.setCurrent(picked);
        this.fillSelection();
    }

    /**
     * A click on the model in the preview: the bone, and the cube of it under the cursor (-1 for
     * the bone itself) — picked in the tree, or added to the pick with ctrl held, as a click on a
     * row would. Whether the click was taken.
     */
    public boolean selectPick(String bone, int cube)
    {
        ModelGroup group = this.model == null ? null : this.model.getGroup(bone);

        if (group == null)
        {
            return false;
        }

        ModelNode node = cube >= 0 && cube < group.cubes.size() ? ModelNode.cube(bone, cube) : ModelNode.group(bone);

        this.search.filter("", true);
        this.tree.reveal(node);

        if (Window.isCtrlPressed())
        {
            this.tree.toggleIndex(this.tree.getList().indexOf(node));
        }
        else
        {
            this.tree.setCurrent(node);
        }

        this.fillSelection();

        return true;
    }

    private void select(String group)
    {
        this.select(ModelNode.group(group));
    }

    private void select(ModelNode node)
    {
        this.tree.reveal(node);
        this.tree.setCurrent(node);
        this.fillSelection();
    }

    /** Pick several groups at once — what a verb on several leaves behind. */
    private void selectAll(List<String> ids)
    {
        List<ModelNode> nodes = new ArrayList<>();

        for (String id : ids)
        {
            nodes.add(ModelNode.group(id));
        }

        if (!nodes.isEmpty())
        {
            this.tree.reveal(nodes.get(0));
        }

        this.tree.setCurrent(nodes);
        this.fillSelection();
    }

    private ModelGroup picked()
    {
        String id = this.getSelected();

        return id == null ? null : this.model.getGroup(id);
    }

    /** Every picked group, in the order the tree lists them; empty with nothing picked. Picked cubes aren't groups. */
    private List<ModelGroup> pickedGroups()
    {
        List<ModelGroup> groups = new ArrayList<>();

        if (this.model == null)
        {
            return groups;
        }

        for (ModelNode node : this.tree.getCurrent())
        {
            ModelGroup group = node.isGroup() ? this.model.getGroup(node.group()) : null;

            if (group != null)
            {
                groups.add(group);
            }
        }

        return groups;
    }

    /**
     * What the viewport outlines: every picked cube, and — fainter — every cube under a picked
     * group, so a group reads as the shape it carries. Built for the frame being drawn; the
     * addresses are looked up on the model as it stands.
     */
    public List<UIModelEditorRenderer.Outline> outlines()
    {
        List<UIModelEditorRenderer.Outline> outlines = new ArrayList<>();

        if (this.model == null)
        {
            return outlines;
        }

        int accent = BBSSettings.primaryColor.get();
        List<ModelNode> picked = this.tree.getCurrent();

        /* The groups' cubes first, so a picked cube's own outline draws over its group's fainter one. */
        for (ModelNode node : picked)
        {
            ModelGroup group = node.isGroup() ? this.model.getGroup(node.group()) : null;

            if (group != null)
            {
                this.outlineSubtree(group, Colors.setA(accent, UNDER_GROUP_ALPHA), outlines);
            }
        }

        for (ModelNode node : picked)
        {
            if (node.isCube())
            {
                outlines.add(new UIModelEditorRenderer.Outline(node, Colors.A100 | accent));
            }
        }

        return outlines;
    }

    private void outlineSubtree(ModelGroup group, int color, List<UIModelEditorRenderer.Outline> outlines)
    {
        for (int i = 0; i < group.cubes.size(); i++)
        {
            outlines.add(new UIModelEditorRenderer.Outline(ModelNode.cube(group.id, i), color));
        }

        for (ModelGroup child : group.children)
        {
            this.outlineSubtree(child, color, outlines);
        }
    }

    /**
     * The leading row under the tree: a group's name and rest, or a cube's name and numbers — one
     * of the two editors shows. With nothing picked the fields stand empty and disabled, so the
     * page keeps its height and the scroll doesn't jump on every pick. With several picked the
     * position stays live — it moves the whole pick — while the name, a size, a rotation, a cube's
     * pivot and inflate, which belong to one row each, go dead. The verbs act on the groups of the
     * pick, however the pick is mixed.
     */
    private void fillSelection()
    {
        ModelNode leader = this.leaderNode();
        ModelGroup group = this.leadGroup();
        ModelCube cube = this.leadCube();

        boolean any = group != null || cube != null;
        boolean groups = !this.pickedGroups().isEmpty();
        boolean single = this.single();
        boolean singleGroup = this.singleGroup();
        boolean singleCube = single && cube != null;

        /* An unnamed cube shows the name it goes by as a hint, so typing over it names the cube. */
        this.name.setText(group != null ? group.id : cube != null ? cube.name : "");
        this.name.textbox.setPlaceholder(cube != null && cube.name.isEmpty() ? IKey.constant(UIModelTree.cubeLabel(cube, leader.cube())) : IKey.EMPTY);
        this.loadAnchor(group);
        this.transform.setTransform(this.anchor);
        this.loadCube(cube);
        this.cubeTransform.setTransform(this.standin);

        this.transform.setVisible(cube == null);
        this.cubeTransform.setVisible(cube != null);
        this.inflateRow.setVisible(cube != null);

        UIUtils.setEnabledDeep(this.body, any);
        this.transform.setRotationEnabled(singleGroup);
        this.cubeTransform.setScaleEnabled(singleCube);
        this.cubeTransform.setRotationEnabled(singleCube);
        UIUtils.setEnabledDeep(this.pivotRow, singleCube);
        this.inflate.setEnabled(singleCube);
        this.name.setEnabled(single);
        this.dupe.setEnabled(groups);
        this.remove.setEnabled(groups);
        this.ikBones.setEnabled(singleGroup);

        this.page.resize();
        this.page.scroll.clamp();
    }

    /**
     * The row's menu offers the verbs of the strip. A row outside the pick becomes the pick; a row
     * already in it leaves the pick alone, so a menu opened on several groups acts on all of them.
     */
    private void fillGroupMenu(ContextMenuManager menu)
    {
        ModelNode node = this.model == null ? null : this.tree.atCursor(this.getContext());

        if (node == null)
        {
            return;
        }

        if (!this.tree.getCurrent().contains(node))
        {
            this.select(node);
        }

        List<ModelGroup> picked = this.pickedGroups();

        menu.icon(MenuVerb.ADD, this::addGroup).label(UIKeys.MODEL_EDITOR_MODEL_GROUP_ADD);

        if (!picked.isEmpty())
        {
            menu.action(Icons.DUPE, UIKeys.MODEL_EDITOR_MODEL_GROUP_DUPLICATE, () -> this.duplicateGroups(picked));
            menu.icon(MenuVerb.REMOVE, () -> this.askRemoveGroups(picked)).label(UIKeys.MODEL_EDITOR_MODEL_GROUP_REMOVE);
        }

        if (this.getSelected() != null)
        {
            menu.action(Icons.IK, UIKeys.MODEL_EDITOR_MODEL_GROUP_IK_BONES, this::pickIKParent);
        }
    }

    /* The rest: the stand-in between the transform editor and the group */

    /** The stand-in takes the group's rest: the pivot as it is, the rotation in radians. */
    private void loadAnchor(ModelGroup group)
    {
        this.anchor.identity();

        if (group != null)
        {
            Vector3f rotate = group.initial.rotate;

            this.anchor.translate.set(group.initial.translate);
            this.anchor.rotate.set(MathUtils.toRad(rotate.x), MathUtils.toRad(rotate.y), MathUtils.toRad(rotate.z));
        }
    }

    /**
     * The leading group takes the stand-in's numbers, and the rest of the pick takes the same STEP
     * rather than the leader's pivot — several picked pivots move together and keep the distances
     * between them, which is what makes a controller stay on the tip it was created on.
     *
     * <p>A rest already within a hair of the numbers is left alone: the round trip through radians
     * isn't exact, and the file must not pick up the noise. The rotation is the leader's alone —
     * with several picked, neither the gizmo nor the row offers it (see {@link #ANCHOR_MANY_MASK}).</p>
     */
    private void applyAnchor()
    {
        ModelNode leader = this.leaderNode();
        ModelGroup group = this.leadGroup();

        if (group == null)
        {
            return;
        }

        Vector3f rotate = this.anchor.rotate;
        Vector3f degrees = new Vector3f(MathUtils.toDeg(rotate.x), MathUtils.toDeg(rotate.y), MathUtils.toDeg(rotate.z));

        if (!group.initial.translate.equals(this.anchor.translate, EPSILON))
        {
            Vector3f step = new Vector3f(this.anchor.translate).sub(group.initial.translate);

            group.initial.translate.set(this.anchor.translate);
            this.distributeStep(step, leader);
        }

        if (!group.initial.rotate.equals(degrees, EPSILON))
        {
            group.initial.rotate.set(degrees);
        }
    }

    /**
     * Every picked row but the leader takes the step the leader took: a group's pivot moves by it,
     * a cube moves as a whole by it — they travel together, keeping the distances between them.
     */
    private void distributeStep(Vector3f step, ModelNode leader)
    {
        for (ModelNode node : this.tree.getCurrent())
        {
            ModelGroup group = node.equals(leader) ? null : this.model.getGroup(node.group());

            if (group == null)
            {
                continue;
            }

            if (node.isGroup())
            {
                group.initial.translate.add(step);
            }
            else if (node.cube() < group.cubes.size())
            {
                group.cubes.get(node.cube()).shift(step);
                this.dirty.add(group);
            }
        }
    }

    /**
     * Every picked group's pivot to the middle of what that group draws — each on its own geometry,
     * not on the pick's — as the translate row's icon. Only the pivot moves: cube and mesh
     * coordinates are absolute in the model, so the geometry stays exactly where it stands and what
     * changes is the point the bone turns about. A group with no geometry at all is passed over.
     *
     * <p>The leader goes through the stand-in rather than into the group, since the stand-in is what
     * {@link #render} writes back every frame — the group would take the new pivot and lose it
     * again on the very next one. The others have no stand-in and take it directly.</p>
     */
    private void centerAnchor()
    {
        ModelGroup leader = this.leadGroup();
        List<ModelGroup> picked = this.pickedGroups();

        if (leader == null)
        {
            return;
        }

        MapType before = this.snapshot();
        int centered = 0;

        for (ModelGroup group : picked)
        {
            Vector3f min = new Vector3f();
            Vector3f max = new Vector3f();

            if (!group.getGeometryBounds(min, max))
            {
                continue;
            }

            Vector3f center = min.add(max).mul(0.5F);
            Vector3f pivot = group == leader ? this.anchor.translate : group.initial.translate;

            if (pivot.equals(center, EPSILON))
            {
                continue;
            }

            pivot.set(center);
            centered++;
        }

        if (centered == 0)
        {
            return;
        }

        /* The leader's new pivot is in the stand-in; the step it just took must not drag the others,
         * which have already been centred on their own geometry. */
        leader.initial.translate.set(this.anchor.translate);

        IKey label = picked.size() > 1
            ? UIKeys.MODEL_EDITOR_MODEL_UNDO_CENTER_ANCHOR_MANY.format(picked.size())
            : UIKeys.MODEL_EDITOR_MODEL_UNDO_CENTER_ANCHOR.format(leader.id);

        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, label.get(), null, before, this.snapshot()));
        this.modelPanel.closeModelEdit();
    }

    /* The cube: the stand-in between its transform editor and the cube, and its own rows */

    /** The stand-in takes the cube's numbers: its corner, its size, its rotation in radians; the rows below take its pivot and inflate. */
    private void loadCube(ModelCube cube)
    {
        this.standin.identity();

        if (cube != null)
        {
            Vector3f rotate = cube.rotate;

            this.standin.translate.set(cube.origin);
            this.standin.scale.set(cube.size);
            this.standin.rotate.set(MathUtils.toRad(rotate.x), MathUtils.toRad(rotate.y), MathUtils.toRad(rotate.z));
        }

        this.applied.copy(this.standin);

        for (int i = 0; i < 3; i++)
        {
            this.pivotFields[i].setValue(cube == null ? 0D : cube.pivot.get(i));
        }

        this.inflate.setValue(cube == null ? 0D : cube.inflate);
    }

    /**
     * The leading cube takes the stand-in's numbers. Its position as a STEP from what was last
     * carried in, so the cube moves as a whole — its pivot along with its corner — and the rest of
     * the pick moves by the same step; its size and rotation as they are. A cube already within a
     * hair of the numbers is left alone, as a group's rest is.
     */
    private void applyCube()
    {
        ModelNode leader = this.leaderNode();
        ModelCube cube = this.leadCube();

        if (cube == null)
        {
            return;
        }

        ModelGroup group = this.model.getGroup(leader.group());
        Vector3f step = new Vector3f(this.standin.translate).sub(this.applied.translate);
        Vector3f rotate = this.standin.rotate;
        Vector3f degrees = new Vector3f(MathUtils.toDeg(rotate.x), MathUtils.toDeg(rotate.y), MathUtils.toDeg(rotate.z));
        boolean changed = false;

        if (step.length() > EPSILON)
        {
            cube.shift(step);
            this.distributeStep(step, leader);
            changed = true;
        }

        if (!cube.size.equals(this.standin.scale, EPSILON))
        {
            cube.size.set(this.standin.scale);
            changed = true;
        }

        if (!cube.rotate.equals(degrees, EPSILON))
        {
            cube.rotate.set(degrees);
            changed = true;
        }

        this.applied.copy(this.standin);

        if (changed)
        {
            this.dirty.add(group);
        }
    }

    /** The pivot alone: the point the cube turns about, with the cube left where it stands. */
    private void setCubePivot(int axis, float value)
    {
        ModelNode leader = this.leaderNode();
        ModelCube cube = this.leadCube();

        if (cube == null || cube.pivot.get(axis) == value)
        {
            return;
        }

        MapType before = this.snapshot();

        cube.pivot.setComponent(axis, value);
        this.dirty.add(this.model.getGroup(leader.group()));
        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, UIKeys.MODEL_EDITOR_MODEL_UNDO_CUBE_PIVOT.format(UIModelTree.cubeLabel(cube, leader.cube())).get(), "pivot:" + leader.key(), before, this.snapshot()));
    }

    /** How far the cube grows past its corners on every side. */
    private void setInflate(float value)
    {
        ModelNode leader = this.leaderNode();
        ModelCube cube = this.leadCube();

        if (cube == null || cube.inflate == value)
        {
            return;
        }

        MapType before = this.snapshot();

        cube.inflate = value;
        this.dirty.add(this.model.getGroup(leader.group()));
        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, UIKeys.MODEL_EDITOR_MODEL_UNDO_CUBE_INFLATE.format(UIModelTree.cubeLabel(cube, leader.cube())).get(), "inflate:" + leader.key(), before, this.snapshot()));
    }

    /**
     * Rebuild what changed numbers invalidated so far: the quads of the touched groups, and their
     * bake alone — once a frame, however many times the numbers moved in it. A drag of a pad or a
     * gizmo changes the numbers on every step, and re-uploading the whole model each time would be
     * a drag's worth of needless work; the model as a whole is settled when the drag is over.
     */
    private void bake()
    {
        if (this.dirty.isEmpty() || this.model == null)
        {
            return;
        }

        this.model.refreshGeometry(this.dirty);

        if (this.instance != null)
        {
            this.instance.rebakeGroups(this.dirty);
        }

        this.dirty.clear();
        this.unsettled = true;
    }

    /**
     * Settle the model as a whole after changed numbers: whatever is still unbaked, then — through
     * the panel — its bake, its welds and its animator, which a group's own bake leaves behind.
     * Nothing to do while nothing changed.
     */
    private void settle()
    {
        this.bake();

        if (this.unsettled)
        {
            this.unsettled = false;
            this.modelPanel.refresh();
        }
    }

    /** Whether a gizmo or hotkey gesture is driving one of the editors, so the numbers haven't settled yet. */
    private boolean gestureRunning()
    {
        return this.transform.isEditing() || this.cubeTransform.isEditing();
    }

    /** Whether a pad of the cube's rows is being dragged — the numbers move on every step of it. */
    private boolean padDragging()
    {
        for (UITrackpad pad : new UITrackpad[]{this.cubeTransform.tx, this.cubeTransform.ty, this.cubeTransform.tz, this.cubeTransform.sx, this.cubeTransform.sy, this.cubeTransform.sz, this.cubeTransform.rx, this.cubeTransform.ry, this.cubeTransform.rz, this.pivotFields[0], this.pivotFields[1], this.pivotFields[2], this.inflate})
        {
            if (pad.isDragging())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The stand-ins are the truth of the picked row's numbers for as long as it's picked — see the
     * class. What they changed is baked once a frame, and the model settles as a whole as soon as
     * nothing is driving the numbers any more.
     */
    @Override
    public void render(UIContext context)
    {
        if (this.leadCube() != null)
        {
            this.applyCube();
        }
        else
        {
            this.applyAnchor();
        }

        if (this.gestureRunning() || this.padDragging())
        {
            this.bake();
        }
        else
        {
            this.settle();
        }

        super.render(context);
    }

    /* An edit of the numbers: the model before it, the model after it, one step on the stack — the
     * steps of a single gesture merge, and its end keeps the next one apart. */

    private MapType snapshot()
    {
        return this.model.toData();
    }

    private void beginEdit()
    {
        if (this.model != null)
        {
            this.before = this.snapshot();
        }
    }

    private void commitEdit()
    {
        ModelNode leader = this.leaderNode();

        if (leader == null || this.before == null)
        {
            this.before = null;

            return;
        }

        ModelCube cube = this.leadCube();
        IKey label;
        String key;

        if (cube != null)
        {
            this.applyCube();
            label = UIKeys.MODEL_EDITOR_MODEL_UNDO_CUBE_TRANSFORM.format(UIModelTree.cubeLabel(cube, leader.cube()));
            key = "transform:" + leader.key();
        }
        else
        {
            int picked = this.pickedGroups().size();

            this.applyAnchor();
            label = picked > 1
                ? UIKeys.MODEL_EDITOR_MODEL_UNDO_TRANSFORM_MANY.format(picked)
                : UIKeys.MODEL_EDITOR_MODEL_UNDO_TRANSFORM.format(leader.group());
            key = "transform:" + leader.group();
        }

        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, label.get(), key, this.before, this.snapshot()));
        this.before = null;
    }

    /** The end of a gesture or a pad's drag: its undo step closes, and the model settles. */
    private void endEdit()
    {
        this.modelPanel.closeModelEdit();
        this.settle();
    }

    /* The structure: groups added, copied, removed, renamed, moved — each one undo step, settled
     * and shown through the panel right after. */

    /** An edit of the model's structure: snapshot, change, push, settle. */
    private void edit(IKey label, Runnable mutation)
    {
        MapType before = this.snapshot();

        mutation.run();
        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, label.get(), null, before, this.snapshot()));
        this.modelPanel.modelStructureChanged();
    }

    /** Where a group sits among its siblings: its parent's children, or the model's roots. */
    private List<ModelGroup> siblings(ModelGroup group)
    {
        return group.parent == null ? this.model.topGroups : group.parent.children;
    }

    /** A name no group has, from {@code base}: the base itself, else with a number after it; {@code taken} holds the names given out before the model knows them. */
    private String uniqueName(String base, Set<String> taken)
    {
        String name = base;

        for (int i = 2; this.model.getGroup(name) != null || taken.contains(name); i++)
        {
            name = base + "_" + i;
        }

        taken.add(name);

        return name;
    }

    /** A new, empty group under the picked one (at its pivot) — under a picked cube's group — or at the root with nothing picked. */
    private void addGroup()
    {
        if (this.model == null)
        {
            return;
        }

        ModelNode first = this.leaderNode();
        ModelGroup parent = first == null ? null : this.model.getGroup(first.group());
        String name = this.uniqueName("group", new HashSet<>());

        this.edit(UIKeys.MODEL_EDITOR_MODEL_UNDO_ADD.format(name), () -> this.addBone(name, parent, parent == null ? null : parent.initial.translate));
        this.select(name);
    }

    /** A new, empty group under {@code parent} (at the root with none), resting at {@code pivot} — the model's origin for none. */
    private ModelGroup addBone(String name, ModelGroup parent, Vector3f pivot)
    {
        ModelGroup group = new ModelGroup(name);

        if (pivot != null)
        {
            group.initial.translate.set(pivot);
        }

        (parent == null ? this.model.topGroups : parent.children).add(group);

        return group;
    }

    /**
     * The IK shortcut: ask what the controls should hang off, then make the three bones an IK chain
     * wants around the picked one. Bones only — what actually solves lives on the FORM (its bones'
     * IK), which this panel doesn't hold, so the chain is still switched on there; the names are a
     * convention of the rigger's, nothing in BBS reads them.
     *
     * <p>The picked bone and everything under it are refused as the parent: a controller inside the
     * chain it drives is the one arrangement IK can't solve.</p>
     */
    private void pickIKParent()
    {
        String id = this.getSelected();

        if (id == null)
        {
            return;
        }

        Set<String> inside = new HashSet<>(this.model.getAllChildrenKeys(id));
        UIBonePickerContextMenu picker = new UIBonePickerContextMenu((parent) -> this.addIKBones(id, parent));

        inside.add(id);
        picker.bones(this.model, null).none().disabled(inside::contains);
        this.getContext().replaceContextMenu(picker);
    }

    /**
     * The tip inside the picked bone, the controller under {@code parentId} (the root for no bone) and
     * the pole inside the controller, as one undo step. All three rest at the picked bone's pivot — they
     * start on the joint they were asked about and are dragged out from there.
     *
     * <p>The tip and the controller become the pick, in that order, so the very next drag moves the two
     * of them together: they have to sit on the same point for the chain to switch on without a jump,
     * and picked together they can no longer drift apart. The pole is left out — where it goes is the
     * chain's business, not the tip's.</p>
     */
    private void addIKBones(String id, String parentId)
    {
        ModelGroup group = this.model == null ? null : this.model.getGroup(id);

        if (group == null)
        {
            return;
        }

        ModelGroup parent = parentId == null || parentId.isEmpty() ? null : this.model.getGroup(parentId);
        Set<String> taken = new HashSet<>();
        String end = this.uniqueName(id + "_end", taken);
        String controller = this.uniqueName("controller_" + id, taken);
        String pole = this.uniqueName("pole_" + id, taken);
        Vector3f pivot = new Vector3f(group.initial.translate);

        this.edit(UIKeys.MODEL_EDITOR_MODEL_UNDO_IK_BONES.format(id), () ->
        {
            this.addBone(end, group, pivot);
            this.addBone(pole, this.addBone(controller, parent, pivot), pivot);
        });
        this.selectAll(List.of(end, controller));
    }

    /**
     * A copy of every picked group and everything in it, each right after its original among the
     * siblings, as one undo step; the copies become the pick. A group inside another picked one is
     * left out: its copy already comes along inside that one.
     */
    private void duplicateGroups(List<ModelGroup> picked)
    {
        List<ModelGroup> groups = outermost(picked);

        if (groups.isEmpty())
        {
            return;
        }

        Set<String> taken = new HashSet<>();
        List<ModelGroup> copies = new ArrayList<>();
        List<String> names = new ArrayList<>();

        for (ModelGroup group : groups)
        {
            ModelGroup copy = this.copy(group, taken);

            copies.add(copy);
            names.add(copy.id);
        }

        this.edit(label(UIKeys.MODEL_EDITOR_MODEL_UNDO_DUPLICATE, UIKeys.MODEL_EDITOR_MODEL_UNDO_DUPLICATE_MANY, groups), () ->
        {
            for (int i = 0; i < groups.size(); i++)
            {
                ModelGroup group = groups.get(i);
                List<ModelGroup> siblings = this.siblings(group);

                siblings.add(siblings.indexOf(group) + 1, copies.get(i));
            }
        });
        this.selectAll(names);
    }

    /** A group and its subtree as new groups under new names, the cubes rebuilt from their data. */
    private ModelGroup copy(ModelGroup group, Set<String> taken)
    {
        ModelGroup copy = new ModelGroup(this.uniqueName(group.id, taken));

        copy.fromData(group.toData());
        copy.generateQuads(this.model.textureWidth, this.model.textureHeight);

        for (ModelGroup child : group.children)
        {
            copy.children.add(this.copy(child, taken));
        }

        return copy;
    }

    /** Removing takes the subtrees and their cubes with them, so it's asked about first. */
    private void askRemoveGroups(List<ModelGroup> picked)
    {
        List<ModelGroup> groups = outermost(picked);

        if (groups.isEmpty())
        {
            return;
        }

        IKey question = groups.size() == 1
            ? UIKeys.MODEL_EDITOR_MODEL_GROUP_REMOVE_CONFIRM.format(groups.get(0).id)
            : UIKeys.MODEL_EDITOR_MODEL_GROUP_REMOVE_CONFIRM_MANY.format(groups.size());

        UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(
            UIKeys.MODEL_EDITOR_MODEL_GROUP_REMOVE,
            question,
            (confirm) ->
            {
                if (confirm)
                {
                    this.removeGroups(groups);
                }
            }
        ));
    }

    private void removeGroups(List<ModelGroup> groups)
    {
        this.tree.deselect();
        this.edit(label(UIKeys.MODEL_EDITOR_MODEL_UNDO_REMOVE, UIKeys.MODEL_EDITOR_MODEL_UNDO_REMOVE_MANY, groups), () ->
        {
            for (ModelGroup group : groups)
            {
                this.siblings(group).remove(group);
            }
        });
        this.fillSelection();
    }

    /**
     * The picked groups with the ones inside another of them left out: a verb on a group is a verb
     * on its whole subtree, so acting on an ancestor and its descendant both would do it twice.
     */
    private static List<ModelGroup> outermost(List<ModelGroup> groups)
    {
        List<ModelGroup> outer = new ArrayList<>();

        for (ModelGroup group : groups)
        {
            boolean inside = false;

            for (ModelGroup parent = group.parent; parent != null && !inside; parent = parent.parent)
            {
                inside = groups.contains(parent);
            }

            if (!inside)
            {
                outer.add(group);
            }
        }

        return outer;
    }

    /** The undo label for a verb on one group (its name) or on several (how many). */
    private static IKey label(IKey one, IKey many, List<ModelGroup> groups)
    {
        return groups.size() == 1 ? one.format(groups.get(0).id) : many.format(groups.size());
    }

    /** The name field, committed: the picked row takes the name, whichever kind it is. */
    private void rename(String to)
    {
        ModelNode leader = this.leaderNode();

        if (leader != null && leader.isCube() && this.single())
        {
            this.renameCube(leader, to.trim());
        }
        else
        {
            this.renameGroup(to);
        }
    }

    /**
     * Rename the picked group everywhere the model's folder knows it, as one undo step that carries
     * the config along. A name that's empty, unchanged or taken is refused, and the field goes back
     * to the name the group has.
     */
    private void renameGroup(String to)
    {
        ModelGroup group = this.picked();
        String from = group == null ? null : group.id;

        to = to.trim();

        if (from == null || to.isEmpty() || to.equals(from) || this.model.getGroup(to) != null)
        {
            this.name.setText(from == null ? "" : from);

            return;
        }

        MapType modelBefore = this.snapshot();
        MapType configBefore = this.modelPanel.getData().toData().asMap();

        this.modelPanel.renameBone(from, to);
        this.modelPanel.pushModelEdit(new ModelEditUndo(this.modelPanel, UIKeys.MODEL_EDITOR_MODEL_UNDO_RENAME.format(from, to).get(), null, modelBefore, this.snapshot(), configBefore, this.modelPanel.getData().toData().asMap(), from, to));
        this.modelPanel.modelStructureChanged();
        this.select(to);
    }

    /**
     * Name the picked cube — or take its name away, so it goes by its number again. Nothing refers
     * to a cube by name, so any name goes, a repeated one included.
     */
    private void renameCube(ModelNode node, String to)
    {
        ModelCube cube = this.leadCube();

        if (cube == null || to.equals(cube.name))
        {
            this.name.setText(cube == null ? "" : cube.name);

            return;
        }

        String from = UIModelTree.cubeLabel(cube, node.cube());
        String named = to.isEmpty() ? UIKeys.MODEL_EDITOR_MODEL_CUBE_LABEL.format(node.cube() + 1).get() : to;

        this.edit(UIKeys.MODEL_EDITOR_MODEL_UNDO_CUBE_RENAME.format(from, named), () -> cube.name = to);
    }

    /* Drops, as the tree reports them */

    /** A row dropped between rows; only groups travel for now. */
    private void moveNode(ModelNode dragged, ModelNode before)
    {
        if (dragged.isGroup())
        {
            this.moveGroup(dragged.group(), before);
        }
    }

    /** A row dropped onto a group's row; only groups travel for now. */
    private void dropNode(ModelNode dragged, String parentId)
    {
        if (dragged.isGroup())
        {
            this.reparentGroup(dragged.group(), parentId);
        }
    }

    /**
     * A group dropped between rows becomes the sibling right before {@code before} — at whatever
     * depth that row sits, since changing a parent is allowed here — or, above a cube's row, the
     * first group inside that cube's group: that is where the caret sits; {@code before} null sends
     * it to the end of the roots. A drop inside the group's own subtree is refused: it would take
     * the group out of the model with it.
     */
    private void moveGroup(String id, ModelNode before)
    {
        ModelGroup group = this.model == null ? null : this.model.getGroup(id);
        ModelGroup target = before == null || this.model == null ? null : this.model.getGroup(before.group());

        if (group == null || (before != null && target == null) || (target != null && inside(target, group)))
        {
            return;
        }

        this.edit(UIKeys.MODEL_EDITOR_MODEL_UNDO_MOVE.format(id), () ->
        {
            this.siblings(group).remove(group);

            if (target == null)
            {
                this.model.topGroups.add(group);
            }
            else if (before.isCube())
            {
                target.children.add(0, group);
            }
            else
            {
                List<ModelGroup> destination = target.parent == null ? this.model.topGroups : target.parent.children;

                destination.add(destination.indexOf(target), group);
            }
        });
        this.select(id);
    }

    /** Whether {@code group} is {@code ancestor} itself or sits somewhere under it. */
    private static boolean inside(ModelGroup group, ModelGroup ancestor)
    {
        for (ModelGroup parent = group; parent != null; parent = parent.parent)
        {
            if (parent == ancestor)
            {
                return true;
            }
        }

        return false;
    }

    /** A group dropped onto another goes inside it, last. */
    private void reparentGroup(String id, String parentId)
    {
        ModelGroup group = this.model == null ? null : this.model.getGroup(id);
        ModelGroup parent = this.model == null ? null : this.model.getGroup(parentId);

        if (group == null || parent == null || group == parent)
        {
            return;
        }

        this.edit(UIKeys.MODEL_EDITOR_MODEL_UNDO_MOVE.format(id), () ->
        {
            this.siblings(group).remove(group);
            parent.children.add(group);
        });
        this.select(id);
    }
}
