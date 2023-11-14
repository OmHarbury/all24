// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.armcommands.Sequence;
import frc.robot.subsystems.ArmSubsystem;

public class RobotContainer {
    private ArmSubsystem m_armSubsystem = new ArmSubsystem();
    private Command m_auton = new Sequence(m_armSubsystem);
    public RobotContainer() {
        configureBindings();
    }

    private void configureBindings() {
    }

    public Command getAutonomousCommand() {
        return m_auton;
    }
    public void runPeriodic() {
        
    }
    public void runTest() {
        XboxController controller = new XboxController(0);
        m_armSubsystem.set(0, 0);
        if (controller.getAButton()) {
            m_armSubsystem.set(.1, 0);
        }
        if (controller.getBButton()) {
            m_armSubsystem.set(0, .1);
        }
        if (controller.getXButton()) {
            m_armSubsystem.set(-.1, 0);
        }
        if (controller.getYButton()) {
            m_armSubsystem.set(0, -.1);
        }
    }

    public void scheduleAuton() {
        m_auton.schedule();
    }
}