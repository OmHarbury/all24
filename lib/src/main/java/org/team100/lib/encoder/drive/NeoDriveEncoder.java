package org.team100.lib.encoder.drive;

import java.util.OptionalDouble;

import org.team100.lib.encoder.SettableEncoder;
import org.team100.lib.motor.drive.NeoDriveMotor;
import org.team100.lib.telemetry.Telemetry;
import org.team100.lib.telemetry.Telemetry.Level;
import org.team100.lib.telemetry.Telemetry.Logger;
import org.team100.lib.units.Distance100;

/**
 * The built-in encoder in Neo motors.
 * 
 * This encoder simply senses the 14 rotor magnets in 3 places, so it's 42 ticks
 * per turn.
 */
public class NeoDriveEncoder implements SettableEncoder<Distance100> {
    private final Telemetry.Logger m_logger;
    private final NeoDriveMotor m_motor;
    private final double m_distancePerTurn;

    public NeoDriveEncoder(
            Logger parent,
            NeoDriveMotor motor,
            double distancePerTurn) {
        m_logger = parent.child(this);
        m_motor = motor;
        m_distancePerTurn = distancePerTurn;
    }

    /** Position in meters. */
    @Override
    public OptionalDouble getPosition() {
        return OptionalDouble.of(getPositionM());
    }

    /** Velocity in meters/sec. */
    @Override
    public OptionalDouble getRate() {
        return OptionalDouble.of(getVelocityM_S());
    }

    /** Position in meters. */
    @Override
    public void setPosition(double positionM) {
        double motorPositionRev = positionM / m_distancePerTurn;
        m_motor.setEncoderPosition(motorPositionRev);
    }

    @Override
    public void reset() {
        m_motor.resetEncoderPosition();
    }

    @Override
    public void close() {
        //
    }

    //////////////////////////////////

    private double getPositionM() {
        // raw position is in rotations
        // this is fast so we don't need to cache it
        double motorPositionRev = m_motor.getPositionRot();
        double positionM = motorPositionRev * m_distancePerTurn;
        m_logger.logDouble(Level.TRACE, "motor position (rev)", ()->motorPositionRev);
        m_logger.logDouble(Level.DEBUG, "position (m)",()-> positionM);
        return positionM;
    }

    private double getVelocityM_S() {
        // raw velocity is in RPM
        // this is fast so we don't need to cache it
        double velocityM_S = m_motor.getRateRPM() * m_distancePerTurn / 60;
        m_logger.logDouble(Level.DEBUG, "velocity (m_s)", ()->velocityM_S);
        return velocityM_S;
    }
}
