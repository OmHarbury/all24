package org.team100.lib.motion.drivetrain.manual;

import java.util.function.Supplier;

import org.team100.lib.commands.drivetrain.FieldRelativeDriver;
import org.team100.lib.commands.drivetrain.HeadingLatch;
import org.team100.lib.controller.State100;
import org.team100.lib.experiments.Experiment;
import org.team100.lib.experiments.Experiments;
import org.team100.lib.hid.DriverControl;
import org.team100.lib.motion.drivetrain.SwerveState;
import org.team100.lib.motion.drivetrain.kinodynamics.FieldRelativeVelocity;
import org.team100.lib.motion.drivetrain.kinodynamics.SwerveKinodynamics;
import org.team100.lib.sensors.HeadingInterface;
import org.team100.lib.telemetry.Telemetry;
import org.team100.lib.telemetry.Telemetry.Level;
import org.team100.lib.util.DriveUtil;
import org.team100.lib.util.Math100;
import org.team100.lib.util.Names;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.filter.LinearFilter;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;

/**
 * Function that supports manual cartesian control, and both manual and locked
 * rotational control.
 * 
 * Rotation uses simple full-state feedback and that's all..
 */
public class ManualWithFullStateHeading implements FieldRelativeDriver {
    private final Telemetry.Logger t;
    private final SwerveKinodynamics m_swerveKinodynamics;
    private final HeadingInterface m_heading;
    /** Absolute input supplier, null if free */
    private final Supplier<Rotation2d> m_desiredRotation;
    private final HeadingLatch m_latch;
    private final String m_name;
    // feedback gains
    private final double[] m_K;
    private final LinearFilter m_outputFilter;

    // package private for testing
    Rotation2d m_goal = null;
    State100 m_thetaSetpoint = null;

    /**
     * 
     * @param parent
     * @param swerveKinodynamics
     * @param heading
     * @param desiredRotation    absolute input supplier, null if free. usually
     *                           POV-derived.
     * @param k                  full state gains
     */
    public ManualWithFullStateHeading(
            String parent,
            SwerveKinodynamics swerveKinodynamics,
            HeadingInterface heading,
            Supplier<Rotation2d> desiredRotation,
            double[] k) {
        m_swerveKinodynamics = swerveKinodynamics;
        m_heading = heading;
        m_desiredRotation = desiredRotation;
        m_name = Names.append(parent, this);
        t = Telemetry.get().logger(m_name);
        m_K = k;
        m_latch = new HeadingLatch();
        m_outputFilter = LinearFilter.singlePoleIIR(0.01, 0.02);
    }

    public void reset(Pose2d currentPose) {
        m_goal = null;
        m_latch.unlatch();
        updateSetpoint(currentPose.getRotation().getRadians(), getHeadingRateNWURad_S());
    }

    private double getHeadingRateNWURad_S() {
        return m_heading.getHeadingRateNWU();
    }

    /** Call this to keep the setpoint in sync with the manual rotation. */
    private void updateSetpoint(double x, double v) {
        m_thetaSetpoint = new State100(x, v);
    }

    /**
     * Clips the input to the unit circle, scales to maximum (not simultaneously
     * feasible) speeds, and then desaturates to a feasible holonomic velocity.
     * 
     * If you touch the POV and not the twist rotation, it remembers the POV. if you
     * use the twist rotation, it forgets and just uses that.
     * 
     * Desaturation prefers the rotational profile completely in the snap case, and
     * normally in the non-snap case.
     * 
     * This uses a fixed dt = 0.02 for the profile.
     * 
     * @param state    current drivetrain state from the pose estimator
     * @param twist1_1 control units, [-1,1]
     * @return feasible field-relative velocity in m/s and rad/s
     */
    public FieldRelativeVelocity apply(SwerveState state, DriverControl.Velocity twist1_1) {
        Pose2d currentPose = state.pose();

        // clip the input to the unit circle
        DriverControl.Velocity clipped = DriveUtil.clampTwist(twist1_1, 1.0);
        // scale to max in both translation and rotation
        FieldRelativeVelocity twistM_S = DriveUtil.scale(
                clipped,
                m_swerveKinodynamics.getMaxDriveVelocityM_S(),
                m_swerveKinodynamics.getMaxAngleSpeedRad_S());

        Rotation2d currentRotation = currentPose.getRotation();
        double headingMeasurement = currentRotation.getRadians();
        double headingRate = getHeadingRateNWURad_S();

        Rotation2d pov = m_desiredRotation.get();
        m_goal = m_latch.latchedRotation(state.theta(), currentRotation, pov, twistM_S.theta());
        if (m_goal == null) {
            // we're not in snap mode, so it's pure manual
            // in this case there is no setpoint
            m_thetaSetpoint = null;
            t.log(Level.TRACE, "mode", "free");
            // desaturate to feasibility
            return m_swerveKinodynamics.analyticDesaturation(twistM_S);
        }

        // take the short path
        m_goal = new Rotation2d(
                Math100.getMinDistance(headingMeasurement, m_goal.getRadians()));

        // if this is the first run since the latch, then the setpoint should be
        // whatever the measurement is
        if (m_thetaSetpoint == null) {
            // TODO: to avoid overshoot, maybe pick a setpoint that is feasible without
            // overshoot?
            updateSetpoint(headingMeasurement, headingRate);
        }

        // in snap mode we take dx and dy from the user, and control dtheta.
        // the omega goal in snap mode is always zero.
        m_thetaSetpoint = new State100(m_goal.getRadians(), 0);

        // the snap overrides the user input for omega.
        double thetaFF = m_thetaSetpoint.v();

        double thetaError = MathUtil.angleModulus(m_thetaSetpoint.x() - headingMeasurement);
        double omegaError = m_thetaSetpoint.v() - headingRate;

        double thetaFB = m_K[0] * thetaError;
        double omegaFB = m_K[1] * omegaError;

        if (Experiments.instance.enabled(Experiment.UseThetaFilter)) {
            // output filtering to prevent oscillation due to delay
            omegaFB = m_outputFilter.calculate(omegaFB);
        }
        if (Math.abs(omegaFB) < 0.05) {
            omegaFB = 0;
        }
        if (Math.abs(thetaFB) < 0.05) {
            thetaFB = 0;
        }
        double omega = MathUtil.clamp(
                thetaFF + thetaFB + omegaFB,
                -m_swerveKinodynamics.getMaxAngleSpeedRad_S(),
                m_swerveKinodynamics.getMaxAngleSpeedRad_S());
        FieldRelativeVelocity twistWithSnapM_S = new FieldRelativeVelocity(twistM_S.x(), twistM_S.y(), omega);

        t.log(Level.TRACE, "mode", "snap");
        t.logDouble(Level.TRACE, "goal/theta",()-> m_goal.getRadians());
        t.log(Level.TRACE, "setpoint/theta", m_thetaSetpoint);
        t.logDouble(Level.TRACE, "measurement/theta",()-> headingMeasurement);
        t.logDouble(Level.TRACE, "measurement/omega",()-> headingRate);
        t.logDouble(Level.TRACE, "error/theta",()-> thetaError);
        t.logDouble(Level.TRACE, "error/omega",()-> omegaError);
        t.logDouble(Level.TRACE, "thetaFF",()-> thetaFF);
        t.logDouble(Level.TRACE, "thetaFB",()-> thetaFB);
        t.logDouble(Level.TRACE, "omegaFB",()-> omegaFB);
        t.logDouble(Level.TRACE, "output/omega", ()->omega);

        // desaturate the end result to feasibility by preferring the rotation over
        // translation
        twistWithSnapM_S = m_swerveKinodynamics.preferRotation(twistWithSnapM_S);
        return twistWithSnapM_S;
    }

    @Override
    public String getGlassName() {
        return "ManualWithFullStateHeading";
    }

}
