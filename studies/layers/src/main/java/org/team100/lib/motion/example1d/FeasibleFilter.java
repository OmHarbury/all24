package org.team100.lib.motion.example1d;

import java.util.function.DoubleUnaryOperator;

import edu.wpi.first.wpilibj.Timer;

public class FeasibleFilter implements DoubleUnaryOperator {

    private final double m_maxVelocityM_S;
    private final double m_maxAccelM_S_S;
    private double m_prevVelM_S;
    private double m_prevTimeS;

    public FeasibleFilter( double maxVelocityM_S, double maxAccelM_S_S) {
        m_maxVelocityM_S = maxVelocityM_S;
        m_maxAccelM_S_S = maxAccelM_S_S;
        m_prevTimeS = 0;
        m_prevVelM_S = 0;
    }

    /**
     * Limit velocity and acceleration.
     * 
     * @return a feasible velocity in meters per second
     */
    @Override
    public double applyAsDouble(double velocityM_S) {
        double nowS = Timer.getFPGATimestamp();
        double dtS = nowS - m_prevTimeS;
        m_prevTimeS = nowS;
        double accelM_S_S = (velocityM_S - m_prevVelM_S)/dtS;
        if (velocityM_S > m_maxVelocityM_S) {
            return m_maxAccelM_S_S;
        }
        if (velocityM_S < -m_maxVelocityM_S) {
            return -m_maxVelocityM_S;
        }
        if (accelM_S_S > m_maxAccelM_S_S) {
            return m_prevVelM_S + m_maxAccelM_S_S * dtS;
        }
        if (accelM_S_S < -m_maxAccelM_S_S) {
            return m_prevVelM_S - m_maxAccelM_S_S * dtS;
        }
        return velocityM_S;
    }
}