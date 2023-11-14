package org.team100.frc2023;

import java.io.IOException;

import org.team100.frc2023.autonomous.DriveToAprilTag;
import org.team100.frc2023.autonomous.Rotate;
import org.team100.frc2023.commands.Defense;
import org.team100.frc2023.commands.DriveScaled;
import org.team100.frc2023.commands.DriveWithHeading;
import org.team100.lib.commands.FancyTrajectory;
import org.team100.lib.commands.ResetPose;
import org.team100.lib.commands.ResetRotation;
import org.team100.lib.config.AllianceSelector;
import org.team100.lib.config.AutonSelector;
import org.team100.lib.config.Identity;
import org.team100.lib.controller.DriveControllers;
import org.team100.lib.controller.DriveControllersFactory;
import org.team100.lib.controller.HolonomicDriveController2;
import org.team100.lib.experiments.Experiments;
import org.team100.lib.hid.Control;
import org.team100.lib.hid.DualXboxControl;
import org.team100.lib.indicator.LEDIndicator;
import org.team100.lib.indicator.LEDIndicator.State;
import org.team100.lib.localization.AprilTagFieldLayoutWithCorrectOrientation;
import org.team100.lib.localization.VisionDataProvider;
import org.team100.lib.motion.drivetrain.Heading;
import org.team100.lib.motion.drivetrain.SpeedLimits;
import org.team100.lib.motion.drivetrain.SpeedLimitsFactory;
import org.team100.lib.motion.drivetrain.SwerveDriveSubsystem;
import org.team100.lib.motion.drivetrain.SwerveLocal;
import org.team100.lib.motion.drivetrain.SwerveModuleCollectionFactory;
import org.team100.lib.motion.drivetrain.SwerveModuleCollectionInterface;
import org.team100.lib.motion.drivetrain.VeeringCorrection;
import org.team100.lib.motion.drivetrain.kinematics.FrameTransform;
import org.team100.lib.motion.drivetrain.kinematics.SwerveDriveKinematicsFactory;
import org.team100.lib.sensors.RedundantGyro;
import org.team100.lib.sensors.RedundantGyroInterface;
import org.team100.lib.swerve.SwerveKinematicLimits;
import org.team100.lib.telemetry.Telemetry;
import org.team100.lib.trajectory.DrawCircle;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.RunCommand;

public class RobotContainer {
    public static class Config {

        //////////////////////////////////////
        // SHOW MODE
        //
        // Show mode is for younger drivers to drive the robot slowly.
        //
        // TODO: make a physical show mode switch.
        public boolean SHOW_MODE = true;
        //
        //////////////////////////////////////

        public double kDriveCurrentLimit = 30;
        // public double kDriveCurrentLimit = SHOW_MODE ? 20 : 60;
    }

    private final Config m_config = new Config();

    private final Telemetry t = Telemetry.get();

    private final AutonSelector m_autonSelector;
    private final AllianceSelector m_allianceSelector;

    // SUBSYSTEMS
    private final Heading m_heading;
    private final LEDIndicator m_indicator;
    private final RedundantGyroInterface ahrsclass;
    private final Field2d m_field;
    private final AprilTagFieldLayoutWithCorrectOrientation layout;
    private final SwerveDriveSubsystem m_robotDrive;
    private final SwerveModuleCollectionInterface m_modules;
    private final SwerveDriveKinematics m_kinematics;
    // TODO replace with SpeedLimits.
    private final SwerveKinematicLimits m_kinematicLimits;
    private final Command m_auton;
    private final FrameTransform m_frameTransform;



    // HID CONTROL
    private final Control control;

    // AUTON

    public RobotContainer() throws IOException {

        m_autonSelector = new AutonSelector();
        t.log("/Routine", getRoutine());

        m_allianceSelector = new AllianceSelector();
        t.log("/Alliance", m_allianceSelector.alliance().name());

        m_indicator = new LEDIndicator(8);

        Identity identity = Identity.get();
        // override the correct identity for testing.
        // Identity identity = Identity.COMP_BOT;

        ahrsclass = new RedundantGyro.Factory(identity).get();
        m_heading = new Heading(ahrsclass);
        m_field = new Field2d();

        SpeedLimits speedLimits = SpeedLimitsFactory.get(identity, false);
        m_kinematics = SwerveDriveKinematicsFactory.get(identity);

        m_kinematicLimits = new SwerveKinematicLimits();
        // TODO: fix these limits
        m_kinematicLimits.kMaxDriveVelocity = 4;
        m_kinematicLimits.kMaxDriveAcceleration = 2;
        m_kinematicLimits.kMaxSteeringVelocity = Units.degreesToRadians(750.0);

        VeeringCorrection veering = new VeeringCorrection(m_heading::getHeadingRateNWU);

        m_frameTransform = new FrameTransform(veering);

        Experiments experiments = new Experiments(identity);

        m_modules = new SwerveModuleCollectionFactory(
                experiments,
                identity,
                m_config.kDriveCurrentLimit).get();

        SwerveDrivePoseEstimator poseEstimator = new SwerveDrivePoseEstimator(
                m_kinematics,
                m_heading.getHeadingNWU(),
                m_modules.positions(),
                new Pose2d(),
                VecBuilder.fill(0.5, 0.5, 0.5),
                VecBuilder.fill(0.1, 0.1, 0.4)); // note tight rotation variance here, used to be MAX_VALUE

        // TODO: make this override work better
        // if (m_allianceSelector.alliance() == DriverStation.Alliance.Blue) {
        layout = AprilTagFieldLayoutWithCorrectOrientation.blueLayout("2023-studies.json");
        // } else { // red
        // layout =
        // AprilTagFieldLayoutWithCorrectOrientation.redLayout("2023-studies.json");
        // }

        // hunting the memory leak
        VisionDataProvider visionDataProvider = new VisionDataProvider(
                layout,
                poseEstimator,
                poseEstimator::getEstimatedPosition);
        visionDataProvider.enable();

        SwerveLocal swerveLocal = new SwerveLocal(experiments, speedLimits, m_kinematics, m_modules);

        DriveControllers controllers = new DriveControllersFactory().get(identity, speedLimits);
        HolonomicDriveController2 controller = new HolonomicDriveController2(controllers);

        m_robotDrive = new SwerveDriveSubsystem(
                m_heading,
                poseEstimator,
                m_frameTransform,
                swerveLocal,
                controller,
                m_field);

        m_auton = new Defense(m_robotDrive);

        // TODO: control selection using names
        control = new DualXboxControl();
        // control = new JoystickControl();

        ////////////////////////////
        // DRIVETRAIN COMMANDS
        control.defense(new Defense(m_robotDrive));
        control.resetRotation0(new ResetRotation(m_robotDrive, new Rotation2d(0)));
        control.resetRotation180(new ResetRotation(m_robotDrive, Rotation2d.fromDegrees(180)));
        SpeedLimits slow = new SpeedLimits(0.4, 1.0, 0.5, 1.0);
        control.driveSlow(new DriveScaled(control::twist, m_robotDrive, slow));
        SpeedLimits medium = new SpeedLimits(2.0, 2.0, 0.5, 1.0);
        control.driveMedium(new DriveScaled(control::twist, m_robotDrive, medium));
        // TODO: make the reset configurable
        // control.resetPose(new ResetPose(m_robotDrive, 0, 0, 0));
        control.resetPose(new ResetPose(m_robotDrive, 0, 0, Math.PI));
        control.rotate0(new Rotate(m_robotDrive, m_heading, speedLimits, new Timer(), 0));

  
        // new Circle(new Pose2d(1, 1, Rotation2d.fromDegrees(180))), m_robotDrive,
        // m_kinematics

        // Circle circle =

        Pose2d[] goalArr = { new Pose2d(-2.199237, -0.400119, Rotation2d.fromDegrees(180)),
                new Pose2d(-2.199237, 1, Rotation2d.fromDegrees(180)),
                new Pose2d(-3.312756, 1, Rotation2d.fromDegrees(180)),
                new Pose2d(-3.312756, -0.400119, Rotation2d.fromDegrees(180)),
                new Pose2d(-2.199237, -0.400119, Rotation2d.fromDegrees(180))

        };
        // control.circle(new Circle(new Pose2d(-2, 0, Rotation2d.fromDegrees(180)),
        // m_robotDrive, m_kinematics));
        control.circle(new DrawCircle(goalArr, m_robotDrive, m_kinematics));



        control.driveWithFancyTrajec(new FancyTrajectory(m_kinematics, m_kinematicLimits, m_robotDrive));

        ///////////////////////////
        // DRIVE

        if (m_config.SHOW_MODE) {
            m_robotDrive.setDefaultCommand(
                    new DriveScaled(
                            control::twist,
                            m_robotDrive,
                            SpeedLimitsFactory.get(identity, false)));
        } else {
            m_robotDrive.setDefaultCommand(
                    new DriveWithHeading(
                            control::twist,
                            m_robotDrive,
                            m_heading,
                            speedLimits,
                            new Timer(),
                            control::desiredRotation));
        }


    }

    public void scheduleAuton() {
        m_auton.schedule();
    }

    public void cancelAuton() {
        m_auton.cancel();
    }

    public void runTest() {
        XboxController controller0 = new XboxController(0);
        System.out.printf(
                "name: %s   left X: %5.2f   left Y: %5.2f   right X: %5.2f   right Y: %5.2f   left T: %5.2f   right T: %5.2f\n",
                DriverStation.getJoystickName(0),
                // DriverStation.getStickAxis(0, 0),
                controller0.getLeftX(), // 0 = GP right X, -0.66 to 0.83
                controller0.getLeftY(), // 1 = GP right Y, -0.64 to 0.64
                controller0.getRightX(), // 4 = GP left X, -0.7 to 0.8
                controller0.getRightY(), // 5
                controller0.getLeftTriggerAxis(), // 2 = GP left Y, -0.64 to 0.6
                controller0.getRightTriggerAxis() // 3
        );
    }

    public void runTest2() {

        XboxController controller0 = new XboxController(0);
        boolean rearLeft = controller0.getAButton();
        boolean rearRight = controller0.getBButton();
        boolean frontLeft = controller0.getXButton();
        boolean frontRight = controller0.getYButton();
        double driveControl = controller0.getLeftY();
        double turnControl = controller0.getLeftX();
        double[][] desiredOutputs = {
                { frontLeft ? driveControl : 0, frontLeft ? turnControl : 0 },
                { frontRight ? driveControl : 0, frontRight ? turnControl : 0 },
                { rearLeft ? driveControl : 0, rearLeft ? turnControl : 0 },
                { rearRight ? driveControl : 0, rearRight ? turnControl : 0 }
        };
        m_robotDrive.test(desiredOutputs);

    }

    public double getRoutine() {
        return m_autonSelector.routine();
    }

    public void ledStart() {
        m_indicator.set(State.ORANGE);
    }

    public void ledStop() {
        m_indicator.close();
    }

    public void red() {
        m_indicator.set(State.RED);
    }

    public void green() {
        m_indicator.set(State.GREEN);
    }

    private DriveToAprilTag toTag(
            int tagID,
            double xOffset,
            double yOffset) {
        return new DriveToAprilTag(
                tagID,
                xOffset,
                yOffset,
                m_robotDrive,
                m_kinematics,
                layout);
    }

    // this keeps the tests from conflicting via the use of simulated HAL ports.
    public void close() {
        m_autonSelector.close();
        m_allianceSelector.close();
        m_indicator.close();
        m_modules.close();
    }
}