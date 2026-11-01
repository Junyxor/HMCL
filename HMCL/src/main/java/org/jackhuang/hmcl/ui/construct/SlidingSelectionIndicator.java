// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.utils.JFXNodeUtils;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// A single highlight that slides between the active items of an [AdvancedListBox].
///
/// The tracked items stop drawing their own selection background (through the `sliding-selection`
/// style class), and this indicator draws it instead on a layer behind the item container. When the
/// active item changes, the highlight glides from the old item to the new one, following the new
/// item's live bounds so it stays aligned even if the layout changes mid-animation.
///
/// All methods must be called on the JavaFX Application Thread.
@NotNullByDefault
final class SlidingSelectionIndicator {
    /// Style class that disables an item's own selection background in favor of this indicator.
    static final String TRACKED_STYLE_CLASS = "sliding-selection";

    /// The container holding the items, whose coordinate space matches [#layer].
    private final VBox container;

    /// The mouse-transparent layer placed behind [#container] that holds [#indicator].
    private final Pane layer = new Pane();

    /// The highlight node, positioned manually and styled by `.advanced-list-box-indicator`.
    private final Region indicator = new Region();

    /// The items whose active state drives the indicator, in insertion order.
    private final List<AdvancedListItem> items = new ArrayList<>();

    /// Slide progress from the start geometry (`0`) to the target item (`1`).
    private final DoubleProperty progress = new SimpleDoubleProperty(1);

    /// Repositions the indicator whenever the target item's bounds change.
    private final ChangeListener<Bounds> targetBoundsListener = (observable, oldValue, newValue) -> update();

    /// The item currently highlighted, or `null` when no tracked item is active.
    private @Nullable AdvancedListItem target;

    /// The indicator's Y position when the current slide started.
    private double fromY;

    /// The indicator's height when the current slide started.
    private double fromHeight;

    /// The running slide animation, or `null` when the indicator is settled.
    private @Nullable Timeline slide;

    /// The running fade animation, or `null` when the indicator's opacity is settled.
    private @Nullable Timeline fade;

    /// The scene on which [#pendingStart] is registered, or `null` when no slide is waiting to start.
    private @Nullable Scene pendingScene;

    /// The one-shot post-layout listener that starts [#slide], or `null` when none is registered.
    private @Nullable Runnable pendingStart;

    /// Whether a [#retarget()] call is already queued on the FX thread.
    private boolean retargetQueued;

    /// Creates an indicator for items placed in `container`.
    ///
    /// The caller must place [#getLayer()] directly behind `container`, at the same position and size.
    ///
    /// @param container the container that holds the tracked items
    SlidingSelectionIndicator(VBox container) {
        this.container = container;
        layer.setMouseTransparent(true);
        indicator.getStyleClass().add("advanced-list-box-indicator");
        indicator.setManaged(false);
        indicator.setOpacity(0);
        layer.getChildren().add(indicator);
        progress.addListener(observable -> update());
    }

    /// Returns the layer that must be placed directly behind the item container.
    Pane getLayer() {
        return layer;
    }

    /// Starts highlighting `item` with this indicator whenever it is active.
    ///
    /// @param item an item that is, or will be, a direct child of the container
    void track(AdvancedListItem item) {
        item.getStyleClass().add(TRACKED_STYLE_CLASS);
        items.add(item);
        item.activeProperty().addListener(observable -> requestRetarget());
        // Removing or hiding the item must also move the highlight away from it.
        item.parentProperty().addListener(observable -> requestRetarget());
        item.visibleProperty().addListener(observable -> requestRetarget());
        retarget();
    }

    /// Queues one [#retarget()] call for after the current event has been handled.
    ///
    /// Switching tabs deactivates the old item and activates the new one in two separate
    /// notifications. Handling them together lets the highlight slide from the old item to the new
    /// one instead of disappearing in between and reappearing without motion.
    private void requestRetarget() {
        if (retargetQueued) {
            return;
        }
        retargetQueued = true;
        Platform.runLater(() -> {
            retargetQueued = false;
            retarget();
        });
    }

    /// Picks the first active, visible, attached item and moves the highlight to it.
    private void retarget() {
        @Nullable AdvancedListItem next = null;
        for (AdvancedListItem item : items) {
            if (item.isActive() && item.isVisible() && item.getParent() == container) {
                next = item;
                break;
            }
        }
        if (next == target) {
            return;
        }

        boolean wasShown = target != null && indicator.getOpacity() > 0 && indicator.getHeight() > 0;
        double startY = indicator.getLayoutY();
        double startHeight = indicator.getHeight();

        if (target != null) {
            target.boundsInParentProperty().removeListener(targetBoundsListener);
        }
        target = next;
        if (next != null) {
            next.boundsInParentProperty().addListener(targetBoundsListener);
        }

        cancelPendingStart();
        if (slide != null) {
            slide.stop();
            slide = null;
        }

        boolean animate = AnimationUtils.isAnimationEnabled() && JFXNodeUtils.isTreeShowing(container);
        if (next == null) {
            fadeTo(0, animate);
            return;
        }

        if (wasShown && animate) {
            // Glide from wherever the highlight is drawn now, even if a previous slide was interrupted.
            fromY = startY;
            fromHeight = startHeight;
            progress.set(0);
            Timeline timeline = new Timeline(new KeyFrame(Motion.PAGE_TRANSITION_DURATION,
                    new KeyValue(progress, 1, Motion.PAGE_TRANSITION_INTERPOLATOR)));
            timeline.setOnFinished(event -> {
                if (slide == timeline) {
                    slide = null;
                }
            });
            slide = timeline;
            playAfterLayout(timeline);
        } else {
            progress.set(1);
            update();
        }
        fadeTo(1, animate && !wasShown);
    }

    /// Plays `timeline` once the scene has finished the CSS and layout pass for the new selection.
    ///
    /// Selecting a tab may build and lay out a whole page, which blocks the FX thread for a while.
    /// Starting the slide only after that pass, as [org.jackhuang.hmcl.ui.animation.TransitionPane]
    /// does for page content, keeps the first frames from being skipped and makes the highlight move
    /// together with the page.
    ///
    /// @param timeline the slide to play; it is dropped if another slide replaces it meanwhile
    private void playAfterLayout(Timeline timeline) {
        @Nullable Scene scene = container.getScene();
        if (scene == null) {
            timeline.play();
            return;
        }
        Runnable listener = new Runnable() {
            @Override
            public void run() {
                scene.removePostLayoutPulseListener(this);
                if (pendingStart == this) {
                    pendingStart = null;
                    pendingScene = null;
                }
                // Leave the pulse before starting, so the clock begins after the heavy frame is rendered.
                Platform.runLater(() -> {
                    if (slide == timeline) {
                        timeline.play();
                    }
                });
            }
        };
        pendingScene = scene;
        pendingStart = listener;
        scene.addPostLayoutPulseListener(listener);
        Platform.requestNextPulse();
    }

    /// Unregisters a slide start that has not fired yet.
    private void cancelPendingStart() {
        if (pendingScene != null && pendingStart != null) {
            pendingScene.removePostLayoutPulseListener(pendingStart);
        }
        pendingScene = null;
        pendingStart = null;
    }

    /// Fades the indicator to `target` opacity, or sets it at once when `animate` is `false`.
    ///
    /// @param targetOpacity the opacity to reach
    /// @param animate       whether to animate the change
    private void fadeTo(double targetOpacity, boolean animate) {
        if (fade != null) {
            fade.stop();
            fade = null;
        }
        if (!animate || indicator.getOpacity() == targetOpacity) {
            indicator.setOpacity(targetOpacity);
            return;
        }
        Timeline timeline = new Timeline(new KeyFrame(Motion.SHORT4,
                new KeyValue(indicator.opacityProperty(), targetOpacity, Motion.STANDARD)));
        timeline.setOnFinished(event -> {
            if (fade == timeline) {
                fade = null;
            }
        });
        fade = timeline;
        timeline.play();
    }

    /// Places the indicator between the slide's start geometry and the target item's content area.
    private void update() {
        @Nullable AdvancedListItem item = target;
        if (item == null) {
            return;
        }
        Node parent = item.getParent();
        if (parent != container) {
            return;
        }

        // The highlight covers the item's content area, i.e. inside its padding, matching its hover feedback.
        double x = container.getLayoutX() + item.getLayoutX() + item.snappedLeftInset();
        double width = Math.max(0, item.getWidth() - item.snappedLeftInset() - item.snappedRightInset());
        double targetY = container.getLayoutY() + item.getLayoutY() + item.snappedTopInset();
        double targetHeight = Math.max(0, item.getHeight() - item.snappedTopInset() - item.snappedBottomInset());

        double p = progress.get();
        double y = fromY + (targetY - fromY) * p;
        double height = fromHeight + (targetHeight - fromHeight) * p;
        indicator.resizeRelocate(x, y, width, height);
    }
}
