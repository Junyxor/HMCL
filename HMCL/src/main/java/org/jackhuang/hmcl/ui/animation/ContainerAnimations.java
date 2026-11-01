// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.animation;

import javafx.animation.*;
import javafx.beans.property.DoubleProperty;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;

public enum ContainerAnimations implements TransitionPane.AnimationProducer {
    NONE {
        @Override
        public void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, false);
            AnimationUtils.reset(nextNode, true);
        }

        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            return new Timeline();
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return this;
        }
    },

    /**
     * A fade between the old and new view
     */
    FADE {
        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            return new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(previousNode.opacityProperty(), 1, interpolator),
                            new KeyValue(nextNode.opacityProperty(), 0, interpolator)),
                    new KeyFrame(duration,
                            new KeyValue(previousNode.opacityProperty(), 0, interpolator),
                            new KeyValue(nextNode.opacityProperty(), 1, interpolator)));
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return this;
        }
    },

    /**
     * A swipe effect
     */
    SWIPE_LEFT {
        @Override
        public void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, true);
            AnimationUtils.reset(nextNode, true);
            nextNode.setTranslateX(container.getWidth());
        }

        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            return new Timeline(new KeyFrame(Duration.ZERO,
                    new KeyValue(nextNode.translateXProperty(), container.getWidth(), interpolator),
                    new KeyValue(previousNode.translateXProperty(), 0, interpolator)),
                    new KeyFrame(duration,
                            new KeyValue(nextNode.translateXProperty(), 0, interpolator),
                            new KeyValue(previousNode.translateXProperty(), -container.getWidth(), interpolator)));
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return SWIPE_RIGHT;
        }
    },

    /**
     * A swipe effect
     */
    SWIPE_RIGHT {
        @Override
        public void init(TransitionPane container, Node previousNode, Node nextNode) {
            AnimationUtils.reset(previousNode, true);
            AnimationUtils.reset(nextNode, true);
            nextNode.setTranslateX(-container.getWidth());
        }

        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            return new Timeline(new KeyFrame(Duration.ZERO,
                    new KeyValue(nextNode.translateXProperty(), -container.getWidth(), interpolator),
                    new KeyValue(previousNode.translateXProperty(), 0, interpolator)),
                    new KeyFrame(duration,
                            new KeyValue(nextNode.translateXProperty(), 0, interpolator),
                            new KeyValue(previousNode.translateXProperty(), container.getWidth(), interpolator)));
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return SWIPE_LEFT;
        }
    },

    /// A Material 3 shared-axis transition along X: the old page slides left and fades out quickly,
    /// then the new page slides in from the right and settles using the supplied interpolator.
    ///
    /// @see <a href="https://m3.material.io/styles/motion/transitions/transition-patterns">Transitions - Material Design 3</a>
    FORWARD {
        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            return sharedAxis(previousNode, nextNode, axisOffset(container.getWidth()), true, duration, interpolator);
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return BACKWARD;
        }
    },

    /// The reverse of [#FORWARD]: the old page slides right and the new page comes in from the left.
    ///
    /// @see <a href="https://m3.material.io/styles/motion/transitions/transition-patterns">Transitions - Material Design 3</a>
    BACKWARD {
        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            return sharedAxis(previousNode, nextNode, -axisOffset(container.getWidth()), true, duration, interpolator);
        }

        @Override
        public TransitionPane.AnimationProducer opposite() {
            return FORWARD;
        }
    },

    /// A shared-axis transition along Y, used for tab switching: the old tab fades out quickly while the
    /// new one rises a short distance into place. Similar to the Windows 11 Settings tab animation.
    SLIDE_UP_FADE_IN {
        @Override
        public Timeline animate(
                Pane container, Node previousNode, Node nextNode,
                Duration duration, Interpolator interpolator) {
            return sharedAxis(previousNode, nextNode, axisOffset(container.getHeight()), false, duration, interpolator);
        }
    },

    /// Fades the old page out, then fades the new page in while its panels slide back into place
    /// and its content grows from [#NAVIGATION_INITIAL_SCALE] to full size (a Material "fade through").
    NAVIGATION {
        /// Also clears the horizontal offsets of the target page's navigation panels and the scale of its content.
        @Override
        public void finish(Node nextNode) {
            super.finish(nextNode);
            if (nextNode instanceof DecoratorAnimatedPage nextPage) {
                nextPage.getLeft().setTranslateX(0);
                nextPage.getCenter().setTranslateX(0);
                nextPage.getCenter().setScaleX(1);
                nextPage.getCenter().setScaleY(1);
            }
        }

        @Override
        public Animation animate(Pane container, Node previousNode, Node nextNode, Duration duration, Interpolator interpolator) {
            Timeline timeline = new Timeline();
            // The old page leaves quickly so the new one gets most of the time to settle (Material "fade through");
            // the new page starts a little before the old one is gone so no frame shows an empty pane.
            Duration exitEnd = duration.multiply(EXIT_FRACTION);
            Duration enterStart = duration.multiply(ENTER_FRACTION);

            timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                    new KeyValue(previousNode.opacityProperty(), 1, EXIT_INTERPOLATOR)));
            timeline.getKeyFrames().add(new KeyFrame(exitEnd,
                    new KeyValue(previousNode.opacityProperty(), 0, EXIT_INTERPOLATOR)));
            if (previousNode instanceof DecoratorAnimatedPage prevPage) {
                Node left = prevPage.getLeft();
                Node center = prevPage.getCenter();

                timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                        new KeyValue(left.translateXProperty(), 0, EXIT_INTERPOLATOR),
                        new KeyValue(center.translateXProperty(), 0, EXIT_INTERPOLATOR)));
                timeline.getKeyFrames().add(new KeyFrame(exitEnd,
                        new KeyValue(left.translateXProperty(), -NAVIGATION_OFFSET, EXIT_INTERPOLATOR),
                        new KeyValue(center.translateXProperty(), NAVIGATION_OFFSET, EXIT_INTERPOLATOR)));
            }

            timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                    new KeyValue(nextNode.opacityProperty(), 0, interpolator)));
            timeline.getKeyFrames().add(new KeyFrame(enterStart,
                    new KeyValue(nextNode.opacityProperty(), 0, interpolator)));
            timeline.getKeyFrames().add(new KeyFrame(duration,
                    new KeyValue(nextNode.opacityProperty(), 1, interpolator)));

            // Only the content area grows in, so the navigation sidebar keeps its size.
            Node scaledNode = nextNode;
            if (nextNode instanceof DecoratorAnimatedPage nextPage) {
                Node left = nextPage.getLeft();
                Node center = nextPage.getCenter();
                scaledNode = center;

                timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                        new KeyValue(left.translateXProperty(), -NAVIGATION_OFFSET, interpolator),
                        new KeyValue(center.translateXProperty(), NAVIGATION_OFFSET, interpolator)));
                timeline.getKeyFrames().add(new KeyFrame(enterStart,
                        new KeyValue(left.translateXProperty(), -NAVIGATION_OFFSET, interpolator),
                        new KeyValue(center.translateXProperty(), NAVIGATION_OFFSET, interpolator)));
                timeline.getKeyFrames().add(new KeyFrame(duration,
                        new KeyValue(left.translateXProperty(), 0, interpolator),
                        new KeyValue(center.translateXProperty(), 0, interpolator)));
            }

            timeline.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                    new KeyValue(scaledNode.scaleXProperty(), NAVIGATION_INITIAL_SCALE, interpolator),
                    new KeyValue(scaledNode.scaleYProperty(), NAVIGATION_INITIAL_SCALE, interpolator)));
            timeline.getKeyFrames().add(new KeyFrame(enterStart,
                    new KeyValue(scaledNode.scaleXProperty(), NAVIGATION_INITIAL_SCALE, interpolator),
                    new KeyValue(scaledNode.scaleYProperty(), NAVIGATION_INITIAL_SCALE, interpolator)));
            timeline.getKeyFrames().add(new KeyFrame(duration,
                    new KeyValue(scaledNode.scaleXProperty(), 1, interpolator),
                    new KeyValue(scaledNode.scaleYProperty(), 1, interpolator)));

            return timeline;
        }
    },
    ;

    /// The scale at which the incoming page's content starts growing during [#NAVIGATION].
    private static final double NAVIGATION_INITIAL_SCALE = 0.96;

    /// The horizontal distance, in pixels, that the navigation panels travel during [#NAVIGATION].
    private static final double NAVIGATION_OFFSET = 30;

    /// The fraction of a transition's duration spent fading the old page out.
    private static final double EXIT_FRACTION = 0.35;

    /// The fraction of a transition's duration after which the new page starts entering.
    ///
    /// It is a bit earlier than [#EXIT_FRACTION], giving a short overlap that avoids a blank frame.
    private static final double ENTER_FRACTION = 0.25;

    /// The easing curve of the old page's exit; it starts gently and then speeds up as the page disappears.
    private static final Interpolator EXIT_INTERPOLATOR = Motion.STANDARD_ACCELERATE;

    /// Returns the shared-axis travel distance for a container whose size along the axis is `extent`.
    ///
    /// Material 3 uses a short, fixed travel instead of a whole-page slide; the distance is a small
    /// fraction of the container, clamped so it stays visible but never feels like a long swipe.
    ///
    /// @param extent the container width or height in pixels, possibly `0` before layout
    /// @return the travel distance in pixels, always positive
    private static double axisOffset(double extent) {
        return Math.max(24, Math.min(extent * 0.06, 56));
    }

    /// Builds a shared-axis transition.
    ///
    /// The old page moves by `-offset` and fades out during the first [#EXIT_FRACTION] of the duration.
    /// The new page starts `offset` away and, from [#ENTER_FRACTION] on, fades in and moves
    /// into place with `interpolator`.
    ///
    /// @param previousNode the page being hidden
    /// @param nextNode     the page being shown
    /// @param offset       the signed travel distance; positive moves content toward the left or the top
    /// @param horizontal   `true` to move along X, `false` to move along Y
    /// @param duration     the total duration
    /// @param interpolator the easing curve of the new page's entrance
    /// @return the unstarted timeline
    private static Timeline sharedAxis(Node previousNode, Node nextNode, double offset, boolean horizontal,
                                       Duration duration, Interpolator interpolator) {
        DoubleProperty previousTranslate = horizontal ? previousNode.translateXProperty() : previousNode.translateYProperty();
        DoubleProperty nextTranslate = horizontal ? nextNode.translateXProperty() : nextNode.translateYProperty();
        Duration exitEnd = duration.multiply(EXIT_FRACTION);
        Duration enterStart = duration.multiply(ENTER_FRACTION);

        return new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(previousTranslate, 0, EXIT_INTERPOLATOR),
                        new KeyValue(previousNode.opacityProperty(), 1, EXIT_INTERPOLATOR),
                        new KeyValue(nextTranslate, offset, interpolator),
                        new KeyValue(nextNode.opacityProperty(), 0, interpolator)),
                new KeyFrame(enterStart,
                        new KeyValue(nextTranslate, offset, interpolator),
                        new KeyValue(nextNode.opacityProperty(), 0, interpolator)),
                new KeyFrame(exitEnd,
                        new KeyValue(previousTranslate, -offset, EXIT_INTERPOLATOR),
                        new KeyValue(previousNode.opacityProperty(), 0, EXIT_INTERPOLATOR)),
                new KeyFrame(duration,
                        new KeyValue(nextTranslate, 0, interpolator),
                        new KeyValue(nextNode.opacityProperty(), 1, interpolator)));
    }
}
