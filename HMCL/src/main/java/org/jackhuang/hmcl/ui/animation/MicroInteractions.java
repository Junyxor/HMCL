// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.animation;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Small pointer-driven animations that make interactive surfaces feel responsive.
///
/// The effects only change the node's X/Y scale, so they never affect layout. When animations
/// are disabled in the launcher settings, every installer in this class does nothing.
@NotNullByDefault
public final class MicroInteractions {

    /// Key in [Node#getProperties()] marking a node that already has a hover/press scale installed.
    private static final String INSTALLED_KEY = MicroInteractions.class.getName() + ".hoverPressScale";

    /// Prevents instantiation of this utility class.
    private MicroInteractions() {
    }

    /// Makes `node` grow slightly while hovered and shrink slightly while pressed.
    ///
    /// Hovering scales the node to `hoverScale` and pressing scales it to `pressedScale`. Releasing
    /// the mouse springs back to the hover scale with a small overshoot, and leaving the node returns
    /// it to its normal size. Calling this method again on the same node has no effect.
    ///
    /// @param node         the node to animate
    /// @param hoverScale   the scale used while the pointer is over the node, typically a bit above `1`
    /// @param pressedScale the scale used while the primary button is held, typically a bit below `1`
    public static void installHoverPressScale(Node node, double hoverScale, double pressedScale) {
        if (!AnimationUtils.isAnimationEnabled() || node.getProperties().containsKey(INSTALLED_KEY)) {
            return;
        }
        node.getProperties().put(INSTALLED_KEY, Boolean.TRUE);

        ScaleAnimator animator = new ScaleAnimator(node);
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> animator.animateTo(hoverScale, Motion.SHORT4, Motion.EMPHASIZED_DECELERATE));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> animator.animateTo(1, Motion.SHORT4, Motion.EMPHASIZED_DECELERATE));
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> animator.animateTo(pressedScale, Motion.SHORT2, Motion.STANDARD));
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, e ->
                // The release may happen outside the node, in which case it should settle at rest size.
                animator.animateTo(node.isHover() ? hoverScale : 1, Motion.MEDIUM2, Motion.EASE_OUT_BACK));
    }

    /// Drives a single node's scale, interrupting any running scale animation with the new target.
    private static final class ScaleAnimator {
        /// The node whose scale is animated.
        private final Node node;

        /// The currently running animation, or `null` when the node is at rest.
        private @Nullable Timeline timeline;

        /// Creates an animator for the given node.
        ///
        /// @param node the node whose scale is animated
        ScaleAnimator(Node node) {
            this.node = node;
        }

        /// Animates the node's X and Y scale from their current values to `target`.
        ///
        /// @param target       the scale to reach
        /// @param duration     the animation length
        /// @param interpolator the easing curve
        void animateTo(double target, Duration duration, Interpolator interpolator) {
            if (timeline != null) {
                timeline.stop();
            }
            // Starting from the current scale keeps the motion continuous when events arrive mid-animation.
            Timeline next = new Timeline(new KeyFrame(duration,
                    new KeyValue(node.scaleXProperty(), target, interpolator),
                    new KeyValue(node.scaleYProperty(), target, interpolator)));
            next.setOnFinished(e -> {
                if (timeline == next) {
                    timeline = null;
                }
            });
            timeline = next;
            next.play();
        }
    }
}
