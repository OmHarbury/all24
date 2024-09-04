# pylint: disable=wrong-import-position,invalid-name,too-many-statements,no-name-in-module,no-member,missing-class-docstring,missing-function-docstring,missing-module-docstring,too-few-public-methods,global-statement


import math
from typing import Iterator
import gtsam  # type:ignore
import matplotlib  # type:ignore

matplotlib.use("Qt5Agg", force=True)
import matplotlib.pyplot as plt  # type:ignore
from matplotlib.markers import MarkerStyle  # type:ignore
from matplotlib import collections
import numpy as np
from gtsam.symbol_shorthand import X  # type:ignore
from landmark import Landmark
from custom_factor_type import CustomFactorType


NUM_DRAWS = 50
TAG_SCALE = 0.1
POSE = 0.1
RNG = np.random.default_rng(0)


LANDMARK_MARK = np.array(
    [
        [-TAG_SCALE, -TAG_SCALE],
        [TAG_SCALE, -TAG_SCALE],
        [TAG_SCALE, TAG_SCALE],
        [-TAG_SCALE, TAG_SCALE],
    ]
)

POSE_MARK = np.array(
    [
        [-POSE, POSE],
        [-POSE / 2, 0],
        [-POSE, -POSE],
        [POSE, 0],
    ]
)


def rot(theta):
    return np.array(
        [
            [np.cos(theta), np.sin(theta)],
            [-np.sin(theta), np.cos(theta)],
        ]
    )


def make_mark(mark, scale, rot_matrix, c):
    return (mark * scale).dot(rot_matrix) + c


class Plot:
    @staticmethod
    def subplots(x, y, sx, sy):
        return plt.subplots(x, y, figsize=(sx, sy))

    def __init__(self, isam, name, fig, ax) -> None:
        self.isam = isam
        self.fig = fig
        self.ax = ax
        self.ax.set_title(name)
        self.ax.set_xlim(-1, 6)
        self.ax.set_ylim(-1, 6)
        self.ax.set_aspect("equal", adjustable="box")
        plt.tight_layout()

        self.vline = self.ax.axvline(4.5, 0, 1, lw=10, alpha=0.2, color="blue")

        # the order the collections are added to the axis
        # is the order they are drawn below, so put the
        # mean after the distribution.
        self.pose_point_poly = collections.PolyCollection(
            [],
            facecolors="none",
            edgecolors="orange",
            alpha=0.5,
        )
        self.ax.add_collection(self.pose_point_poly)
        self.pose_mean_poly = collections.PolyCollection(
            [],
            facecolors="none",
            edgecolors="black",
        )
        self.ax.add_collection(self.pose_mean_poly)

        self.landmark_point_scatter = self.ax.scatter(
            [],
            [],
            marker=MarkerStyle("x"),
            s=5,
            alpha=0.5,
            color="royalblue",
        )
        self.landmark_mean_poly = collections.PolyCollection(
            [],
            facecolors="none",
            edgecolors="black",
        )
        self.ax.add_collection(self.landmark_mean_poly)

        self.sights = collections.PathCollection(
            [],
            edgecolors="green",
        )
        self.ax.add_collection(self.sights)

        self.odometry = collections.PathCollection(
            [],
            edgecolors="red",
        )
        self.ax.add_collection(self.odometry)

        self.fig.canvas.draw()
        plt.show(block=False)
        self.fig.canvas.update()
        self.fig.canvas.flush_events()

    def plot_variables_and_factors(
        self,
        result: gtsam.Values,
        poses: list[X],
        landmarks: list[Landmark],
        factors: gtsam.NonlinearFactorGraph,
    ) -> None:
        self.ax.draw_artist(self.ax.patch)

        if len(poses) > 0:
            pose_mean_poses: list[gtsam.Pose2] = [result.atPose2(var) for var in poses]
            pose_mean_verts = [
                make_mark(POSE_MARK, 1.0, rot(c.theta()), c.translation())
                for c in pose_mean_poses
            ]
            self.pose_mean_poly.set_verts(pose_mean_verts)

            try:
                pose_point_poses: list[gtsam.Pose2] = list(
                    self.pose_samples(result, poses)
                )
                pose_point_verts = [
                    make_mark(POSE_MARK, 0.5, rot(c.theta()), c.translation())
                    for c in pose_point_poses
                ]
                self.pose_point_poly.set_verts(pose_point_verts)
            except AttributeError:
                pass  # no marginal covariance

        if len(landmarks) > 0:
            landmark_mean_translations = np.array(
                [result.atPoint2(var.symbol) for var in landmarks]
            )
            landmark_mean_verts = [
                make_mark(LANDMARK_MARK, 1.0, rot(math.pi / 4), c)
                for c in landmark_mean_translations
            ]
            self.landmark_mean_poly.set_verts(landmark_mean_verts)

            try:
                landmark_point_translations = np.vstack(
                    [self.landmark_point(result, var.symbol) for var in landmarks]
                )
                self.landmark_point_scatter.set_offsets(landmark_point_translations)
            except AttributeError:
                pass  # batch smoother has no marginal covariance method.

        self.plot_factors(result, factors)

        self.ax.redraw_in_frame()
        self.fig.canvas.update()
        self.fig.canvas.flush_events()

    def plot_factors(
        self,
        result: gtsam.Values,
        factors: gtsam.NonlinearFactorGraph,
    ) -> None:
        paths = []
        odometry_paths = []
        # if *any* of the factors are over the boundary...
        boundary = False
        for f_i in range(factors.size()):
            if not factors.exists(f_i):
                # the list is not dense
                continue
            factor = factors.at(f_i)
            if isinstance(factor, gtsam.CustomFactor):
                factor_type = CustomFactorType(factor.getKey())
                match factor_type:
                    case CustomFactorType.BETWEEN:
                        self.odometry_path(result, odometry_paths, factor)
                    case CustomFactorType.BOUNDARY:
                        pose = result.atPose2(factor.keys()[0])
                        if pose.x() > 4.5:
                            boundary = True
                    case _:
                        print("wtf")

            if isinstance(factor, gtsam.BearingRangeFactor2D):
                self.bearing_range_path(result, paths, factor)

        if boundary:
            self.vline.set_color("red")
            self.vline.set_alpha(0.5)
        else:
            self.vline.set_color("blue")
            self.vline.set_alpha(0.2)

        self.sights.set_paths(paths)
        self.odometry.set_paths(odometry_paths)

    def odometry_path(self, result, odometry_paths, factor):
        # paint the path as a straight line
        odometry_paths.append(
            matplotlib.path.Path(
                [
                    result.atPose2(factor.keys()[0]).translation(),
                    result.atPose2(factor.keys()[1]).translation(),
                ]
            )
        )

    def bearing_range_path(self, result, paths, factor):
        # for now, a sighting is a point2 and a pose2, see sam.i
        paths.append(
            matplotlib.path.Path(
                [
                    result.atPose2(factor.keys()[0]).translation(),
                    result.atPoint2(factor.keys()[1]),
                ]
            )
        )

    def landmark_point(self, result, var):
        mean = result.atPoint2(var)  # 1x2
        covariance = self.isam.getISAM2().marginalCovariance(var)  # 2x2
        return RNG.multivariate_normal(mean, covariance, NUM_DRAWS)

    def pose_samples(
        self, result: gtsam.Values, poses: list[X]
    ) -> Iterator[gtsam.Pose2]:
        for var in poses:
            mean: gtsam.Pose2 = result.atPose2(var)  # 1x3
            # this covariance is in the *tangent space* at the *mean*
            # i.e. the "twist", so that's how we apply it
            covariance: np.ndarray = self.isam.getISAM2().marginalCovariance(var)  # 3x3
            # it is much faster to call the RNG once for N draws than to do it in the loop
            random_points: np.ndarray = RNG.multivariate_normal(
                np.zeros(3), covariance, NUM_DRAWS
            )
            # random_poses: list = [None] * NUM_DRAWS
            for i in range(0, NUM_DRAWS):
                yield mean.compose(gtsam.Pose2.Expmap(random_points[i, :]))
            # return random_poses