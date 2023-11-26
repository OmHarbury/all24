package org.team100.lib.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.team100.lib.geometry.GeometryUtil;
import org.team100.lib.swerve.SwerveKinematicLimits;
import org.team100.lib.timing.CentripetalAccelerationConstraint;
import org.team100.lib.timing.TimedPose;
import org.team100.lib.timing.TimingConstraint;
import org.team100.lib.trajectory.Trajectory100;
import org.team100.lib.trajectory.TrajectoryPlanner;
import org.team100.lib.trajectory.TrajectoryTimeIterator;
import org.team100.lib.trajectory.TrajectoryTimeSampler;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.geometry.Twist2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.util.Units;

class DrivePIDControllerTest {

    private static final double kMaxVelocityMetersPerSecond = 5.05; // Calibrated 3/12 on Comp Bot
    private static final double kMaxAccelerationMetersPerSecondSquared = 4.4;

    private static final double kDriveTrackwidthMeters = 0.52705; // DONE Measure and set trackwidth
    private static final double kDriveWheelbaseMeters = 0.52705; // DONE Measure and set wheelbase

    private static final SwerveDriveKinematics kKinematics = new SwerveDriveKinematics(
            // Front left
            new Translation2d(kDriveTrackwidthMeters / 2.0, kDriveWheelbaseMeters / 2.0),
            // Front right
            new Translation2d(kDriveTrackwidthMeters / 2.0, -kDriveWheelbaseMeters / 2.0),
            // Back left
            new Translation2d(-kDriveTrackwidthMeters / 2.0, kDriveWheelbaseMeters / 2.0),
            // Back right
            new Translation2d(-kDriveTrackwidthMeters / 2.0, -kDriveWheelbaseMeters / 2.0));

    private static final SwerveKinematicLimits kSmoothKinematicLimits = new SwerveKinematicLimits(4.5, 4.4, 13);

    @Test
    void testPIDControl() {
        final double kMaxVel = 1.0;
        final double kMaxAccel = 1.0;
        // this doesn't actually do anything.
        final double kMaxVoltage = 9.0;

        // first right and then ahead
        List<Pose2d> waypoints = List.of(
                new Pose2d(0, 0, Rotation2d.fromDegrees(270)),
                new Pose2d(10, -10, Rotation2d.fromDegrees(0)));
        // face +y and end up -x
        List<Rotation2d> headings = List.of(
                GeometryUtil.fromDegrees(90),
                GeometryUtil.fromDegrees(180));
        // so this trajectory is actually (robot-relative) -x the whole way, more or
        // less.
        // these don't actually do anything.
        List<TimingConstraint> constraints = List.of(
                new CentripetalAccelerationConstraint(60));

        // note there are static constraints in here.
        TrajectoryPlanner planner = new TrajectoryPlanner(kKinematics, kSmoothKinematicLimits);
        double start_vel = 0;
        double end_vel = 0;
        // there's a bug in here; it doesn't use the constraints, nor the voltage.
        Trajectory100 trajectory = planner.generateTrajectory(
                false,
                waypoints,
                headings,
                constraints,
                start_vel,
                end_vel,
                kMaxVel,
                kMaxAccel,
                kMaxVoltage);
        // System.out.println(trajectory);
        // System.out.println("TRAJECTORY LENGTH: " + trajectory.length());
        // why is this so large?
        assertEquals(1300, trajectory.length());

        TrajectoryTimeSampler view = new TrajectoryTimeSampler(trajectory);

        TrajectoryTimeIterator iter = new TrajectoryTimeIterator(view);

        DrivePIDController controller = new DrivePIDController();
        controller.setTrajectory(iter);

        // this is a series of perfect trajectory following states,
        // based on the trajectory itself.

        {
            // System.out.println("============initialize============");
            ChassisSpeeds output = controller.update(0,
                    new Pose2d(new Translation2d(0, 0), Rotation2d.fromRadians(1.57079632679)),
                    new Twist2d());
            assertEquals(0, output.vxMetersPerSecond, 0.001);
            assertEquals(0, output.vyMetersPerSecond, 0.001);
            assertEquals(0, output.omegaRadiansPerSecond, 0.001);
        }

        {
            // System.out.println("============4 sec============");
            ChassisSpeeds output = controller.update(4.0,
                    new Pose2d(new Translation2d(0.25, -3.5), Rotation2d.fromRadians(1.69)),
                    new Twist2d());
            // remember, facing +90, moving -90, so this should be like -1
            assertEquals(-1, output.vxMetersPerSecond, 0.05);
            assertEquals(-0.1, output.vyMetersPerSecond, 0.05);
            // turning slowly to the left
            assertEquals(0.1, output.omegaRadiansPerSecond, 0.05);
            Translation2d translational_error = new Translation2d(
                    controller.getError().getTranslation().getX(),
                    controller.getError().getTranslation().getY());
            assertEquals(0, translational_error.getX(), 0.05);
            assertEquals(0, translational_error.getY(), 0.05);
            Rotation2d heading_error = controller.getError().getRotation();
            assertEquals(0, heading_error.getRadians(), 0.05);
            TimedPose path_setpoint = controller.getSetpoint();
            assertEquals(0.25, path_setpoint.state().getPose().getX(), 0.01);
            assertEquals(-3.5, path_setpoint.state().getPose().getY(), 0.05);
            assertEquals(1.69, path_setpoint.state().getPose().getRotation().getRadians(), 0.01);
            assertEquals(4, path_setpoint.getTimeS(), 0.01);
            assertEquals(1, path_setpoint.velocityM_S(), 0.01);
            assertEquals(0, path_setpoint.acceleration(), 0.001);
            // Rotation2d heading_setpoint = mMotionPlanner.getHeadingSetpoint();
            // assertEquals(0, heading_setpoint.getRadians(), 0.001);
        }
        {
            // System.out.println("============8 sec============");
            ChassisSpeeds output = controller.update(8.0,
                    new Pose2d(new Translation2d(1.85, -7.11), Rotation2d.fromRadians(2.22)),
                    new Twist2d());
            assertEquals(-0.96, output.vxMetersPerSecond, 0.05);
            assertEquals(-0.05, output.vyMetersPerSecond, 0.05);
            assertEquals(0.18, output.omegaRadiansPerSecond, 0.05);
            Translation2d translational_error = new Translation2d(
                    controller.getError().getTranslation().getX(),
                    controller.getError().getTranslation().getY());
            assertEquals(0, translational_error.getX(), 0.01);
            assertEquals(0, translational_error.getY(), 0.01);
            Rotation2d heading_error = controller.getError().getRotation();
            assertEquals(0, heading_error.getRadians(), 0.01);
            TimedPose path_setpoint = controller.getSetpoint();
            assertEquals(1.85, path_setpoint.state().getPose().getX(), 0.01);
            assertEquals(-7.11, path_setpoint.state().getPose().getY(), 0.01);
            assertEquals(2.22, path_setpoint.state().getPose().getRotation().getRadians(), 0.01);
            assertEquals(8, path_setpoint.getTimeS(), 0.001);
            assertEquals(1, path_setpoint.velocityM_S(), 0.001);
            assertEquals(0, path_setpoint.acceleration(), 0.001);
            // Rotation2d heading_setpoint = mMotionPlanner.getHeadingSetpoint();
            // assertEquals(0, heading_setpoint.getRadians(), 0.001);
        }
    }

}
