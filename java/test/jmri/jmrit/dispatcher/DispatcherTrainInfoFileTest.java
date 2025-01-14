package jmri.jmrit.dispatcher;

import jmri.util.JUnitUtil;

import org.junit.jupiter.api.*;
import org.junit.Assert;

/**
 * Swing tests for dispatcher train info.
 *
 * @author Dave Duchamp
 */
public class DispatcherTrainInfoFileTest {

    @Test
    public void testFileRead() throws Exception {

        TrainInfoFile tif = new TrainInfoFile();
        tif.setFileLocation("java/test/jmri/jmrit/dispatcher/traininfo/");
        TrainInfo ti = tif.readTrainInfo("TestTrain.xml");
        // test input information
        Assert.assertEquals("Transit Name", "Red Main Loop CW", ti.getTransitName());
        Assert.assertEquals("Transit Id", "IZ5", ti.getTransitId());
        Assert.assertEquals("Train Name", "GTW 6418", ti.getTrainName());
        Assert.assertEquals("DCC Address", " ", ti.getDccAddress());
        Assert.assertTrue("Train In Transit", ti.getTrainInTransit());
        Assert.assertEquals("Start Block Name", "Red Siding-1", ti.getStartBlockName());
        Assert.assertEquals("Start Block Id", "IB1", ti.getStartBlockId());
        Assert.assertEquals("Start Block Sequ", 1, ti.getStartBlockSeq());

        Assert.assertEquals("Destination Block Name", "Red Siding-7", ti.getDestinationBlockName());
        Assert.assertEquals("Destination Block Id", "IB1", ti.getDestinationBlockId());
        Assert.assertEquals("Destination Block Sequ", 7, ti.getDestinationBlockSeq());
        Assert.assertTrue("Train From Roster", ti.getTrainFromRoster());
        Assert.assertFalse("Train From Trains", ti.getTrainFromTrains());
        Assert.assertFalse("Train From User", ti.getTrainFromUser());
        Assert.assertEquals("Priority", 7, ti.getPriority());
        Assert.assertFalse("Run Auto", ti.getAutoRun());
        Assert.assertFalse("Reset When Done", ti.getResetWhenDone());
        Assert.assertEquals("Delayed Start", 1, ti.getDelayedStart());
        Assert.assertEquals("Departure Time Hours", 8, ti.getDepartureTimeHr());
        Assert.assertEquals("Departure Time Minutes", 10, ti.getDepartureTimeMin());
        Assert.assertEquals("Train Type", "THROUGH_FREIGHT", ti.getTrainType());

        Assert.assertEquals("Speed Factor", ti.getSpeedFactor(), 0.8f, 0.0);
        Assert.assertEquals("Maximum Speed", ti.getMaxSpeed(), 0.6f, 0.0);
        Assert.assertEquals("Ramp Rate", "RAMP_FAST", ti.getRampRate());
        Assert.assertTrue("Resistance Wheels", ti.getResistanceWheels());
        Assert.assertFalse("Run In Reverse", ti.getRunInReverse());
        Assert.assertFalse("Sound Decoder", ti.getSoundDecoder());
        Assert.assertEquals("Maximum Train Length", ti.getMaxTrainLength(), 222.0f, 0.0);

    }

    // Version 2
    @Test
    public void testFileRead_V2() throws Exception {

        TrainInfoFile tif = new TrainInfoFile();
        tif.setFileLocation("java/test/jmri/jmrit/dispatcher/traininfo/");
        TrainInfo ti = tif.readTrainInfo("TestTrainCW_V2.xml");
        // test input information
        Assert.assertEquals("Version is 2",ti.getVersion(),2);
        Assert.assertEquals("Transit Name", "SouthPlatform CW", ti.getTransitName());
        Assert.assertEquals("transitid", "IZ1", ti.getTransitId());
        Assert.assertEquals("Train Name", "1000", ti.getTrainName());
        Assert.assertEquals("DCC Address", "1000", ti.getDccAddress());
        Assert.assertTrue("Train In Transit", ti.getTrainInTransit());
        Assert.assertEquals("Start Block Name", "South Platform-1", ti.getStartBlockName());
        Assert.assertEquals("Start Block Id", "IB:AUTO:0003", ti.getStartBlockId());
        Assert.assertEquals("Start Block Sequ", 1, ti.getStartBlockSeq());
        Assert.assertEquals("Destination Block Name", "South Platform-5", ti.getDestinationBlockName());
        Assert.assertEquals("Destination Block Id", "IB:AUTO:0003", ti.getDestinationBlockId());
        Assert.assertEquals("Destination Block Sequ", 5, ti.getDestinationBlockSeq());
        Assert.assertFalse("Train From Roster", ti.getTrainFromRoster());
        Assert.assertFalse("Train From Trains", ti.getTrainFromTrains());
        Assert.assertTrue("Train From User", ti.getTrainFromUser());
        Assert.assertEquals("Priority", 5, ti.getPriority());
        Assert.assertTrue("Run Auto", ti.getAutoRun());
        Assert.assertFalse("Reset When Done", ti.getResetWhenDone());
        Assert.assertEquals("Delayed Start", 0, ti.getDelayedStart());
        Assert.assertEquals("Start Sensor", null, ti.getDelaySensorName());
        Assert.assertEquals("Departure Time Hours", 8, ti.getDepartureTimeHr());
        Assert.assertEquals("Departure Time Minutes", 0, ti.getDepartureTimeMin());
        Assert.assertEquals("Train Type", "LOCAL_PASSENGER", ti.getTrainType());
        Assert.assertEquals("Speed Factor", ti.getSpeedFactor(), 1.0f, 0.0);
        Assert.assertEquals("Maximum Speed", ti.getMaxSpeed(), 0.6f, 0.0);
        Assert.assertEquals("Ramp Rate", "None", ti.getRampRate());
        Assert.assertTrue("Resistance Wheels", ti.getResistanceWheels());
        Assert.assertFalse("Run In Reverse", ti.getRunInReverse());
        Assert.assertTrue("Sound Decoder", ti.getSoundDecoder());
        Assert.assertEquals("Maximum Train Length", ti.getMaxTrainLength(), 200.0f, 0.0);
        Assert.assertEquals("Allocation Method", ti.getAllocationMethod(),3,0);
        Assert.assertFalse("Use Speed Profile", ti.getUseSpeedProfile());
        Assert.assertEquals("Use Speed Profile Adjust block length", ti.getStopBySpeedProfileAdjust(),1.0f,0.0f);
    }

    // Version 3
    @Test
    public void testFileRead_V3_withReverse() throws Exception {

        TrainInfoFile tif = new TrainInfoFile();
        tif.setFileLocation("java/test/jmri/jmrit/dispatcher/traininfo/");
        TrainInfo ti = tif.readTrainInfo("FWDREV40.xml");
        // test input information
        Assert.assertEquals("Version is 3",ti.getVersion(),3);
        Assert.assertEquals("Transit Name", "StopFWDandREV", ti.getTransitName());
        Assert.assertEquals("transitid", "StopFWDandREV", ti.getTransitId()); // since version 3 same
        Assert.assertEquals("Train Name", "AAA", ti.getTrainName());
        Assert.assertEquals("DCC Address", "3", ti.getDccAddress());
        Assert.assertTrue("Train In Transit", ti.getTrainInTransit());
        Assert.assertEquals("Start Block Name", "Block1-1", ti.getStartBlockName());
        Assert.assertEquals("Start Block Id", "Block1", ti.getStartBlockId());
        Assert.assertEquals("Start Block Sequ", 1, ti.getStartBlockSeq());
        Assert.assertEquals("Destination Block Name", "Block7", ti.getDestinationBlockName());
        Assert.assertEquals("Destination Block Id", "Block7", ti.getDestinationBlockId());
        Assert.assertEquals("Destination Block Sequ", 3, ti.getDestinationBlockSeq());
        Assert.assertFalse("Train From Roster", ti.getTrainFromRoster());
        Assert.assertFalse("Train From Trains", ti.getTrainFromTrains());
        Assert.assertTrue("Train From User", ti.getTrainFromUser());
        Assert.assertEquals("Priority", 5, ti.getPriority());
        Assert.assertTrue("Run Auto", ti.getAutoRun());
        Assert.assertTrue("Reset When Done", ti.getResetWhenDone());
        Assert.assertTrue("Reset restart sensor", ti.getResetRestartSensor());
        Assert.assertEquals("Restart sensor", "TrainRestart", ti.getRestartSensorName());
        Assert.assertEquals("Reverse At End Sensor", "TrainRestart", ti.getReverseRestartSensorName()); // not if file should have defaulted to restart value.
        Assert.assertTrue("Reset Reverse Reverse At End sensor", ti.getReverseResetRestartSensor());
        Assert.assertEquals("Delayed Start", 0, ti.getDelayedStart());
        Assert.assertEquals("Start Sensor", null, ti.getDelaySensorName());
        Assert.assertEquals("Departure Time Hours", 8, ti.getDepartureTimeHr());
        Assert.assertEquals("Departure Time Minutes", 0, ti.getDepartureTimeMin());
        Assert.assertEquals("Train Type", "LOCAL_PASSENGER", ti.getTrainType());
        Assert.assertEquals("Speed Factor", ti.getSpeedFactor(), 1.0f, 0.0);
        Assert.assertEquals("Maximum Speed", ti.getMaxSpeed(), 1.0f, 0.0);
        Assert.assertEquals("Ramp Rate", "None", ti.getRampRate());
        Assert.assertTrue("Resistance Wheels", ti.getResistanceWheels());
        Assert.assertFalse("Run In Reverse", ti.getRunInReverse());
        Assert.assertFalse("Sound Decoder", ti.getSoundDecoder());
        Assert.assertEquals("Maximum Train Length", ti.getMaxTrainLength(), 40.0f, 0.0);
        Assert.assertEquals("Allocation Method", ti.getAllocationMethod(),-1,0);
        Assert.assertFalse("Use Speed Profile", ti.getUseSpeedProfile());
        Assert.assertEquals("Use Speed Profile Adjust block length", ti.getStopBySpeedProfileAdjust(),1.0f,0.0f);
    }

    // Version 4
    @Test
    public void testFileRead_V4_withReverse() throws Exception {

        TrainInfoFile tif = new TrainInfoFile();
        tif.setFileLocation("java/test/jmri/jmrit/dispatcher/traininfo/");
        TrainInfo ti = tif.readTrainInfo("FWDREV120.xml");
        // test input information
        Assert.assertEquals("Version is 4",ti.getVersion(),4);
        Assert.assertEquals("Transit Name", "StopFWDandREV", ti.getTransitName());
        Assert.assertEquals("transitid", "StopFWDandREV", ti.getTransitId()); // since version 3 same
        Assert.assertEquals("Train Name", "AAA", ti.getTrainName());
        Assert.assertEquals("DCC Address", "3", ti.getDccAddress());
        Assert.assertTrue("Train In Transit", ti.getTrainInTransit());
        Assert.assertEquals("Start Block Name", "Block3-1", ti.getStartBlockName());
        Assert.assertEquals("Start Block Sequ", 1, ti.getStartBlockSeq());
        Assert.assertEquals("Destination Block Name", "Block7", ti.getDestinationBlockName());
        Assert.assertEquals("Destination Block Id", "Block7", ti.getDestinationBlockId()); // since 3 both the same
        Assert.assertEquals("Destination Block Sequ", 3, ti.getDestinationBlockSeq());
        Assert.assertFalse("Train From Roster", ti.getTrainFromRoster());
        Assert.assertFalse("Train From Trains", ti.getTrainFromTrains());
        Assert.assertTrue("Train From User", ti.getTrainFromUser());
        Assert.assertEquals("Priority", 5, ti.getPriority());
        Assert.assertTrue("Run Auto", ti.getAutoRun());
        Assert.assertTrue("Reset When Done", ti.getResetWhenDone());
        Assert.assertEquals("Restart sensor", "TrainRestart", ti.getRestartSensorName());
        Assert.assertEquals("Reverse At End Sensor", "TrainReverseRestart", ti.getReverseRestartSensorName());
        Assert.assertFalse("Reset Reverse Reverse At End sensor", ti.getReverseResetRestartSensor());
        Assert.assertEquals("Delayed Start", 0, ti.getDelayedStart());
        Assert.assertEquals("Start Sensor", null, ti.getDelaySensorName());
        Assert.assertEquals("Departure Time Hours", 8, ti.getDepartureTimeHr());
        Assert.assertEquals("Departure Time Minutes", 0, ti.getDepartureTimeMin());
        Assert.assertEquals("Train Type", "LOCAL_PASSENGER", ti.getTrainType());
        Assert.assertEquals("Speed Factor", ti.getSpeedFactor(), 1.0f, 0.0);
        Assert.assertEquals("Maximum Speed", ti.getMaxSpeed(), 1.0f, 0.0);
        Assert.assertEquals("Ramp Rate", "None", ti.getRampRate());
        Assert.assertTrue("Resistance Wheels", ti.getResistanceWheels());
        Assert.assertFalse("Run In Reverse", ti.getRunInReverse());
        Assert.assertFalse("Sound Decoder", ti.getSoundDecoder());
        Assert.assertEquals("Maximum Train Length", ti.getMaxTrainLength(), 120.0f, 0.0);
        Assert.assertEquals("Allocation Method", ti.getAllocationMethod(),-1,0);
        Assert.assertFalse("Use Speed Profile", ti.getUseSpeedProfile());
        Assert.assertEquals("Use Speed Profile Adjust block length", ti.getStopBySpeedProfileAdjust(),1.0f,0.0f);
    }

    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
    }

    @AfterEach
    public void tearDown() {
        JUnitUtil.tearDown();
    }
}
