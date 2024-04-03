package org.team100.lib.copies;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.constraint.TrajectoryConstraint;

/**
 * A class that enforces constraints on the swerve drive kinematics. This can be
 * used to ensure that the trajectory is constructed so that the commanded
 * velocities for all 4 wheels of the drivetrain stay below a certain limit.
 * 
 * TODO: remove this class, do trajectories in just one way (not this way).
 */
public class SwerveDriveKinematicsConstraint100 implements TrajectoryConstraint {
    private final double m_maxSpeedMetersPerSecond;
    private final SwerveDriveKinematics100 m_kinematics;

    /**
     * Constructs a swerve drive kinematics constraint.
     *
     * @param kinematics              Swerve drive kinematics.
     * @param maxSpeedMetersPerSecond The max speed that a side of the robot can
     *                                travel at.
     */
    public SwerveDriveKinematicsConstraint100(
            final SwerveDriveKinematics100 kinematics, double maxSpeedMetersPerSecond) {
        m_maxSpeedMetersPerSecond = maxSpeedMetersPerSecond;
        m_kinematics = kinematics;
    }

    /**
     * Returns the max velocity given the current pose and curvature.
     *
     * @param poseMeters              The pose at the current point in the
     *                                trajectory.
     * @param curvatureRadPerMeter    The curvature at the current point in the
     *                                trajectory.
     * @param velocityMetersPerSecond The velocity at the current point in the
     *                                trajectory before
     *                                constraints are applied.
     * @return The absolute maximum velocity.
     */
    @Override
    public double getMaxVelocityMetersPerSecond(
            Pose2d poseMeters,
            double curvatureRadPerMeter,
            double velocityMetersPerSecond) {
        // Represents the velocity of the chassis in the x direction
        double xdVelocity = velocityMetersPerSecond * poseMeters.getRotation().getCos();

        // Represents the velocity of the chassis in the y direction
        double ydVelocity = velocityMetersPerSecond * poseMeters.getRotation().getSin();

        // Create an object to represent the current chassis speeds.
        ChassisSpeeds chassisSpeeds = new ChassisSpeeds(xdVelocity, ydVelocity,
                velocityMetersPerSecond * curvatureRadPerMeter);

        // Get the wheel speeds and normalize them to within the max velocity.
        SwerveModuleState[] wheelSpeeds = m_kinematics.toSwerveModuleStates(chassisSpeeds);
        SwerveDriveKinematics.desaturateWheelSpeeds(wheelSpeeds, m_maxSpeedMetersPerSecond);

        // Convert normalized wheel speeds back to chassis speeds
        ChassisSpeeds normSpeeds = m_kinematics.toChassisSpeeds(wheelSpeeds);

        // Return the new linear chassis speed.
        return Math.hypot(normSpeeds.vxMetersPerSecond, normSpeeds.vyMetersPerSecond);
    }

    /**
     * Returns the minimum and maximum allowable acceleration for the trajectory
     * given pose, curvature, and speed.
     *
     * @param poseMeters              The pose at the current point in the
     *                                trajectory.
     * @param curvatureRadPerMeter    The curvature at the current point in the
     *                                trajectory.
     * @param velocityMetersPerSecond The speed at the current point in the
     *                                trajectory.
     * @return The min and max acceleration bounds.
     */
    @Override
    public MinMax getMinMaxAccelerationMetersPerSecondSq(
            Pose2d poseMeters, double curvatureRadPerMeter, double velocityMetersPerSecond) {
        return new MinMax();
    }
}
