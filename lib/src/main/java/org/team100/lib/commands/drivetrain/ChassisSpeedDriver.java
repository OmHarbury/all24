package org.team100.lib.commands.drivetrain;

import org.team100.lib.dashboard.Glassy;
import org.team100.lib.hid.DriverControl;
import org.team100.lib.motion.drivetrain.SwerveState;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;

public interface ChassisSpeedDriver extends Glassy {
    /**
     * @param input control units, [-1,1]
     * @return feasible chassis speeds in m/s and rad/s
     */

    ChassisSpeeds apply(SwerveState state, DriverControl.Velocity input);

    void reset(Pose2d p);

}
