package frc.robot.subsystems;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.DemandType;
import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.StatusFrameEnhanced;
import com.ctre.phoenix.motorcontrol.can.TalonSRX;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.lib.drivers.TalonSRXFactory;
import frc.lib.util.Util;
import frc.robot.Constants;
import frc.robot.loops.ILooper;
import frc.robot.loops.Loop;

public class Elbow extends Subsystem {
        private static final int kMagicMotionSlot = 0;
        private static final int kClimbControlSlot = 1;

        private int kHomeAngle = 145+8; //145
        private boolean mHasBeenZeroed = false;
    
        private static Elbow mInstance;
        private final TalonSRX mMaster;
        private PeriodicIO mPeriodicIO = new PeriodicIO();
        private double mZeroPosition = Double.NaN;
        private SystemState mSystemState = SystemState.HOMING;
        private SystemState mDesiredState = SystemState.MOTION_PROFILING;
    
        private Elbow() {
            mMaster = TalonSRXFactory.createDefaultTalon(Constants.kElbowMasterId);
    
            //configure talon
            mMaster.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Relative, 0, 100);
            mMaster.configVoltageCompSaturation(12.0, Constants.kLongCANTimeoutMs);

            //configure magic motion
            mMaster.config_kP(kMagicMotionSlot, Constants.kElbowKp, Constants.kLongCANTimeoutMs);
            mMaster.config_kI(kMagicMotionSlot, Constants.kElbowKi, Constants.kLongCANTimeoutMs);
            mMaster.config_kD(kMagicMotionSlot, Constants.kElbowKd, Constants.kLongCANTimeoutMs);
            mMaster.config_kF(kMagicMotionSlot, Constants.kElbowKf, Constants.kLongCANTimeoutMs);
            mMaster.configAllowableClosedloopError(kMagicMotionSlot, Constants.kWristDeadband, Constants.kLongCANTimeoutMs);
            mMaster.configMotionAcceleration(Constants.kElbowAcceleration, Constants.kLongCANTimeoutMs);
            mMaster.configMotionCruiseVelocity(Constants.kElbowCruiseVelocity, Constants.kLongCANTimeoutMs);

            // Configure position PID
            mMaster.config_kP(kClimbControlSlot, Constants.kElbowClimbKp, Constants.kLongCANTimeoutMs);
            mMaster.config_kI(kClimbControlSlot, Constants.kElbowKi, Constants.kLongCANTimeoutMs);
            mMaster.config_kD(kClimbControlSlot, Constants.kElbowClimbKd, Constants.kLongCANTimeoutMs);
            mMaster.configAllowableClosedloopError(kClimbControlSlot, Constants.kWristDeadband, Constants.kLongCANTimeoutMs);

            mMaster.configContinuousCurrentLimit(30, Constants.kLongCANTimeoutMs);
            mMaster.configPeakCurrentLimit(40, Constants.kLongCANTimeoutMs);
            mMaster.configPeakCurrentDuration(500, Constants.kLongCANTimeoutMs);
            mMaster.configClosedloopRamp(Constants.kElbowRampRate, Constants.kLongCANTimeoutMs);
            mMaster.configPeakOutputForward(1.0, 0);
            mMaster.configPeakOutputReverse(-1.0, 0);
            mMaster.enableCurrentLimit(true);
    
            mMaster.selectProfileSlot(0, 0);
    
            mMaster.setInverted(false);
            mMaster.setSensorPhase(false);
            mMaster.setNeutralMode(NeutralMode.Brake);
            mMaster.enableVoltageCompensation(true);
            mMaster.set(ControlMode.PercentOutput, 0);
    
            mMaster.setStatusFramePeriod(StatusFrameEnhanced.Status_2_Feedback0, 10, 20);
            mMaster.setStatusFramePeriod(StatusFrameEnhanced.Status_10_MotionMagic, 10, 20);
        }
    
        public synchronized static Elbow getInstance() {
            if (mInstance == null) {
                mInstance = new Elbow();
            }
            return mInstance;
        }
    
        @Override
        public synchronized void outputTelemetry() {
            SmartDashboard.putNumber("Elbow Angle", getAngle());
            SmartDashboard.putNumber("Elbow Position", getPosition());
            /*SmartDashboard.putNumber("Elbow Velocity", mPeriodicIO.velocity_ticks_per_100ms);
            SmartDashboard.putNumber("Elbow Ticks", mPeriodicIO.position_ticks);
            SmartDashboard.putNumber("Elbow periodic demand", mPeriodicIO.demand);
    
            SmartDashboard.putNumber("Elbow RPM", getRPM());
            SmartDashboard.putNumber("Elbow Power %", mPeriodicIO.output_percent);
            SmartDashboard.putNumber("Elbow feedforward", mPeriodicIO.feedforward);
    
            if (mCSVWriter != null) {
                mCSVWriter.write();
            }*/
        }
    
        @Override
        public void stop() {
            setOpenLoop(0.0);
        }
    
        @Override
        public synchronized void zeroSensors() {
            mMaster.setSelectedSensorPosition(0, 0, 0);
            mHasBeenZeroed = true;
        }
    
        public synchronized boolean hasBeenZeroed() {
            return mHasBeenZeroed;
        }
    
        @Override
        public void registerEnabledLoops(ILooper enabledLooper) {
            enabledLooper.register(new Loop() {

                @Override
                public void onStart(double timestamp) {
                }
    
                @Override
                public void onLoop(double timestamp) {
                    synchronized (Elbow.this) {
                        if (!Double.isNaN(mZeroPosition) && mDesiredState != mSystemState) {
                            System.out.println(timestamp + ": Wrist changed states: " + mSystemState + " -> " +
                                    mDesiredState);
                            mSystemState = mDesiredState;
                        }
    
                        switch (mSystemState) {
                            case OPEN_LOOP:
                                // Handled in writePeriodicOutputs
                                break;
                            case MOTION_PROFILING:
                                // Handled in writePeriodicOutputs
                                break;
                            case HOMING:
                                // TODO get this working again
    //                            if (Double.isNaN(mZeroPosition)) {
    //                                mPeriodicOutputs.demand = resetIfAtLimit() ? 0.0 : kHomingOutput;
    //                            } else {
                                mSystemState = SystemState.OPEN_LOOP;
    
                                break;
                            default:
                                System.out.println("Fell through on Wrist states!");
                        }
                    }
                }
    
                @Override
                public void onStop(double timestamp) {
                }
            });
        }
    
        public synchronized void setOpenLoop(double percentage) {
            mPeriodicIO.demand = percentage;
            mDesiredState = SystemState.OPEN_LOOP;
        }
    
        /**
         * @param position the target position of the wrist in sensor units
         */
        public void setClosedLoop(int position) {
            mPeriodicIO.demand = (position);
            mDesiredState = SystemState.MOTION_PROFILING;
        }

        public synchronized void setHomeAngle(int relativeAngle) {
            kHomeAngle += relativeAngle;
        }
    
        /**
         * @param angle the target position of the wrist in degrees.  0 is full back, 180 is facing forwards
         */
        public synchronized void setMotionProfileAngle(double angle) {
            double angleFromHome = angle - kHomeAngle;
            mPeriodicIO.demand = (degreesToSensorUnits(angleFromHome));
            if (mDesiredState != SystemState.MOTION_PROFILING) {
                mDesiredState = SystemState.MOTION_PROFILING;
                mMaster.selectProfileSlot(kMagicMotionSlot, 0);
            }
        }
    
        /**
         * @param angle the target position of the wrist in degrees.  0 is full back, 180 is facing forwards
         */
        public synchronized void setPositionPIDAngle(double angle) {
            double angleFromHome = angle - kHomeAngle;
            mPeriodicIO.demand = (degreesToSensorUnits(angleFromHome));
            if (mDesiredState != SystemState.POSITION_PID) {
                mDesiredState = SystemState.POSITION_PID;
                mMaster.selectProfileSlot(kClimbControlSlot, 0);
            }
        }

        public synchronized void setClimbingAngle(double angle) {
            setPositionPIDAngle(angle);
        }
    
        /**
         * @return current position of the wrist in sensor units
         */
        public synchronized double getPosition() { //returns angle of wrist in degrees
            return (mPeriodicIO.position_ticks) + degreesToSensorUnits(kHomeAngle);
        }
    
        /**
         * @return current angle of the wrist in degrees
         */
        public synchronized double getAngle() { //returns angle of wrist in degrees
            return sensorUnitsToDegrees((mPeriodicIO.position_ticks)) + kHomeAngle;
        }
    
        /**
         * @return current velocity in rpm
         */
        public double getRPM() {
            return sensorUnitsToDegrees(mPeriodicIO.velocity_ticks_per_100ms) * 600.0 / 360.0;
        }
    
        /**
         * @return current velocity in degrees per second
         */
        public double getDegreesPerSecond() {
            return sensorUnitsToDegrees(mPeriodicIO.velocity_ticks_per_100ms) * 10.0;
        }
    
        public synchronized boolean hasFinishedTrajectory() {
            if (Util.epsilonEquals(mPeriodicIO.active_trajectory_position,
                    degreesToSensorUnits(getSetpoint()), 2)) {
                return true;
            }
            return false;
        }
    
        public synchronized double getSetpoint() {
            return mDesiredState == SystemState.MOTION_PROFILING || mDesiredState == SystemState.POSITION_PID
                    ? sensorUnitsToDegrees((mPeriodicIO.demand)) : Double.NaN;
        }
    
        private double sensorUnitsToDegrees(double units) {
            return units / 4096.0 * 360.0 / 2.7351; //2.735 pbot estimate: 3.0, theoretic: 3.077 | cbot: 2.735
        }
    
        private double degreesToSensorUnits(double degrees) {
            return degrees * 4096.0 / 360.0 * 2.7351; //3.2323233 3.077
        }
    
        @Override
        public synchronized void readPeriodicInputs() {
            if (mMaster.getControlMode() == ControlMode.MotionMagic) {
                mPeriodicIO.active_trajectory_position = mMaster.getActiveTrajectoryPosition();
    
                final int newVel = mMaster.getActiveTrajectoryVelocity();
                // TODO check sign of accel
                if (Util.epsilonEquals(newVel, Constants.kWristCruiseVelocity, 5) ||
                        Util.epsilonEquals(newVel, mPeriodicIO.active_trajectory_velocity, 5)) {
                    // Wrist is ~constant velocity.
                    mPeriodicIO.active_trajectory_acceleration_rad_per_s2 = 0.0;
                } else {
                    // Wrist is accelerating.
                    mPeriodicIO.active_trajectory_acceleration_rad_per_s2 = Math.signum(newVel - mPeriodicIO
                            .active_trajectory_velocity) * Constants.kWristAcceleration * 20.0 * Math.PI /
                            4096;
                }
                mPeriodicIO.active_trajectory_velocity = newVel;
            } else {
                mPeriodicIO.active_trajectory_position = Integer.MIN_VALUE;
                mPeriodicIO.active_trajectory_velocity = 0;
                mPeriodicIO.active_trajectory_acceleration_rad_per_s2 = 0.0;
            }
            //mPeriodicIO.output_voltage = mMaster.getMotorOutputVoltage();
            //mPeriodicIO.output_percent = mMaster.getMotorOutputPercent();
            mPeriodicIO.position_ticks = mMaster.getSelectedSensorPosition(0);
            mPeriodicIO.velocity_ticks_per_100ms = mMaster.getSelectedSensorVelocity(0);
    
            if (getAngle() > Constants.kWristEpsilon || sensorUnitsToDegrees(mPeriodicIO.active_trajectory_position) > Constants.kWristEpsilon) {
                double wristGravityComponent = Math.sin(Math.toRadians(getAngle())) * Constants.kElbowKfMultiplier;
                //double elevatorAccelerationComponent = mElevator.getActiveTrajectoryAccelG() * Constants
                      //  .kWristElevatorAccelerationMultiplier;
                double wristAccelerationComponent = mPeriodicIO.active_trajectory_acceleration_rad_per_s2 * Constants.kElbowKa;
                mPeriodicIO.feedforward = wristGravityComponent + wristAccelerationComponent; //(elevatorAccelerationComponent) * 
            } else {
                if (getSetpoint() < Util.kEpsilon) {
                    mPeriodicIO.feedforward = -0.1;
                } else {
                    mPeriodicIO.feedforward = 0.0;
                }
            }
        }
    
        @Override
        public synchronized void writePeriodicOutputs() {
            if (mDesiredState == SystemState.MOTION_PROFILING) {
                mMaster.set(ControlMode.MotionMagic, mPeriodicIO.demand, DemandType.ArbitraryFeedForward, mPeriodicIO.feedforward);
            } else if (mDesiredState == SystemState.POSITION_PID) {
                mMaster.set(ControlMode.Position, mPeriodicIO.demand, DemandType.ArbitraryFeedForward, mPeriodicIO.feedforward);
            } else {
                mMaster.set(ControlMode.PercentOutput, mPeriodicIO.demand, DemandType.ArbitraryFeedForward, mPeriodicIO.feedforward);
            }
        }
    
        @Override
        public boolean checkSystem() {
                return true;
        }
    
        public enum SystemState {
            HOMING,
            MOTION_PROFILING,
            POSITION_PID,
            OPEN_LOOP,
        }
    
        public static class PeriodicIO {
            // INPUTS
            public int position_ticks;
            public int velocity_ticks_per_100ms;
            public int active_trajectory_position;
            public int active_trajectory_velocity;
            public double active_trajectory_acceleration_rad_per_s2;
            public double feedforward;
    
            // OUTPUTS
            public double demand;
        }
    }