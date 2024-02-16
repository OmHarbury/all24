// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package org.team100.frc2024.motion;

import org.team100.frc2024.RobotState100;
import org.team100.frc2024.RobotState100.IntakeState100;
import org.team100.frc2024.motion.indexer.IndexerSubsystem;
import org.team100.frc2024.motion.intake.Intake;

import edu.wpi.first.wpilibj2.command.Command;

public class IntakeNote extends Command {
  /** Creates a new IntakeNote. */
  Intake m_intake;
  IndexerSubsystem m_indexer;

  public IntakeNote(Intake intake, IndexerSubsystem indexer) {
    // Use addRequirements() here to declare subsystem dependencies.
    m_intake = intake;
    m_indexer = indexer;
  }

  // Called when the command is initially scheduled.
  @Override
  public void initialize() {
    RobotState100.changeIntakeState(IntakeState100.INTAKE);

  }

  // Called every time the scheduler runs while the command is scheduled.
  @Override
  public void execute() {

  }

  // Called once the command ends or is interrupted.
  @Override
  public void end(boolean interrupted) {
    RobotState100.changeIntakeState(IntakeState100.STOP);

  }

  // Returns true when the command should end.
  @Override
  public boolean isFinished() {
    return false;
  }
}
