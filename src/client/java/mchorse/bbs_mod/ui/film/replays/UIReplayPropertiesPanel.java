package mchorse.bbs_mod.ui.film.replays;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;

import java.util.function.Consumer;

public class UIReplayPropertiesPanel extends UIElement
{
    private static final List<BiFunction<UIFilmPanel, Supplier<Replay>, UIElement>> ACTIONS = new ArrayList<>();

    public static void registerAction(BiFunction<UIFilmPanel, Supplier<Replay>, UIElement> factory)
    {
        ACTIONS.add(Objects.requireNonNull(factory));
    }

    private final UIFilmPanel filmPanel;

    public UIElement properties;
    public UINestedEdit pickEdit;
    public UIToggle enabled;
    public UITextbox label;
    public UITextbox nameTag;
    public UIToggle shadow;
    public UITrackpad shadowSize;
    public UIToggle shadowFollow;
    public UITrackpad shadowOffsetX;
    public UITrackpad shadowOffsetY;
    public UITrackpad shadowOffsetZ;
    public UITrackpad looping;
    public UIToggle actor;
    public UIToggle actorPickup;
    public UIToggle dropItemsOnDeath;
    public UIElement dropVelocityGroup;
    public UITrackpad dropVelocityMinX;
    public UITrackpad dropVelocityMaxX;
    public UITrackpad dropVelocityMinY;
    public UITrackpad dropVelocityMaxY;
    public UITrackpad dropVelocityMinZ;
    public UITrackpad dropVelocityMaxZ;
    public UIToggle fp;
    public UIToggle relative;
    public UITrackpad relativeOffsetX;
    public UITrackpad relativeOffsetY;
    public UITrackpad relativeOffsetZ;
    public UIToggle axesPreview;
    public UIButton pickAxesPreviewBone;
    public UIToggle guideLines;
    public UISliderTrackpad guideThickness;
    public UISliderTrackpad guideOpacity;

    private UIReplayList list;

    /** The replay whose values the fields show; writes still go to the whole selection. */
    private Replay replay;

    /**
     * Bind a field to the shown replay: the read runs on every frame the field is drawn, so the
     * panel stops showing what the replay held when it was last selected.
     */
    private <T extends UIElement> T bound(T element, Consumer<Replay> read)
    {
        element.valueBinding(() ->
        {
            if (this.replay != null)
            {
                read.accept(this.replay);
            }
        });

        return element;
    }

    public UIReplayPropertiesPanel(UIFilmPanel filmPanel)
    {
        this.filmPanel = filmPanel;

        this.pickEdit = new UINestedEdit((editing) ->
        {
            if (this.list == null)
            {
                return;
            }

            Replay r = this.list.getSelectedReplayFirst();

            if (r != null)
            {
                this.list.openFormEditor(r.form, editing, this.pickEdit::setForm);
            }
        });
        this.pickEdit.pick.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PICK_FORM);
        this.pickEdit.edit.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_EDIT_FORM);
        this.enabled = this.bound(new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) ->
        {
            this.edit((replay) -> replay.enabled.set(b.getValue()));
            filmPanel.getController().createEntities();
        }), (r) -> this.enabled.setValue(r.enabled.get()));
        this.label = this.bound(new UITextbox(1000, (s) -> this.edit((replay) -> replay.label.set(s))), (r) -> this.label.setText(r.label.get()));
        this.label.textbox.setPlaceholder(UIKeys.FILM_REPLAY_LABEL);
        this.nameTag = this.bound(new UITextbox(1000, (s) -> this.edit((replay) -> replay.nameTag.set(s))), (r) -> this.nameTag.setText(r.nameTag.get()));
        this.nameTag.textbox.setPlaceholder(UIKeys.FILM_REPLAY_NAME_TAG);
        this.shadow = this.bound(new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) -> this.edit((replay) -> replay.shadow.set(b.getValue()))), (r) -> this.shadow.setValue(r.shadow.get()));
        this.shadowSize = this.bound(new UITrackpad((v) -> this.edit((replay) -> replay.shadowSize.set(v.floatValue()))), (r) -> this.shadowSize.setValue(r.shadowSize.get()));
        this.shadowSize.tooltip(UIKeys.FILM_REPLAY_SHADOW_SIZE);
        this.shadowFollow = this.bound(new UIToggle(UIKeys.FILM_REPLAY_SHADOW_FOLLOW, (b) -> this.edit((replay) -> replay.shadowFollow.set(b.getValue()))), (r) -> this.shadowFollow.setValue(r.shadowFollow.get()));
        this.shadowFollow.tooltip(UIKeys.FILM_REPLAY_SHADOW_FOLLOW_TOOLTIP);
        this.shadowOffsetX = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.shadowOffset, (value) -> value.get().x = v))), (r) -> this.shadowOffsetX.setValue(r.shadowOffset.get().x));
        this.shadowOffsetX.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET);
        this.shadowOffsetY = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.shadowOffset, (value) -> value.get().y = v))), (r) -> this.shadowOffsetY.setValue(r.shadowOffset.get().y));
        this.shadowOffsetY.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET);
        this.shadowOffsetZ = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.shadowOffset, (value) -> value.get().z = v))), (r) -> this.shadowOffsetZ.setValue(r.shadowOffset.get().z));
        this.shadowOffsetZ.tooltip(UIKeys.FILM_REPLAY_SHADOW_OFFSET);
        this.looping = this.bound(new UITrackpad((v) -> this.edit((replay) -> replay.looping.set(v.intValue()))), (r) -> this.looping.setValue(r.looping.get()));
        this.looping.limit(0).integer().tooltip(UIKeys.FILM_REPLAY_LOOPING_TOOLTIP);
        this.actor = this.bound(new UIToggle(UIKeys.FILM_REPLAY_ACTOR, (b) -> this.edit((replay) -> replay.actor.set(b.getValue()))), (r) -> this.actor.setValue(r.actor.get()));
        this.actor.tooltip(UIKeys.FILM_REPLAY_ACTOR_TOOLTIP);
        this.actorPickup = this.bound(new UIToggle(UIKeys.FILM_REPLAY_ACTOR_PICKUP, (b) -> this.edit((replay) -> replay.actorPickup.set(b.getValue()))), (r) -> this.actorPickup.setValue(r.actorPickup.get()));
        this.actorPickup.tooltip(UIKeys.FILM_REPLAY_ACTOR_PICKUP_TOOLTIP);
        this.dropVelocityMinX = this.bound(new UITrackpad((v) -> this.edit((replay) -> replay.dropVelocityMinX.set(v.floatValue()))), (r) -> this.dropVelocityMinX.setValue(r.dropVelocityMinX.get()));
        this.dropVelocityMinX.tooltip(UIKeys.FILM_REPLAY_DROP_VELOCITY_MIN_X);
        this.dropVelocityMaxX = this.bound(new UITrackpad((v) -> this.edit((replay) -> replay.dropVelocityMaxX.set(v.floatValue()))), (r) -> this.dropVelocityMaxX.setValue(r.dropVelocityMaxX.get()));
        this.dropVelocityMaxX.tooltip(UIKeys.FILM_REPLAY_DROP_VELOCITY_MAX_X);

        this.dropVelocityMinY = this.bound(new UITrackpad((v) -> this.edit((replay) -> replay.dropVelocityMinY.set(v.floatValue()))), (r) -> this.dropVelocityMinY.setValue(r.dropVelocityMinY.get()));
        this.dropVelocityMinY.tooltip(UIKeys.FILM_REPLAY_DROP_VELOCITY_MIN_Y);
        this.dropVelocityMaxY = this.bound(new UITrackpad((v) -> this.edit((replay) -> replay.dropVelocityMaxY.set(v.floatValue()))), (r) -> this.dropVelocityMaxY.setValue(r.dropVelocityMaxY.get()));
        this.dropVelocityMaxY.tooltip(UIKeys.FILM_REPLAY_DROP_VELOCITY_MAX_Y);

        this.dropVelocityMinZ = this.bound(new UITrackpad((v) -> this.edit((replay) -> replay.dropVelocityMinZ.set(v.floatValue()))), (r) -> this.dropVelocityMinZ.setValue(r.dropVelocityMinZ.get()));
        this.dropVelocityMinZ.tooltip(UIKeys.FILM_REPLAY_DROP_VELOCITY_MIN_Z);
        this.dropVelocityMaxZ = this.bound(new UITrackpad((v) -> this.edit((replay) -> replay.dropVelocityMaxZ.set(v.floatValue()))), (r) -> this.dropVelocityMaxZ.setValue(r.dropVelocityMaxZ.get()));
        this.dropVelocityMaxZ.tooltip(UIKeys.FILM_REPLAY_DROP_VELOCITY_MAX_Z);

        this.dropVelocityGroup = UI.column(5,
            UI.label(UIKeys.FILM_REPLAY_DROP_VELOCITY),
            UI.row(this.dropVelocityMinX, this.dropVelocityMaxX),
            UI.row(this.dropVelocityMinY, this.dropVelocityMaxY),
            UI.row(this.dropVelocityMinZ, this.dropVelocityMaxZ)
        );

        this.dropItemsOnDeath = this.bound(new UIToggle(UIKeys.FILM_REPLAY_DROP_ITEMS_ON_DEATH, (b) ->
        {
            this.edit((replay) -> replay.dropItemsOnDeath.set(b.getValue()));
            this.dropVelocityGroup.setVisible(b.getValue());
            this.properties.resize();
        }), (r) ->
        {
            this.dropItemsOnDeath.setValue(r.dropItemsOnDeath.get());
            boolean visible = r.dropItemsOnDeath.get();
            if (this.dropVelocityGroup.isVisible() != visible)
            {
                this.dropVelocityGroup.setVisible(visible);
                this.properties.resize();
            }
        });
        this.dropItemsOnDeath.tooltip(UIKeys.FILM_REPLAY_DROP_ITEMS_ON_DEATH_TOOLTIP);
        this.fp = new UIToggle(UIKeys.FILM_REPLAY_FP, (b) ->
        {
            if (filmPanel.getData() != null)
            {
                for (Replay replay : filmPanel.getData().replays.getList())
                {
                    if (replay.fp.get())
                    {
                        replay.fp.set(false);
                    }
                }
            }

            Replay first = this.list == null ? null : this.list.getSelectedReplayFirst();

            if (first != null)
            {
                first.fp.set(b.getValue());
            }
        });
        this.bound(this.fp, (r) -> this.fp.setValue(r.fp.get()));
        this.relative = this.bound(new UIToggle(UIKeys.CAMERA_PANELS_RELATIVE, (b) -> this.edit((replay) -> replay.relative.set(b.getValue()))), (r) -> this.relative.setValue(r.relative.get()));
        this.relative.tooltip(UIKeys.FILM_REPLAY_RELATIVE_TOOLTIP);
        this.relativeOffsetX = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().x = v))), (r) -> this.relativeOffsetX.setValue(r.relativeOffset.get().x));
        this.relativeOffsetY = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().y = v))), (r) -> this.relativeOffsetY.setValue(r.relativeOffset.get().y));
        this.relativeOffsetZ = this.bound(new UITrackpad((v) -> this.edit((replay) -> BaseValue.edit(replay.relativeOffset, (value) -> value.get().z = v))), (r) -> this.relativeOffsetZ.setValue(r.relativeOffset.get().z));
        this.axesPreview = this.bound(new UIToggle(UIKeys.FILM_REPLAY_AXES_PREVIEW, (b) -> this.edit((replay) -> replay.axesPreview.set(b.getValue()))), (r) -> this.axesPreview.setValue(r.axesPreview.get()));
        this.pickAxesPreviewBone = new UIButton(UIKeys.FILM_REPLAY_PICK_AXES_PREVIEW, (b) ->
        {
            Replay replay = filmPanel.replayEditor.getReplay();

            if (replay != null && filmPanel.getData() != null)
            {
                UIAnchorKeyframeFactory.displayAttachments(filmPanel, replay.getId(), replay.axesPreviewBone.get(), (s) ->
                {
                    this.edit((r) -> r.axesPreviewBone.set(s));
                });
            }
        });

        UISection shadowSection = new UISection(UIKeys.FILM_REPLAY_SHADOW);

        shadowSection.fields.add(
            this.shadow, this.shadowSize,
            this.shadowFollow, UI.row(this.shadowOffsetX, this.shadowOffsetY, this.shadowOffsetZ)
        );

        UISection other = new UISection(UIKeys.FILM_REPLAY_SECTION_OTHER);

        other.fields.add(
            this.looping, this.actor, this.actorPickup, this.dropItemsOnDeath, this.dropVelocityGroup, this.fp,
            this.relative, UI.row(this.relativeOffsetX, this.relativeOffsetY, this.relativeOffsetZ),
            this.axesPreview, this.pickAxesPreviewBone
        );

        shadowSection.setExpanded(false);
        other.setExpanded(false);

        this.guideLines = new UIToggle(UIKeys.FILM_REPLAY_GUIDES_ENABLED, (b) -> BBSSettings.replayGuideLines.set(b.getValue()));
        this.guideLines.valueBinding(() -> this.guideLines.setValue(BBSSettings.replayGuideLines.get()));
        this.guideLines.tooltip(UIKeys.FILM_REPLAY_GUIDES_ENABLED_TOOLTIP);

        this.guideThickness = new UISliderTrackpad((v) -> BBSSettings.replayGuideThickness.set(v.intValue()));
        this.guideThickness.limit(1, 10, true).integer();
        this.guideThickness.valueBinding(() -> this.guideThickness.setValue(BBSSettings.replayGuideThickness.get()));
        this.guideThickness.tooltip(UIKeys.FILM_REPLAY_GUIDES_THICKNESS_TOOLTIP);

        this.guideOpacity = new UISliderTrackpad((v) -> BBSSettings.replayGuideOpacity.set(v.floatValue()));
        this.guideOpacity.limit(0, 1).increment(0.01D);
        this.guideOpacity.valueBinding(() -> this.guideOpacity.setValue(BBSSettings.replayGuideOpacity.get()));
        this.guideOpacity.tooltip(UIKeys.FILM_REPLAY_GUIDES_OPACITY_TOOLTIP);

        UISection guidesSection = new UISection(UIKeys.FILM_REPLAY_GUIDES_TITLE);

        guidesSection.fields.add(
            this.guideLines,
            UI.labelRow(UIKeys.FILM_REPLAY_GUIDES_THICKNESS, this.guideThickness),
            UI.labelRow(UIKeys.FILM_REPLAY_GUIDES_OPACITY, this.guideOpacity)
        );
        guidesSection.setExpanded(false);

        this.properties = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING,
            this.pickEdit, this.enabled, this.label, this.nameTag,
            shadowSection,
            guidesSection,
            other
        );
        this.properties.relative(this).x(0).y(0).w(1F).h(1F);

        for (var factory : ACTIONS)
        {
            UIElement action = factory.apply(this.filmPanel, () -> this.replay);

            if (action != null) this.properties.add(action);
        }

        this.add(this.properties);
        this.setReplay(null);
    }

    public void attachReplayList(UIReplayList list)
    {
        this.list = list;
    }

    public Consumer<Form> getFormConsumer()
    {
        return this.pickEdit::setForm;
    }

    private void edit(Consumer<Replay> consumer)
    {
        if (consumer != null && this.list != null)
        {
            for (Replay replay : this.list.getSelectedReplays())
            {
                consumer.accept(replay);
            }
        }
    }

    public void setReplay(Replay replay)
    {
        this.replay = replay;
        this.properties.setVisible(replay != null);

        if (replay != null)
        {
            this.pickEdit.setForm(replay.form.get());
            this.dropVelocityGroup.setVisible(replay.dropItemsOnDeath.get());
        }
    }
}
