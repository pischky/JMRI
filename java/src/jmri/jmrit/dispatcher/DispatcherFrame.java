package jmri.jmrit.dispatcher;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.ResourceBundle;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import jmri.Block;
import jmri.EntryPoint;
import jmri.InstanceManager;
import jmri.Scale;
import jmri.Section;
import jmri.Sensor;
import jmri.SignalMast;
import jmri.Timebase;
import jmri.Transit;
import jmri.TransitManager;
import jmri.TransitSection;
import jmri.jmrit.display.layoutEditor.LayoutEditor;
import jmri.jmrit.roster.RosterEntry;
import jmri.util.JmriJFrame;
import jmri.util.table.ButtonEditor;
import jmri.util.table.ButtonRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatcher functionality, working with Sections, Transits and ActiveTrain.
 *
 * <P>
 * Dispatcher serves as the manager for ActiveTrains. All allocation of Sections
 * to ActiveTrains is performed here.
 * <P>
 * Programming Note: public methods may be addressed externally via
 * jmri.jmrit.dispatcher.DispatcherFrame.instance(). ...
 * <P>
 * Dispatcher listens to fast clock minutes to handle all ActiveTrain items tied
 * to fast clock time.
 * <P>
 * Delayed start of manual and automatic trains is enforced by not allocating
 * Sections for trains until the fast clock reaches the departure time.
 * <P>
 * This file is part of JMRI.
 * <P>
 * JMRI is open source software; you can redistribute it and/or modify it under
 * the terms of version 2 of the GNU General Public License as published by the
 * Free Software Foundation. See the "COPYING" file for a copy of this license.
 * <P>
 * JMRI is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * @author	Dave Duchamp Copyright (C) 2008-2011
 */
public class DispatcherFrame extends jmri.util.JmriJFrame {

    /**
     * Get a DispatcherFrame through the instance() method
     */
    private DispatcherFrame() {
        super(false, true);
        initializeOptions();
        openDispatcherWindow();
        autoTurnouts = new AutoTurnouts(this);
        if (autoTurnouts == null) {
            log.error("Failed to create AutoTurnouts object when constructing Dispatcher");
        }
        if (_LE != null) {
            autoAllocate = new AutoAllocate(this);
            if (autoAllocate == null) {
                log.error("Failed to create AutoAllocate object when constructing Dispatcher");
            }
        }
        InstanceManager.sectionManagerInstance().initializeBlockingSensors();
        getActiveTrainFrame();

        if (fastClock == null) {
            log.error("Failed to instantiate a fast clock when constructing Dispatcher");
        } else {
            minuteChangeListener = new java.beans.PropertyChangeListener() {
                public void propertyChange(java.beans.PropertyChangeEvent e) {
                    //process change to new minute
                    newFastClockMinute();
                }
            };
            fastClock.addMinuteChangeListener(minuteChangeListener);
        }
    }

    static final ResourceBundle rb = ResourceBundle
            .getBundle("jmri.jmrit.dispatcher.DispatcherBundle");

    // Dispatcher options (saved to disk if user requests, and restored if present)
    private LayoutEditor _LE = null;
    public static final int SIGNALHEAD = 0x00;
    public static final int SIGNALMAST = 0x01;
    private int _SignalType = SIGNALHEAD;
    private boolean _UseConnectivity = false;
    private boolean _HasOccupancyDetection = false; // "true" if blocks have occupancy detection
    private boolean _TrainsFromRoster = true;
    private boolean _TrainsFromTrains = false;
    private boolean _TrainsFromUser = false;
    private boolean _AutoAllocate = false;
    private boolean _AutoTurnouts = false;
    private boolean _TrustKnownTurnouts = false;
    private boolean _ShortActiveTrainNames = false;
    private boolean _ShortNameInBlock = true;
    private boolean _RosterEntryInBlock = false;
    private boolean _ExtraColorForAllocated = true;
    private boolean _NameInAllocatedBlock = false;
    private boolean _UseScaleMeters = false;  // "true" if scale meters, "false" for scale feet
    private int _LayoutScale = Scale.HO;
    private boolean _SupportVSDecoder = false;
    private int _MinThrottleInterval = 100; //default time (in ms) between consecutive throttle commands
    private int _FullRampTime = 10000; //default time (in ms) for RAMP_FAST to go from 0% to 100%

    // operational instance variables
    private ArrayList<ActiveTrain> activeTrainsList = new ArrayList<ActiveTrain>();  // list of ActiveTrain objects
    private ArrayList<java.beans.PropertyChangeListener> _atListeners
            = new ArrayList<java.beans.PropertyChangeListener>();
    private ArrayList<ActiveTrain> delayedTrains = new ArrayList<ActiveTrain>();  // list of delayed Active Trains
    private ArrayList<ActiveTrain> restartingTrainsList = new ArrayList<ActiveTrain>();  // list of Active Trains with restart requests
    private TransitManager transitManager = InstanceManager.transitManagerInstance();
    private ArrayList<AllocationRequest> allocationRequests = new ArrayList<AllocationRequest>();  // List of AllocatedRequest objects
    private ArrayList<AllocatedSection> allocatedSections = new ArrayList<AllocatedSection>();  // List of AllocatedSection objects
    private boolean optionsRead = false;
    private AutoTurnouts autoTurnouts = null;
    private AutoAllocate autoAllocate = null;
    private OptionsMenu optionsMenu = null;
    private ActivateTrainFrame atFrame = null;

    public ActivateTrainFrame getActiveTrainFrame() {
        if (atFrame == null) {
            atFrame = new ActivateTrainFrame(this);
            if (atFrame == null) {
                log.error("Failed to create ActivateTrainFrame object when constructing Dispatcher");
            }
        }
        return atFrame;
    }
    private boolean newTrainActive = false;

    public boolean getNewTrainActive() {
        return newTrainActive;
    }

    public void setNewTrainActive(boolean boo) {
        newTrainActive = boo;
    }
    private AutoTrainsFrame _autoTrainsFrame = null;
    private Timebase fastClock = InstanceManager.timebaseInstance();
    private Sensor fastClockSensor = InstanceManager.sensorManagerInstance().provideSensor("ISCLOCKRUNNING");
    private transient java.beans.PropertyChangeListener minuteChangeListener = null;

    // dispatcher window variables
    protected JmriJFrame dispatcherFrame = null;
    private Container contentPane = null;
    private ActiveTrainsTableModel activeTrainsTableModel = null;
    private JButton addTrainButton = null;
    private JButton terminateTrainButton = null;
    private JButton cancelRestartButton = null;
    private JButton allocateExtraButton = null;
    private JCheckBox autoReleaseBox = null;
    private JCheckBox autoAllocateBox = null;
    private AllocationRequestTableModel allocationRequestTableModel = null;
    private AllocatedSectionTableModel allocatedSectionTableModel = null;

    void initializeOptions() {
        if (optionsRead) {
            return;
        }
        optionsRead = true;
        try {
            OptionsFile.instance().readDispatcherOptions(this);
        } catch (org.jdom2.JDOMException jde) {
            log.error("JDOM Exception when retreiving dispatcher options " + jde);
        } catch (java.io.IOException ioe) {
            log.error("I/O Exception when retreiving dispatcher options " + ioe);
        }
    }

    void openDispatcherWindow() {
        if (dispatcherFrame == null) {
            dispatcherFrame = this;
            dispatcherFrame.setTitle(rb.getString("TitleDispatcher"));
            JMenuBar menuBar = new JMenuBar();
            optionsMenu = new OptionsMenu(this);
            menuBar.add(optionsMenu);
            setJMenuBar(menuBar);
            dispatcherFrame.addHelpMenu("package.jmri.jmrit.dispatcher.Dispatcher", true);
            contentPane = dispatcherFrame.getContentPane();
            contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
            // set up active trains table
            JPanel p11 = new JPanel();
            p11.setLayout(new FlowLayout());
            p11.add(new JLabel(rb.getString("ActiveTrainTableTitle")));
            contentPane.add(p11);
            JPanel p12 = new JPanel();
            p12.setLayout(new FlowLayout());
            activeTrainsTableModel = new ActiveTrainsTableModel();
            JTable activeTrainsTable = new JTable(activeTrainsTableModel);
            activeTrainsTable.setRowSelectionAllowed(false);
            activeTrainsTable.setPreferredScrollableViewportSize(new java.awt.Dimension(950, 160));
            TableColumnModel activeTrainsColumnModel = activeTrainsTable.getColumnModel();
            TableColumn transitColumn = activeTrainsColumnModel.getColumn(ActiveTrainsTableModel.TRANSIT_COLUMN);
            transitColumn.setResizable(true);
            transitColumn.setMinWidth(140);
            transitColumn.setMaxWidth(220);
            TableColumn trainColumn = activeTrainsColumnModel.getColumn(ActiveTrainsTableModel.TRAIN_COLUMN);
            trainColumn.setResizable(true);
            trainColumn.setMinWidth(90);
            trainColumn.setMaxWidth(160);
            TableColumn typeColumn = activeTrainsColumnModel.getColumn(ActiveTrainsTableModel.TYPE_COLUMN);
            typeColumn.setResizable(true);
            typeColumn.setMinWidth(130);
            typeColumn.setMaxWidth(190);
            TableColumn statusColumn = activeTrainsColumnModel.getColumn(ActiveTrainsTableModel.STATUS_COLUMN);
            statusColumn.setResizable(true);
            statusColumn.setMinWidth(90);
            statusColumn.setMaxWidth(140);
            TableColumn modeColumn = activeTrainsColumnModel.getColumn(ActiveTrainsTableModel.MODE_COLUMN);
            modeColumn.setResizable(true);
            modeColumn.setMinWidth(90);
            modeColumn.setMaxWidth(140);
            TableColumn allocatedColumn = activeTrainsColumnModel.getColumn(ActiveTrainsTableModel.ALLOCATED_COLUMN);
            allocatedColumn.setResizable(true);
            allocatedColumn.setMinWidth(120);
            allocatedColumn.setMaxWidth(200);
            TableColumn nextSectionColumn = activeTrainsColumnModel.getColumn(ActiveTrainsTableModel.NEXTSECTION_COLUMN);
            nextSectionColumn.setResizable(true);
            nextSectionColumn.setMinWidth(120);
            nextSectionColumn.setMaxWidth(200);
            TableColumn allocateButtonColumn = activeTrainsColumnModel.getColumn(ActiveTrainsTableModel.ALLOCATEBUTTON_COLUMN);
            allocateButtonColumn.setCellEditor(new ButtonEditor(new JButton()));
            allocateButtonColumn.setMinWidth(110);
            allocateButtonColumn.setMaxWidth(190);
            allocateButtonColumn.setResizable(false);
            ButtonRenderer buttonRenderer = new ButtonRenderer();
            activeTrainsTable.setDefaultRenderer(JButton.class, buttonRenderer);
            JButton sampleButton = new JButton(rb.getString("AllocateButtonName"));
            activeTrainsTable.setRowHeight(sampleButton.getPreferredSize().height);
            allocateButtonColumn.setPreferredWidth((sampleButton.getPreferredSize().width) + 2);
            JScrollPane activeTrainsTableScrollPane = new JScrollPane(activeTrainsTable);
            p12.add(activeTrainsTableScrollPane, BorderLayout.CENTER);
            contentPane.add(p12);
            JPanel p13 = new JPanel();
            p13.setLayout(new FlowLayout());
            p13.add(addTrainButton = new JButton(rb.getString("InitiateTrain") + "..."));
            addTrainButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (!newTrainActive) {
                        atFrame.initiateTrain(e);
                        newTrainActive = true;
                    } else {
                        atFrame.showActivateFrame();
                    }
                }
            });
            addTrainButton.setToolTipText(rb.getString("InitiateTrainButtonHint"));
            p13.add(new JLabel("   "));
            p13.add(new JLabel("   "));
            p13.add(allocateExtraButton = new JButton(rb.getString("AllocateExtra") + "..."));
            allocateExtraButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    allocateExtraSection(e);
                }
            });
            allocateExtraButton.setToolTipText(rb.getString("AllocateExtraButtonHint"));
            p13.add(new JLabel("   "));
            p13.add(cancelRestartButton = new JButton(rb.getString("CancelRestart") + "..."));
            cancelRestartButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (!newTrainActive) {
                        cancelRestart(e);
                    } else if (restartingTrainsList.size() > 0) {
                        atFrame.showActivateFrame();
                        JOptionPane.showMessageDialog(dispatcherFrame, rb.getString("Message2"),
                                rb.getString("MessageTitle"), JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        atFrame.showActivateFrame();
                    }
                }
            });
            cancelRestartButton.setToolTipText(rb.getString("CancelRestartButtonHint"));
            p13.add(new JLabel("   "));
            p13.add(terminateTrainButton = new JButton(rb.getString("TerminateTrain") + "..."));
            terminateTrainButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (!newTrainActive) {
                        terminateTrain(e);
                    } else if (activeTrainsList.size() > 0) {
                        atFrame.showActivateFrame();
                        JOptionPane.showMessageDialog(dispatcherFrame, rb.getString("Message1"),
                                rb.getString("MessageTitle"), JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        atFrame.showActivateFrame();
                    }
                }
            });
            terminateTrainButton.setToolTipText(rb.getString("TerminateTrainButtonHint"));
            contentPane.add(p13);
            // set up pending allocations table
            contentPane.add(new JSeparator());
            JPanel p21 = new JPanel();
            p21.setLayout(new FlowLayout());
            p21.add(new JLabel(rb.getString("RequestedAllocationsTableTitle")));
            contentPane.add(p21);
            JPanel p22 = new JPanel();
            p22.setLayout(new FlowLayout());
            allocationRequestTableModel = new AllocationRequestTableModel();
            JTable allocationRequestTable = new JTable(allocationRequestTableModel);
            allocationRequestTable.setRowSelectionAllowed(false);
            allocationRequestTable.setPreferredScrollableViewportSize(new java.awt.Dimension(950, 100));
            TableColumnModel allocationRequestColumnModel = allocationRequestTable.getColumnModel();
            TableColumn activeColumn = allocationRequestColumnModel.getColumn(AllocationRequestTableModel.ACTIVE_COLUMN);
            activeColumn.setResizable(true);
            activeColumn.setMinWidth(210);
            activeColumn.setMaxWidth(260);
            TableColumn priorityColumn = allocationRequestColumnModel.getColumn(AllocationRequestTableModel.PRIORITY_COLUMN);
            priorityColumn.setResizable(true);
            priorityColumn.setMinWidth(40);
            priorityColumn.setMaxWidth(60);
            TableColumn trainTypColumn = allocationRequestColumnModel.getColumn(AllocationRequestTableModel.TRAINTYPE_COLUMN);
            trainTypColumn.setResizable(true);
            trainTypColumn.setMinWidth(130);
            trainTypColumn.setMaxWidth(190);
            TableColumn sectionColumn = allocationRequestColumnModel.getColumn(AllocationRequestTableModel.SECTION_COLUMN);
            sectionColumn.setResizable(true);
            sectionColumn.setMinWidth(140);
            sectionColumn.setMaxWidth(210);
            TableColumn secStatusColumn = allocationRequestColumnModel.getColumn(AllocationRequestTableModel.STATUS_COLUMN);
            secStatusColumn.setResizable(true);
            secStatusColumn.setMinWidth(90);
            secStatusColumn.setMaxWidth(150);
            TableColumn occupancyColumn = allocationRequestColumnModel.getColumn(AllocationRequestTableModel.OCCUPANCY_COLUMN);
            occupancyColumn.setResizable(true);
            occupancyColumn.setMinWidth(80);
            occupancyColumn.setMaxWidth(130);
            TableColumn secLengthColumn = allocationRequestColumnModel.getColumn(AllocationRequestTableModel.SECTIONLENGTH_COLUMN);
            secLengthColumn.setResizable(true);
            secLengthColumn.setMinWidth(40);
            secLengthColumn.setMaxWidth(60);
            TableColumn allocateColumn = allocationRequestColumnModel.getColumn(AllocationRequestTableModel.ALLOCATEBUTTON_COLUMN);
            allocateColumn.setCellEditor(new ButtonEditor(new JButton()));
            allocateColumn.setMinWidth(90);
            allocateColumn.setMaxWidth(170);
            allocateColumn.setResizable(false);
            allocationRequestTable.setDefaultRenderer(JButton.class, buttonRenderer);
            sampleButton = new JButton(rb.getString("AllocateButton"));
            allocationRequestTable.setRowHeight(sampleButton.getPreferredSize().height);
            allocateColumn.setPreferredWidth((sampleButton.getPreferredSize().width) + 2);
            TableColumn cancelButtonColumn = allocationRequestColumnModel.getColumn(AllocationRequestTableModel.CANCELBUTTON_COLUMN);
            cancelButtonColumn.setCellEditor(new ButtonEditor(new JButton()));
            cancelButtonColumn.setMinWidth(75);
            cancelButtonColumn.setMaxWidth(170);
            cancelButtonColumn.setResizable(false);
            cancelButtonColumn.setPreferredWidth((sampleButton.getPreferredSize().width) + 2);
            JScrollPane allocationRequestTableScrollPane = new JScrollPane(allocationRequestTable);
            p22.add(allocationRequestTableScrollPane, BorderLayout.CENTER);
            contentPane.add(p22);
            // set up allocated sections table
            contentPane.add(new JSeparator());
            JPanel p30 = new JPanel();
            p30.setLayout(new FlowLayout());
            p30.add(new JLabel(rb.getString("AllocatedSectionsTitle") + "    "));
            autoReleaseBox = new JCheckBox(rb.getString("AutoReleaseBoxLabel"));
            p30.add(autoReleaseBox);
            autoReleaseBox.setToolTipText(rb.getString("AutoReleaseBoxHint"));
            autoReleaseBox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    handleAutoReleaseChanged(e);
                }
            });
            autoReleaseBox.setSelected(_AutoAllocate);  // initiallize autoRelease to match autoAllocate
            autoAllocateBox = new JCheckBox(rb.getString("AutoDispatchItem"));
            p30.add(autoAllocateBox);
            autoAllocateBox.setToolTipText(rb.getString("AutoAllocateBoxHint"));
            autoAllocateBox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    handleAutoAllocateChanged(e);
                }
            });
            contentPane.add(p30);
            autoAllocateBox.setSelected(_AutoAllocate);
            JPanel p31 = new JPanel();
            p31.setLayout(new FlowLayout());
            allocatedSectionTableModel = new AllocatedSectionTableModel();
            JTable allocatedSectionTable = new JTable(allocatedSectionTableModel);
            allocatedSectionTable.setRowSelectionAllowed(false);
            allocatedSectionTable.setPreferredScrollableViewportSize(new java.awt.Dimension(730, 200));
            TableColumnModel allocatedSectionColumnModel = allocatedSectionTable.getColumnModel();
            TableColumn activeAColumn = allocatedSectionColumnModel.getColumn(AllocatedSectionTableModel.ACTIVE_COLUMN);
            activeAColumn.setResizable(true);
            activeAColumn.setMinWidth(250);
            activeAColumn.setMaxWidth(350);
            TableColumn sectionAColumn = allocatedSectionColumnModel.getColumn(AllocatedSectionTableModel.SECTION_COLUMN);
            sectionAColumn.setResizable(true);
            sectionAColumn.setMinWidth(200);
            sectionAColumn.setMaxWidth(350);
            TableColumn occupancyAColumn = allocatedSectionColumnModel.getColumn(AllocatedSectionTableModel.OCCUPANCY_COLUMN);
            occupancyAColumn.setResizable(true);
            occupancyAColumn.setMinWidth(80);
            occupancyAColumn.setMaxWidth(140);
            TableColumn useStatusColumn = allocatedSectionColumnModel.getColumn(AllocatedSectionTableModel.USESTATUS_COLUMN);
            useStatusColumn.setResizable(true);
            useStatusColumn.setMinWidth(90);
            useStatusColumn.setMaxWidth(150);
            TableColumn releaseColumn = allocatedSectionColumnModel.getColumn(AllocatedSectionTableModel.RELEASEBUTTON_COLUMN);
            releaseColumn.setCellEditor(new ButtonEditor(new JButton()));
            releaseColumn.setMinWidth(90);
            releaseColumn.setMaxWidth(170);
            releaseColumn.setResizable(false);
            allocatedSectionTable.setDefaultRenderer(JButton.class, buttonRenderer);
            JButton sampleAButton = new JButton(rb.getString("ReleaseButton"));
            allocatedSectionTable.setRowHeight(sampleAButton.getPreferredSize().height);
            releaseColumn.setPreferredWidth((sampleAButton.getPreferredSize().width) + 2);
            JScrollPane allocatedSectionTableScrollPane = new JScrollPane(allocatedSectionTable);
            p31.add(allocatedSectionTableScrollPane, BorderLayout.CENTER);
            contentPane.add(p31);
        }
        dispatcherFrame.pack();
        dispatcherFrame.setVisible(true);
    }

    void releaseAllocatedSectionFromTable(int index) {
        AllocatedSection as = allocatedSections.get(index);
        releaseAllocatedSection(as, false);
    }

    // allocate extra window variables
    private JmriJFrame extraFrame = null;
    private Container extraPane = null;
    private JComboBox<String> atSelectBox = new JComboBox<String>();
    private JComboBox<String> extraBox = new JComboBox<String>();
    private ArrayList<Section> extraBoxList = new ArrayList<Section>();
    private int atSelectedIndex = -1;

    public void allocateExtraSection(ActionEvent e, ActiveTrain at) {
        allocateExtraSection(e);
        if (_ShortActiveTrainNames) {
            atSelectBox.setSelectedItem(at.getTrainName());
        } else {
            atSelectBox.setSelectedItem(at.getActiveTrainName());
        }
    }

    // allocate an extra Section to an Active Train
    private void allocateExtraSection(ActionEvent e) {
        if (extraFrame == null) {
            extraFrame = new JmriJFrame(rb.getString("ExtraTitle"));
            extraFrame.addHelpMenu("package.jmri.jmrit.dispatcher.AllocateExtra", true);
            extraPane = extraFrame.getContentPane();
            extraPane.setLayout(new BoxLayout(extraFrame.getContentPane(), BoxLayout.Y_AXIS));
            JPanel p1 = new JPanel();
            p1.setLayout(new FlowLayout());
            p1.add(new JLabel(rb.getString("ActiveColumnTitle") + ":"));
            p1.add(atSelectBox);
            atSelectBox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    handleATSelectionChanged(e);
                }
            });
            atSelectBox.setToolTipText(rb.getString("ATBoxHint"));
            extraPane.add(p1);
            JPanel p2 = new JPanel();
            p2.setLayout(new FlowLayout());
            p2.add(new JLabel(rb.getString("ExtraBoxLabel") + ":"));
            p2.add(extraBox);
            extraBox.setToolTipText(rb.getString("ExtraBoxHint"));
            extraPane.add(p2);
            JPanel p7 = new JPanel();
            p7.setLayout(new FlowLayout());
            JButton cancelButton = null;
            p7.add(cancelButton = new JButton(rb.getString("CancelButton")));
            cancelButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    cancelExtraRequested(e);
                }
            });
            cancelButton.setToolTipText(rb.getString("CancelExtraHint"));
            p7.add(new JLabel("    "));
            JButton aExtraButton = null;
            p7.add(aExtraButton = new JButton(rb.getString("AllocateButton")));
            aExtraButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    addExtraRequested(e);
                }
            });
            aExtraButton.setToolTipText(rb.getString("AllocateButtonHint"));
            extraPane.add(p7);
        }
        initializeATComboBox();
        initializeExtraComboBox();
        extraFrame.pack();
        extraFrame.setVisible(true);
    }

    private void handleAutoAllocateChanged(ActionEvent e) {
        setAutoAllocate(autoAllocateBox.isSelected());
        if (optionsMenu != null) {
            optionsMenu.initializeMenu();
        }
        if (_AutoAllocate) {
            autoAllocate.scanAllocationRequestList(allocationRequests);
        }
    }

    protected void forceScanOfAllocation() {
        if (_AutoAllocate) {
            autoAllocate.scanAllocationRequestList(allocationRequests);
        }
    }

    private void handleAutoReleaseChanged(ActionEvent e) {
        if (autoReleaseBox.isSelected()) {
            checkAutoRelease();
        }
    }

    private void handleATSelectionChanged(ActionEvent e) {
        atSelectedIndex = atSelectBox.getSelectedIndex();
        initializeExtraComboBox();
        extraFrame.pack();
        extraFrame.setVisible(true);
    }

    private void initializeATComboBox() {
        atSelectedIndex = -1;
        atSelectBox.removeAllItems();
        for (int i = 0; i < activeTrainsList.size(); i++) {
            ActiveTrain at = activeTrainsList.get(i);
            if (_ShortActiveTrainNames) {
                atSelectBox.addItem(at.getTrainName());
            } else {
                atSelectBox.addItem(at.getActiveTrainName());
            }
        }
        if (activeTrainsList.size() > 0) {
            atSelectBox.setSelectedIndex(0);
            atSelectedIndex = 0;
        }
    }

    private void initializeExtraComboBox() {
        extraBox.removeAllItems();
        extraBoxList.clear();
        if (atSelectedIndex < 0) {
            return;
        }
        ActiveTrain at = activeTrainsList.get(atSelectedIndex);
        //Transit t = at.getTransit();
        ArrayList<AllocatedSection> allocatedSectionList = at.getAllocatedSectionList();
        ArrayList<String> allSections = (ArrayList<String>) InstanceManager.sectionManagerInstance().getSystemNameList();
        for (int j = 0; j < allSections.size(); j++) {
            Section s = InstanceManager.sectionManagerInstance().getSection(allSections.get(j));
            if (s.getState() == Section.FREE) {
                // not already allocated, check connectivity to this train's allocated sections
                boolean connected = false;
                for (int k = 0; k < allocatedSectionList.size(); k++) {
                    if (connected(s, allocatedSectionList.get(k).getSection())) {
                        connected = true;
                    }
                }
                if (connected) {
                    // add to the combo box, not allocated and connected to allocated
                    extraBoxList.add(s);
                    extraBox.addItem(getSectionName(s));
                }
            }
        }
        if (extraBoxList.size() > 0) {
            extraBox.setSelectedIndex(0);
        }
    }

    private boolean connected(Section s1, Section s2) {
        if ((s1 != null) && (s2 != null)) {
            ArrayList<EntryPoint> s1Entries = (ArrayList<EntryPoint>) s1.getEntryPointList();
            ArrayList<EntryPoint> s2Entries = (ArrayList<EntryPoint>) s2.getEntryPointList();
            for (int i = 0; i < s1Entries.size(); i++) {
                Block b = s1Entries.get(i).getFromBlock();
                for (int j = 0; j < s2Entries.size(); j++) {
                    if (b == s2Entries.get(j).getBlock()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public String getSectionName(Section sec) {
        String s = sec.getSystemName();
        String u = sec.getUserName();
        if ((u != null) && (!u.equals("") && (!u.equals(s)))) {
            return (s + "(" + u + ")");
        }
        return s;
    }

    private void cancelExtraRequested(ActionEvent e) {
        extraFrame.setVisible(false);
        extraFrame.dispose();   // prevent listing in the Window menu.
        extraFrame = null;
    }

    private void addExtraRequested(ActionEvent e) {
        int index = extraBox.getSelectedIndex();
        if ((atSelectedIndex < 0) || (index < 0)) {
            cancelExtraRequested(e);
            return;
        }
        ActiveTrain at = activeTrainsList.get(atSelectedIndex);
        Transit t = at.getTransit();
        Section s = extraBoxList.get(index);
        //Section ns = null;
        AllocationRequest ar = null;
        boolean requested = false;
        if (t.containsSection(s)) {
            if (s == at.getNextSectionToAllocate()) {
                // this is a request that the next section in the transit be allocated
                allocateNextRequested(atSelectedIndex);
                return;
            } else {
                // requesting allocation of a section in the Transit, but not the next Section
                int seq = -99;
                ArrayList<Integer> seqList = t.getSeqListBySection(s);
                if (seqList.size() > 0) {
                    seq = seqList.get(0).intValue();
                }
                if (seqList.size() > 1) {
                    // this section is in the Transit multiple times 
                    int test = at.getNextSectionSeqNumber() - 1;
                    int diff = java.lang.Math.abs(seq - test);
                    for (int i = 1; i < seqList.size(); i++) {
                        if (diff > java.lang.Math.abs(test - seqList.get(i).intValue())) {
                            seq = seqList.get(i).intValue();
                            diff = java.lang.Math.abs(seq - test);
                        }
                    }
                }
                requested = requestAllocation(at, s, at.getAllocationDirectionFromSectionAndSeq(s, seq),
                        seq, true, extraFrame);
                ar = findAllocationRequestInQueue(s, seq,
                        at.getAllocationDirectionFromSectionAndSeq(s, seq), at);
            }
        } else {
            // requesting allocation of a section outside of the Transit, direction set arbitrary
            requested = requestAllocation(at, s, Section.FORWARD, -99, true, extraFrame);
            ar = findAllocationRequestInQueue(s, -99, Section.FORWARD, at);
        }
        // if allocation request is OK, allocate the Section, if not already allocated
        if (requested && (ar != null)) {
            allocateSection(ar, null);
        }
        if (extraFrame != null) {
            extraFrame.setVisible(false);
            extraFrame.dispose();   // prevent listing in the Window menu.
            extraFrame = null;
        }
    }

    /**
     * This method is for use to extend the allocation of a section to a active
     * train Its use is to allow a dispatcher to manually route a train to its
     * final destination
     */
    public boolean extendActiveTrainsPath(Section s, ActiveTrain at, JmriJFrame jFrame) {
        if (s.getEntryPointFromSection(at.getEndBlockSection(), Section.FORWARD) != null
                && at.getNextSectionToAllocate() == null) {

            int seq = at.getEndBlockSectionSequenceNumber() + 1;
            if (!at.addEndSection(s, seq)) {
                return false;
            }
            jmri.TransitSection ts = new jmri.TransitSection(s, seq, Section.FORWARD);
            ts.setTemporary(true);
            at.getTransit().addTransitSection(ts);

            // requesting allocation of a section outside of the Transit, direction set arbitrary
            boolean requested = requestAllocation(at, s, Section.FORWARD, seq, true, jFrame);

            AllocationRequest ar = findAllocationRequestInQueue(s, seq, Section.FORWARD, at);
            // if allocation request is OK, force an allocation the Section so that the dispatcher can then allocate futher paths through
            if (requested && (ar != null)) {
                allocateSection(ar, null);
                return true;
            }
        }
        return false;
    }

    public boolean removeFromActiveTrainPath(Section s, ActiveTrain at, JmriJFrame jFrame) {
        if (s == null || at == null) {
            return false;
        }
        if (at.getEndBlockSection() != s) {
            log.error("Active trains end section " + at.getEndBlockSection().getDisplayName() + " is not the same as the requested section to remove " + s.getDisplayName());
            return false;
        }
        if (!at.getTransit().removeLastTemporarySection(s)) {
            return false;
        }

        //Need to find allocation and remove from list.
        for (int k = allocatedSections.size(); k > 0; k--) {
            if (at == allocatedSections.get(k - 1).getActiveTrain()
                    && allocatedSections.get(k - 1).getSection() == s) {
                releaseAllocatedSection(allocatedSections.get(k - 1), true);
            }
        }
        at.removeLastAllocatedSection();
        return true;
    }

    // cancel the automatic restart request of an Active Train from the button in the Dispatcher window
    void cancelRestart(ActionEvent e) {
        ActiveTrain at = null;
        if (restartingTrainsList.size() == 1) {
            at = restartingTrainsList.get(0);
        } else if (restartingTrainsList.size() > 1) {
            Object choices[] = new Object[restartingTrainsList.size()];
            for (int i = 0; i < restartingTrainsList.size(); i++) {
                if (_ShortActiveTrainNames) {
                    choices[i] = restartingTrainsList.get(i).getTrainName();
                } else {
                    choices[i] = restartingTrainsList.get(i).getActiveTrainName();
                }
            }
            Object selName = JOptionPane.showInputDialog(dispatcherFrame,
                    rb.getString("CancelRestartChoice"),
                    rb.getString("CancelRestartTitle"), JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
            if (selName == null) {
                return;
            }
            for (int j = 0; j < restartingTrainsList.size(); j++) {
                if (selName.equals(choices[j])) {
                    at = restartingTrainsList.get(j);
                }
            }
        }
        if (at != null) {
            at.setResetWhenDone(false);
            for (int j = restartingTrainsList.size(); j > 0; j--) {
                if (restartingTrainsList.get(j - 1) == at) {
                    restartingTrainsList.remove(j - 1);
                    return;
                }
            }
        }
    }

    // terminate an Active Train from the button in the Dispatcher window
    void terminateTrain(ActionEvent e) {
        ActiveTrain at = null;
        if (activeTrainsList.size() == 1) {
            at = activeTrainsList.get(0);
        } else if (activeTrainsList.size() > 1) {
            Object choices[] = new Object[activeTrainsList.size()];
            for (int i = 0; i < activeTrainsList.size(); i++) {
                if (_ShortActiveTrainNames) {
                    choices[i] = activeTrainsList.get(i).getTrainName();
                } else {
                    choices[i] = activeTrainsList.get(i).getActiveTrainName();
                }
            }
            Object selName = JOptionPane.showInputDialog(dispatcherFrame,
                    rb.getString("TerminateTrainChoice"),
                    rb.getString("TerminateTrainTitle"), JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
            if (selName == null) {
                return;
            }
            for (int j = 0; j < activeTrainsList.size(); j++) {
                if (selName.equals(choices[j])) {
                    at = activeTrainsList.get(j);
                }
            }
        }
        if (at != null) {
            terminateActiveTrain(at);
        }
    }

    // allocate the next section for an ActiveTrain at dispatcher's request
    void allocateNextRequested(int index) {
        // set up an Allocation Request
        ActiveTrain at = activeTrainsList.get(index);
        Section next = at.getNextSectionToAllocate();
        if (next == null) {
            return;
        }
        int seqNext = at.getNextSectionSeqNumber();
        int dirNext = at.getAllocationDirectionFromSectionAndSeq(next, seqNext);
        if (requestAllocation(at, next, dirNext, seqNext, true, dispatcherFrame)) {
            AllocationRequest ar = findAllocationRequestInQueue(next, seqNext, dirNext, at);
            if (ar == null) {
                return;
            }
            // attempt to allocate
            allocateSection(ar, null);
        }
    }

    /**
     * Creates a new ActiveTrain, and registers it with Dispatcher
     * <P>
     * Required input entries: transitID - system or user name of a Transit in
     * the Transit Table trainID - any text that identifies the train tSource -
     * either ROSTER, OPERATIONS, or USER (see ActiveTrain.java) startBlockName
     * - system or user name of Block where train currently resides
     * startBlockSectionSequenceNumber - sequence number in the Transit of the
     * Section containing the startBlock (if the startBlock is within the
     * Transit) , or of the Section the train will enter from the startBlock (if
     * the startBlock is outside the Transit). endBlockName - system or user
     * name of Block where train will end up after its transit
     * endBlockSectionSequenceNumber - sequence number in the Transit of the
     * Section containing the endBlock. autoRun - set to "true" if computer is
     * to run the train automatically, otherwise "false" dccAddress - required
     * if "autoRun" is "true", set to null otherwise priority - any integer,
     * higher number is higher priority. Used to arbitrate allocation request
     * conflicts resetWhenDone - set to "true" if the Active Train is capable of
     * continuous running and the user has requested that it be automatically
     * reset for another run thru its Transit each time it completes running
     * through its Transit. showErrorMessages - "true" if error message dialogs
     * are to be displayed for detected errors Set to "false" to suppress error
     * message dialogs from this method. frame - window request is from, or
     * "null" if not from a window
     * <P>
     * Returns an ActiveTrain object if successful, returns "null" otherwise
     */
    public ActiveTrain createActiveTrain(String transitID, String trainID, int tSource, String startBlockName,
            int startBlockSectionSequenceNumber, String endBlockName, int endBlockSectionSequenceNumber,
            boolean autoRun, String dccAddress, int priority, boolean resetWhenDone, boolean reverseAtEnd,
            boolean showErrorMessages, JmriJFrame frame) {
        // validate input
        Transit t = transitManager.getTransit(transitID);
        if (t == null) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                        "Error1"), new Object[]{transitID}), rb.getString("ErrorTitle"),
                        JOptionPane.ERROR_MESSAGE);
            }
            log.error("Bad Transit name '" + transitID + "' when attempting to create an Active Train");
            return null;
        }
        if (t.getState() != Transit.IDLE) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                        "Error2"), new Object[]{transitID}), rb.getString("ErrorTitle"),
                        JOptionPane.ERROR_MESSAGE);
            }
            log.error("Transit '" + transitID + "' not IDLE, cannot create an Active Train");
            return null;
        }
        if ((trainID == null) || trainID.equals("")) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, rb.getString("Error3"),
                        rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
            }
            log.error("TrainID string not provided, cannot create an Active Train");
            return null;
        }
        if ((tSource != ActiveTrain.ROSTER) && (tSource != ActiveTrain.OPERATIONS)
                && (tSource != ActiveTrain.USER)) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, rb.getString("Error21"),
                        rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
            }
            log.error("Train source is invalid - " + tSource + " - cannot create an Active Train");
            return null;
        }
        Block startBlock = InstanceManager.blockManagerInstance().getBlock(startBlockName);
        if (startBlock == null) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                        "Error4"), new Object[]{startBlockName}), rb.getString("ErrorTitle"),
                        JOptionPane.ERROR_MESSAGE);
            }
            log.error("Bad startBlockName '" + startBlockName + "' when attempting to create an Active Train");
            return null;
        }
        if (isInAllocatedSection(startBlock)) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                        "Error5"), new Object[]{startBlock.getDisplayName()}), rb.getString("ErrorTitle"),
                        JOptionPane.ERROR_MESSAGE);
            }
            log.error("Start block '" + startBlockName + "' in allocated Section, cannot create an Active Train");
            return null;
        }
        if (_HasOccupancyDetection && (!(startBlock.getState() == Block.OCCUPIED))) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                        "Error6"), new Object[]{startBlock.getDisplayName()}), rb.getString("ErrorTitle"),
                        JOptionPane.ERROR_MESSAGE);
            }
            log.error("No train in start block '" + startBlockName + "', cannot create an Active Train");
            return null;
        }
        if (startBlockSectionSequenceNumber <= 0) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, rb.getString("Error12"),
                        rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
            }
        } else if (startBlockSectionSequenceNumber > t.getMaxSequence()) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                        "Error13"), new Object[]{"" + startBlockSectionSequenceNumber}),
                        rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
            }
            log.error("Invalid sequence number '" + startBlockSectionSequenceNumber + "' when attempting to create an Active Train");
            return null;
        }
        Block endBlock = InstanceManager.blockManagerInstance().getBlock(endBlockName);
        if ((endBlock == null) || (!t.containsBlock(endBlock))) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                        "Error7"), new Object[]{endBlockName}), rb.getString("ErrorTitle"),
                        JOptionPane.ERROR_MESSAGE);
            }
            log.error("Bad endBlockName '" + endBlockName + "' when attempting to create an Active Train");
            return null;
        }
        if ((endBlockSectionSequenceNumber <= 0) && (t.getBlockCount(endBlock) > 1)) {
            JOptionPane.showMessageDialog(frame, rb.getString("Error8"),
                    rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
        } else if (endBlockSectionSequenceNumber > t.getMaxSequence()) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                        "Error9"), new Object[]{"" + endBlockSectionSequenceNumber}),
                        rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
            }
            log.error("Invalid sequence number '" + endBlockSectionSequenceNumber + "' when attempting to create an Active Train");
            return null;
        }
        if ((!reverseAtEnd) && resetWhenDone && (!t.canBeResetWhenDone())) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                        "Error26"), new Object[]{(t.getSystemName() + "(" + t.getUserName() + ")")}),
                        rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
            }
            log.error("Incompatible Transit set up and request to Reset When Done when attempting to create an Active Train");
            return null;
        }
        if (autoRun && ((dccAddress == null) || dccAddress.equals(""))) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, rb.getString("Error10"),
                        rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
            }
            log.error("AutoRun requested without a dccAddress when attempting to create an Active Train");
            return null;
        }
        if (autoRun) {
            if (_autoTrainsFrame == null) {
                // This is the first automatic active train--check if all required options are present
                //   for automatic running.  First check for layout editor panel
                if (!_UseConnectivity || (_LE == null)) {
                    if (showErrorMessages) {
                        JOptionPane.showMessageDialog(frame, rb.getString("Error33"),
                                rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
                        log.error("AutoRun requested without a LayoutEditor panel for connectivity.");
                        return null;
                    }
                }
                if (!_HasOccupancyDetection) {
                    if (showErrorMessages) {
                        JOptionPane.showMessageDialog(frame, rb.getString("Error35"),
                                rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
                        log.error("AutoRun requested without occupancy detection.");
                        return null;
                    }
                }
            }
            // check/set Transit specific items for automatic running				
            // validate connectivity for all Sections in this transit
            int numErrors = t.validateConnectivity(_LE);
            if (numErrors != 0) {
                if (showErrorMessages) {
                    JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                            "Error34"), new Object[]{("" + numErrors)}),
                            rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }
            // check/set direction sensors in signal logic for all Sections in this Transit.
            if (getSignalType() == SIGNALHEAD) {
                numErrors = t.checkSignals(_LE);
                if (numErrors == 0) {
                    t.initializeBlockingSensors();
                }
                if (numErrors != 0) {
                    if (showErrorMessages) {
                        JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                                "Error36"), new Object[]{("" + numErrors)}),
                                rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
                    }
                    return null;
                }
            }
            //todo Need to set the same for signal masts
            // this train is OK, activate the AutoTrains window, if needed
            if (_autoTrainsFrame == null) {
                _autoTrainsFrame = new AutoTrainsFrame(_instance);
            } else {
                _autoTrainsFrame.setVisible(true);
            }
        } else if (_UseConnectivity && (_LE != null)) {
            // not auto run, set up direction sensors in signals since use connectivity was requested
            if (getSignalType() == SIGNALHEAD) {
                int numErrors = t.checkSignals(_LE);
                if (numErrors == 0) {
                    t.initializeBlockingSensors();
                }
                if (numErrors != 0) {
                    if (showErrorMessages) {
                        JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                                "Error36"), new Object[]{("" + numErrors)}),
                                rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
                    }
                    return null;
                }
            }
        }
        // all information checks out - create	
        ActiveTrain at = new ActiveTrain(t, trainID, tSource);
        //if (at==null) {
        //	if (showErrorMessages) {
        //		JOptionPane.showMessageDialog(frame,java.text.MessageFormat.format(rb.getString(
        //				"Error11"),new Object[] { transitID, trainID }), rb.getString("ErrorTitle"),
        //					JOptionPane.ERROR_MESSAGE);
        //	}
        //	log.error("Creating Active Train failed, Transit - "+transitID+", train - "+trainID);
        //	return null;
        //}
        activeTrainsList.add(at);
        java.beans.PropertyChangeListener listener = null;
        at.addPropertyChangeListener(listener = new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent e) {
                handleActiveTrainChange(e);
            }
        });
        _atListeners.add(listener);
        t.setState(Transit.ASSIGNED);
        at.setStartBlock(startBlock);
        at.setStartBlockSectionSequenceNumber(startBlockSectionSequenceNumber);
        at.setEndBlock(endBlock);
        at.setEndBlockSection(t.getSectionFromBlockAndSeq(endBlock, endBlockSectionSequenceNumber));
        at.setEndBlockSectionSequenceNumber(endBlockSectionSequenceNumber);
        at.setResetWhenDone(resetWhenDone);
        if (resetWhenDone) {
            restartingTrainsList.add(at);
        }
        at.setReverseAtEnd(reverseAtEnd);
        at.setPriority(priority);
        at.setDccAddress(dccAddress);
        at.setAutoRun(autoRun);
        return at;
    }

    public void allocateNewActiveTrain(ActiveTrain at) {
        if (at.getDelayedStart() == ActiveTrain.SENSORDELAY && at.getDelaySensor() != null) {
            if (at.getDelaySensor().getState() != jmri.Sensor.ACTIVE) {
                at.initializeDelaySensor();
            }
        }
        AllocationRequest ar = at.initializeFirstAllocation();
        if (ar != null) {
            if ((ar.getSection()).containsBlock(at.getStartBlock())) {
                // Active Train is in the first Section, go ahead and allocate it
                AllocatedSection als = allocateSection(ar, null);
                if (als == null) {
                    log.error("Problem allocating the first Section of the Active Train - " + at.getActiveTrainName());
                }
            }
        }
        activeTrainsTableModel.fireTableDataChanged();
        if (allocatedSectionTableModel != null) {
            allocatedSectionTableModel.fireTableDataChanged();
        }
    }

    private void handleActiveTrainChange(java.beans.PropertyChangeEvent e) {
        activeTrainsTableModel.fireTableDataChanged();
    }

    private boolean isInAllocatedSection(jmri.Block b) {
        for (int i = 0; i < allocatedSections.size(); i++) {
            Section s = allocatedSections.get(i).getSection();
            if (s.containsBlock(b)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Terminates an Active Train and removes it from the Dispatcher The
     * ActiveTrain object should not be used again after this method is called
     */
    public void terminateActiveTrain(ActiveTrain at) {
        // ensure there is a train to terminate
        if (at == null) {
            log.error("Null ActiveTrain pointer when attempting to terminate an ActiveTrain");
            return;
        }
        // terminate the train - remove any allocation requests
        for (int k = allocationRequests.size(); k > 0; k--) {
            if (at == allocationRequests.get(k - 1).getActiveTrain()) {
                allocationRequests.get(k - 1).dispose();
                allocationRequests.remove(k - 1);
            }
        }
        // remove any allocated sections
        for (int k = allocatedSections.size(); k > 0; k--) {
            try {
                if (at == allocatedSections.get(k - 1).getActiveTrain()) {
                    releaseAllocatedSection(allocatedSections.get(k - 1), true);
                }
            } catch (Exception e) {
                log.warn("releaseAllocatedSection failed - maybe the AllocatedSection was removed due to a terminating train??",e.toString());
                continue;
            }
        }
        // remove from restarting trains list, if present
        for (int j = restartingTrainsList.size(); j > 0; j--) {
            if (at == restartingTrainsList.get(j - 1)) {
                restartingTrainsList.remove(j - 1);
            }
        }
        // terminate the train
        for (int m = activeTrainsList.size(); m > 0; m--) {
            if (at == activeTrainsList.get(m - 1)) {
                activeTrainsList.remove(m - 1);
                at.removePropertyChangeListener(_atListeners.get(m - 1));
                _atListeners.remove(m - 1);
            }
        }
        if (at.getAutoRun()) {
            AutoActiveTrain aat = at.getAutoActiveTrain();
            _autoTrainsFrame.removeAutoActiveTrain(aat);
            aat.terminate();
            aat.dispose();
        }
        removeHeldMast(null, at);
        at.terminate();
        at.dispose();
        activeTrainsTableModel.fireTableDataChanged();
        if (allocatedSectionTableModel != null) {
            allocatedSectionTableModel.fireTableDataChanged();
        }
        allocationRequestTableModel.fireTableDataChanged();
    }

    /**
     * Creates an Allocation Request, and registers it with Dispatcher
     * <P>
     * Required input entries: activeTrain - ActiveTrain requesting the
     * allocation section - Section to be allocated direction - direction of
     * travel in the allocated Section seqNumber - sequence number of the
     * Section in the Transit of the ActiveTrain. If the requested Section is
     * not in the Transit, a sequence number of -99 should be entered.
     * showErrorMessages - "true" if error message dialogs are to be displayed
     * for detected errors Set to "false" to suppress error message dialogs from
     * this method. frame - window request is from, or "null" if not from a
     * window
     */
    protected boolean requestAllocation(ActiveTrain activeTrain, Section section, int direction,
            int seqNumber, boolean showErrorMessages, JmriJFrame frame) {
        // check input entries
        if (activeTrain == null) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, rb.getString("Error16"),
                        rb.getString("ErrorTitle"), JOptionPane.ERROR_MESSAGE);
            }
            log.error("Missing ActiveTrain specification");
            return false;
        }
        if (section == null) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                        "Error17"), new Object[]{activeTrain.getActiveTrainName()}), rb.getString("ErrorTitle"),
                        JOptionPane.ERROR_MESSAGE);
            }
            log.error("Missing Section specification in allocation request from " + activeTrain.getActiveTrainName());
            return false;
        }
        if (((seqNumber <= 0) || (seqNumber > (activeTrain.getTransit().getMaxSequence()))) && (seqNumber != -99)) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                        "Error19"), new Object[]{"" + seqNumber, activeTrain.getActiveTrainName()}), rb.getString("ErrorTitle"),
                        JOptionPane.ERROR_MESSAGE);
            }
            log.error("Out-of-range sequence number *" + seqNumber + "* in allocation request");
            return false;
        }
        if ((direction != Section.FORWARD) && (direction != Section.REVERSE)) {
            if (showErrorMessages) {
                JOptionPane.showMessageDialog(frame, java.text.MessageFormat.format(rb.getString(
                        "Error18"), new Object[]{"" + direction, activeTrain.getActiveTrainName()}), rb.getString("ErrorTitle"),
                        JOptionPane.ERROR_MESSAGE);
            }
            log.error("Invalid direction '" + direction + "' specification in allocation request");
            return false;
        }
        // check if this allocation has already been requested
        AllocationRequest ar = findAllocationRequestInQueue(section, seqNumber, direction, activeTrain);
        if (ar == null) {
            ar = new AllocationRequest(section, seqNumber, direction, activeTrain);
            allocationRequests.add(ar);
            if (_AutoAllocate) {
                autoAllocate.scanAllocationRequestList(allocationRequests);
            }
        }
        activeTrainsTableModel.fireTableDataChanged();
        allocationRequestTableModel.fireTableDataChanged();
        return true;
    }

    // ensures there will not be any duplicate allocation requests
    protected AllocationRequest findAllocationRequestInQueue(Section s, int seq, int dir, ActiveTrain at) {
        for (int i = 0; i < allocationRequests.size(); i++) {
            AllocationRequest ar = allocationRequests.get(i);
            if ((ar.getActiveTrain() == at) && (ar.getSection() == s) && (ar.getSectionSeqNumber() == seq)
                    && (ar.getSectionDirection() == dir)) {
                return ar;
            }
        }
        return null;
    }

    private void cancelAllocationRequest(int index) {
        AllocationRequest ar = allocationRequests.get(index);
        allocationRequests.remove(index);
        ar.dispose();
        allocationRequestTableModel.fireTableDataChanged();
    }

    private void allocateRequested(int index) {
        AllocationRequest ar = allocationRequests.get(index);
        allocateSection(ar, null);
    }

    protected void addDelayedTrain(ActiveTrain at) {
        if (at.getDelayedRestart() == ActiveTrain.TIMEDDELAY) {
            if (!delayedTrains.contains(at)) {
                delayedTrains.add(at);
            }
        } else if (at.getDelayedRestart() == ActiveTrain.SENSORDELAY) {
            if (at.getRestartDelaySensor() != null) {
                at.initializeReStartDelaySensor();
            }
        }
    }

    /**
     * Allocates a Section to an Active Train according to the information in an
     * AllocationRequest If successful, returns an AllocatedSection and removes
     * the AllocationRequest from the queue. If not successful, returns null and
     * leaves the AllocationRequest in the queue. To be allocatable, a Section
     * must be FREE and UNOCCUPIED. If a Section is OCCUPIED, the allocation is
     * rejected unless the dispatcher chooses to override this restriction. To
     * be allocatable, the Active Train must not be waiting for its start time.
     * If the start time has not been reached, the allocation is rejected,
     * unless the dispatcher chooses to override the start time. The user may
     * choose to specify the next Section by entering "ns". If this method is to
     * determine the next Section, or if the next section is the last section,
     * null should be entered for ns. Null should also be entered for ns if
     * allocating an Extra Section that is not the Next Section.
     */
    public AllocatedSection allocateSection(AllocationRequest ar, Section ns) {
        AllocatedSection as = null;
        Section nextSection = null;
        int nextSectionSeqNo = 0;
        if (ar != null) {
            ActiveTrain at = ar.getActiveTrain();
            if (at.holdAllocation() || at.reachedRestartPoint()) {
                return null;
            }
            Section s = ar.getSection();
            if (s.getState() != Section.FREE) {
                return null;
            }
            // skip occupancy check if this is the first allocation and the train is occupying the Section
            boolean checkOccupancy = true;
            if ((at.getLastAllocatedSection() == null) && (s.containsBlock(at.getStartBlock()))) {
                checkOccupancy = false;
            }
            // check if section is occupied
            if (checkOccupancy && (s.getOccupancy() == Section.OCCUPIED)) {
                if (_AutoAllocate) {
                    return null;  // autoAllocate never overrides occupancy
                }
                int selectedValue = JOptionPane.showOptionDialog(dispatcherFrame,
                        rb.getString("Question1"), rb.getString("WarningTitle"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                        new Object[]{rb.getString("ButtonYes"), rb.getString("ButtonNo")}, rb.getString("ButtonNo"));
                if (selectedValue == 1) {
                    return null;   // return without allocating if "No" response
                }
            }
            // check if train has reached its start time if delayed start
            if (checkOccupancy && (!at.getStarted()) && at.getDelayedStart() != ActiveTrain.NODELAY) {
                if (_AutoAllocate) {
                    return null;  // autoAllocate never overrides start time
                }
                int selectedValue = JOptionPane.showOptionDialog(dispatcherFrame,
                        rb.getString("Question4"), rb.getString("WarningTitle"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                        new Object[]{rb.getString("ButtonYes"), rb.getString("ButtonNo")}, rb.getString("ButtonNo"));
                if (selectedValue == 1) {
                    return null;
                } else {
                    at.setStarted();
                    for (int i = delayedTrains.size() - 1; i >= 0; i--) {
                        if (delayedTrains.get(i) == at) {
                            delayedTrains.remove(i);
                        }
                    }
                }
            }
            //check here to see if block is already assigned to an allocated section;
            if (getSignalType() == SIGNALMAST && checkBlocksNotInAllocatedSection(s, ar) != null) {
                return null;
            }
            // Programming Note: if ns is not null, the program will not check for end Block, but will use ns. Calling
            //		code must do all validity checks on a non-null ns.
            if (ns != null) {
                nextSection = ns;
            } else if ((ar.getSectionSeqNumber() != -99) && (at.getNextSectionSeqNumber() == ar.getSectionSeqNumber())
                    && (!((s == at.getEndBlockSection()) && (ar.getSectionSeqNumber() == at.getEndBlockSectionSequenceNumber())))
                    && (!(at.isAllocationReversed() && (ar.getSectionSeqNumber() == 1)))) {
                // not at either end - determine the next section 
                int seqNum = ar.getSectionSeqNumber();
                if (at.isAllocationReversed()) {
                    seqNum -= 1;
                } else {
                    seqNum += 1;
                }
                ArrayList<Section> secList = at.getTransit().getSectionListBySeq(seqNum);
                if (secList.size() == 1) {
                    nextSection = secList.get(0);

                } else if (secList.size() > 1) {
                    if (_AutoAllocate) {
                        nextSection = autoChoice(secList, ar);
                    } else {
                        nextSection = dispatcherChoice(secList, ar);
                    }
                }
                nextSectionSeqNo = seqNum;
            } else if (at.getReverseAtEnd() && (!at.isAllocationReversed()) && (s == at.getEndBlockSection())
                    && (ar.getSectionSeqNumber() == at.getEndBlockSectionSequenceNumber())) {
                // need to reverse Transit direction when train is in the last Section, set next section.
                nextSectionSeqNo = at.getEndBlockSectionSequenceNumber() - 1;
                at.setAllocationReversed(true);
                ArrayList<Section> secList = at.getTransit().getSectionListBySeq(nextSectionSeqNo);
                if (secList.size() == 1) {
                    nextSection = secList.get(0);
                } else if (secList.size() > 1) {
                    if (_AutoAllocate) {
                        nextSection = autoChoice(secList, ar);
                    } else {
                        nextSection = dispatcherChoice(secList, ar);
                    }
                }
            } else if (((!at.isAllocationReversed()) && (s == at.getEndBlockSection())
                    && (ar.getSectionSeqNumber() == at.getEndBlockSectionSequenceNumber()))
                    || (at.isAllocationReversed() && (ar.getSectionSeqNumber() == 1))) {
                // request to allocate the last block in the Transit, or the Transit is reversed and
                //      has reached the beginning of the Transit--check for automatic restart
                if (at.getResetWhenDone()) {
                    if (at.getDelayedRestart() != ActiveTrain.NODELAY) {
                        at.holdAllocation(true);
                    }
                    nextSection = at.getSecondAllocatedSection();
                    nextSectionSeqNo = 2;
                    at.setAllocationReversed(false);
                }
            }

            //This might be the location to check to see if we have an intermediate section that we then need to perform extra checks on.
            //Working on the basis that if the nextsection is not null, then we are not at the end of the transit.
            ArrayList<Section> intermediateSections = new ArrayList<Section>();
            Section mastHeldAtSection = null;
            if (nextSection != null && ar.getSection().getProperty("intermediateSection") != null && ((Boolean) ar.getSection().getProperty("intermediateSection")).booleanValue()) {
                String property = "forwardMast";
                if (at.isAllocationReversed()) {
                    property = "reverseMast";
                }
                if (ar.getSection().getProperty(property) != null) {
                    SignalMast endMast = InstanceManager.signalMastManagerInstance().getSignalMast(ar.getSection().getProperty(property).toString());
                    if (endMast != null) {
                        if (endMast.getHeld()) {
                            mastHeldAtSection = ar.getSection();
                        }
                    }
                }
                ArrayList<TransitSection> tsList = ar.getActiveTrain().getTransit().getTransitSectionList();
                boolean found = false;
                if (at.isAllocationReversed()) {
                    for (int i = tsList.size() - 1; i > 0; i--) {
                        TransitSection ts = tsList.get(i);
                        if (ts.getSection() == ar.getSection() && ts.getSequenceNumber() == ar.getSectionSeqNumber()) {
                            found = true;
                        } else if (found) {
                            if (ts.getSection().getProperty("intermediateSection") != null && ((Boolean) ts.getSection().getProperty("intermediateSection")).booleanValue()) {
                                intermediateSections.add(ts.getSection());
                            } else {
                                //we add the section after the last intermediate in, so that the last allocation request can be built correctly
                                intermediateSections.add(ts.getSection());
                                break;
                            }
                        }
                    }
                } else {
                    for (int i = 0; i <= tsList.size() - 1; i++) {
                        TransitSection ts = tsList.get(i);
                        if (ts.getSection() == ar.getSection() && ts.getSequenceNumber() == ar.getSectionSeqNumber()) {
                            found = true;
                        } else if (found) {
                            if (ts.getSection().getProperty("intermediateSection") != null && ((Boolean) ts.getSection().getProperty("intermediateSection")).booleanValue()) {
                                intermediateSections.add(ts.getSection());
                            } else {
                                //we add the section after the last intermediate in, so that the last allocation request can be built correctly
                                intermediateSections.add(ts.getSection());
                                break;
                            }
                        }
                    }
                }
                boolean intermediatesOccupied = false;

                for (int i = 0; i < intermediateSections.size() - 1; i++) {  // ie do not check last section which is not an intermediate section
                    Section se = intermediateSections.get(i);
                    if (se.getState() == Section.FREE) {
                        //If the section state is free, we need to look to see if any of the blocks are used else where
                        Section conflict = checkBlocksNotInAllocatedSection(se, null);
                        if (conflict != null) {
                            //We have a conflicting path 
                            //We might need to find out if the section which the block is allocated to is one in our transit, and if so is it running in the same direction.
                            return null;
                        } else {
                            if (mastHeldAtSection == null) {
                                if (se.getProperty(property) != null) {
                                    SignalMast endMast = InstanceManager.signalMastManagerInstance().getSignalMast(se.getProperty(property).toString());
                                    if (endMast != null && endMast.getHeld()) {
                                        mastHeldAtSection = se;
                                    }
                                }
                            }
                        }
                    } else if (at.getLastAllocatedSection() != null && se.getState() != at.getLastAllocatedSection().getState()) {
                        //Last allocated section and the checking section direction are not the same
                        return null;
                    } else {
                        intermediatesOccupied = true;
                    }
                }
                //If the intermediate sections are already occupied or allocated then we clear the intermediate list and only allocate the original request.
                if (intermediatesOccupied) {
                    intermediateSections = new ArrayList<Section>();
                }
            }

            // check/set turnouts if requested or if autorun
            // Note: If "Use Connectivity..." is specified in the Options window, turnouts are checked. If 
            //			turnouts are not set correctly, allocation will not proceed without dispatcher override.
            //		 If in addition Auto setting of turnouts is requested, the turnouts are set automatically
            //			if not in the correct position.
            // Note: Turnout checking and/or setting is not performed when allocating an extra section.
            if ((_UseConnectivity) && (ar.getSectionSeqNumber() != -99)) {
                if (!checkTurnoutStates(s, ar.getSectionSeqNumber(), nextSection, at, at.getLastAllocatedSection())) {
                    return null;
                }
                Section preSec = s;
                Section tmpcur = nextSection;
                int tmpSeqNo = nextSectionSeqNo;
                //The first section in the list will be the same as the nextSection, so we skip that.
                for (int i = 1; i < intermediateSections.size(); i++) {
                    Section se = intermediateSections.get(i);
                    if (preSec == mastHeldAtSection) {
                        log.debug("Section is beyond held mast do not set turnouts " + (tmpcur != null ? tmpcur.getDisplayName() : "null"));
                        break;
                    }
                    if (!checkTurnoutStates(tmpcur, tmpSeqNo, se, at, preSec)) {
                        return null;
                    }
                    preSec = tmpcur;
                    tmpcur = se;
                    if (at.isAllocationReversed()) {
                        tmpSeqNo -= 1;
                    } else {
                        tmpSeqNo += 1;
                    }
                }
            }

            as = allocateSection(at, s, ar.getSectionSeqNumber(), nextSection, nextSectionSeqNo, ar.getSectionDirection());
            if (intermediateSections.size() > 1 && mastHeldAtSection != s) {
                Section tmpcur = nextSection;
                int tmpSeqNo = nextSectionSeqNo;
                int tmpNxtSeqNo = tmpSeqNo;
                if (at.isAllocationReversed()) {
                    tmpNxtSeqNo -= 1;
                } else {
                    tmpNxtSeqNo += 1;
                }
                //The first section in the list will be the same as the nextSection, so we skip that.
                for (int i = 1; i < intermediateSections.size(); i++) {
                    if (tmpcur == mastHeldAtSection) {
                        log.debug("Section is beyond held mast do not allocate any more sections " + (tmpcur != null ? tmpcur.getDisplayName() : "null"));
                        break;
                    }
                    Section se = intermediateSections.get(i);
                    as = allocateSection(at, tmpcur, tmpSeqNo, se, tmpNxtSeqNo, ar.getSectionDirection());
                    tmpcur = se;
                    if (at.isAllocationReversed()) {
                        tmpSeqNo -= 1;
                        tmpNxtSeqNo -= 1;
                    } else {
                        tmpSeqNo += 1;
                        tmpNxtSeqNo += 1;
                    }
                }
            }
            int ix = -1;
            for (int i = 0; i < allocationRequests.size(); i++) {
                if (ar == allocationRequests.get(i)) {
                    ix = i;
                }
            }
            allocationRequests.remove(ix);
            ar.dispose();
            allocationRequestTableModel.fireTableDataChanged();
            activeTrainsTableModel.fireTableDataChanged();
            if (allocatedSectionTableModel != null) {
                allocatedSectionTableModel.fireTableDataChanged();
            }
            if (extraFrame != null) {
                cancelExtraRequested(null);
            }
            if (_AutoAllocate) {
                requestNextAllocation(at);
                autoAllocate.scanAllocationRequestList(allocationRequests);
            }

        } else {
            log.error("Null Allocation Request provided in request to allocate a section");
        }
        return as;
    }

    AllocatedSection allocateSection(ActiveTrain at, Section s, int seqNum, Section nextSection, int nextSectionSeqNo, int direction) {
        AllocatedSection as = null;
        // allocate the section
        as = new AllocatedSection(s, at, seqNum, nextSection, nextSectionSeqNo);
        if (_SupportVSDecoder) {
            as.addPropertyChangeListener(InstanceManager.getDefault(jmri.jmrit.vsdecoder.VSDecoderManager.class));
        }

        s.setState(direction/*ar.getSectionDirection()*/);
        if (getSignalType() == SIGNALMAST) {
            String property = "forwardMast";
            if (s.getState() == Section.REVERSE) {
                property = "reverseMast";
            }
            if (s.getProperty(property) != null) {
                SignalMast toHold = InstanceManager.signalMastManagerInstance().getSignalMast(s.getProperty(property).toString());
                if (toHold != null) {
                    if (!toHold.getHeld()) {
                        heldMasts.add(new HeldMastDetails(toHold, at));
                        toHold.setHeld(true);
                    }
                }

            }

            if (at.getLastAllocatedSection() != null && at.getLastAllocatedSection().getProperty(property) != null) {
                SignalMast toRelease = InstanceManager.signalMastManagerInstance().getSignalMast(at.getLastAllocatedSection().getProperty(property).toString());
                if (toRelease != null && isMastHeldByDispatcher(toRelease, at)) {
                    removeHeldMast(toRelease, at);
                    //heldMasts.remove(toRelease);
                    toRelease.setHeld(false);
                }
            }
        }
        at.addAllocatedSection(as);
        allocatedSections.add(as);
        return as;
    }

    boolean checkTurnoutStates(Section s, int sSeqNum, Section nextSection, ActiveTrain at, Section prevSection) {
        boolean turnoutsOK = true;
        if (_AutoTurnouts || at.getAutoRun()) {
            // automatically set the turnouts for this section before allocation
            turnoutsOK = autoTurnouts.setTurnoutsInSection(s, sSeqNum, nextSection,
                    at, _LE, _TrustKnownTurnouts, prevSection);
        } else {
            // check that turnouts are correctly set before allowing allocation to proceed
            turnoutsOK = autoTurnouts.checkTurnoutsInSection(s, sSeqNum, nextSection,
                    at, _LE, prevSection);
        }
        if (!turnoutsOK) {
            if (_AutoAllocate) {
                return false;
            } else {
                // give the manual dispatcher a chance to override turnouts not OK
                int selectedValue = JOptionPane.showOptionDialog(dispatcherFrame,
                        rb.getString("Question2"), rb.getString("WarningTitle"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                        new Object[]{rb.getString("ButtonYes"), rb.getString("ButtonNo")}, rb.getString("ButtonNo"));
                if (selectedValue == 1) {
                    return false;   // return without allocating if "No" response
                }
            }
        }
        return true;
    }

    ArrayList<HeldMastDetails> heldMasts = new ArrayList<HeldMastDetails>();

    static class HeldMastDetails {

        SignalMast mast = null;
        ActiveTrain at = null;

        HeldMastDetails(SignalMast sm, ActiveTrain a) {
            mast = sm;
            at = a;
        }

        ActiveTrain getActiveTrain() {
            return at;
        }

        SignalMast getMast() {
            return mast;
        }
    }

    public boolean isMastHeldByDispatcher(SignalMast sm, ActiveTrain at) {
        for (HeldMastDetails hmd : heldMasts) {
            if (hmd.getMast() == sm && hmd.getActiveTrain() == at) {
                return true;
            }
        }
        return false;
    }

    private void removeHeldMast(SignalMast sm, ActiveTrain at) {
        ArrayList<HeldMastDetails> toRemove = new ArrayList<HeldMastDetails>();
        for (HeldMastDetails hmd : heldMasts) {
            if (hmd.getActiveTrain() == at) {
                if (sm == null) {
                    toRemove.add(hmd);
                } else if (sm == hmd.getMast()) {
                    toRemove.add(hmd);
                }
            }
        }
        for (HeldMastDetails hmd : toRemove) {
            hmd.getMast().setHeld(false);
            heldMasts.remove(hmd);
        }
    }

    /*
     * This is used to determine if the blocks in a section we want to allocate are already allocated to a section, or if they are now free.
     */
    protected Section checkBlocksNotInAllocatedSection(Section s, AllocationRequest ar) {
        for (AllocatedSection as : allocatedSections) {
            if (as.getSection() != s) {
                ArrayList<Block> blas = as.getSection().getBlockList();
                for (Block b : s.getBlockList()) {
                    if (blas.contains(b)) {
                        if (as.getSection().getOccupancy() == Block.OCCUPIED) {
                            //The next check looks to see if the block has already been passed or not and therefore ready for allocation.
                            if (as.getSection().getState() == Section.FORWARD) {
                                for (int i = 0; i < blas.size(); i++) {
                                    //The block we get to is occupied therefore the subsequent blocks have not been entered
                                    if (blas.get(i).getState() == Block.OCCUPIED) {
                                        if (ar != null) {
                                            ar.setWaitingOnBlock(b);
                                        }
                                        return as.getSection();
                                    } else if (blas.get(i) == b) {
                                        break;
                                    }
                                }
                            } else {
                                for (int i = blas.size(); i >= 0; i--) {
                                    //The block we get to is occupied therefore the subsequent blocks have not been entered
                                    if (blas.get(i).getState() == Block.OCCUPIED) {
                                        if (ar != null) {
                                            ar.setWaitingOnBlock(b);
                                        }
                                        return as.getSection();
                                    } else if (blas.get(i) == b) {
                                        break;
                                    }
                                }
                            }
                        } else if (as.getSection().getOccupancy() != Section.FREE) {
                            if (ar != null) {
                                ar.setWaitingOnBlock(b);
                            }
                            return as.getSection();
                        }
                    }
                }
            }
        }
        return null;
    }

    // automatically make a choice of next section
    private Section autoChoice(ArrayList<Section> sList, AllocationRequest ar) {
        Section tSection = autoAllocate.autoNextSectionChoice(sList, ar);
        if (tSection != null) {
            return tSection;
        }
        // if automatic choice failed, ask the dispatcher
        return dispatcherChoice(sList, ar);
    }

    // manually make a choice of next section
    private Section dispatcherChoice(ArrayList<Section> sList, AllocationRequest ar) {
        Object choices[] = new Object[sList.size()];
        for (int i = 0; i < sList.size(); i++) {
            Section s = sList.get(i);
            String txt = s.getSystemName();
            String user = s.getUserName();
            if ((user != null) && (!user.equals("")) && (!user.equals(txt))) {
                txt = txt + "(" + user + ")";
            }
            choices[i] = txt;
        }
        Object secName = JOptionPane.showInputDialog(dispatcherFrame,
                rb.getString("ExplainChoice") + " " + ar.getSectionName() + ".",
                rb.getString("ChoiceFrameTitle"), JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        if (secName == null) {
            JOptionPane.showMessageDialog(dispatcherFrame, rb.getString("WarnCancel"));
            return sList.get(0);
        }
        for (int j = 0; j < sList.size(); j++) {
            if (secName.equals(choices[j])) {
                return sList.get(j);
            }
        }
        return sList.get(0);
    }

    // submit an AllocationRequest for the next Section of an ActiveTrain
    private void requestNextAllocation(ActiveTrain at) {
        // set up an Allocation Request
        Section next = at.getNextSectionToAllocate();
        if (next == null) {
            return;
        }
        int seqNext = at.getNextSectionSeqNumber();
        int dirNext = at.getAllocationDirectionFromSectionAndSeq(next, seqNext);
        requestAllocation(at, next, dirNext, seqNext, true, dispatcherFrame);
    }

    /**
     * Check if any allocation requests need to be allocated, or if any
     * allocated sections need to be released
     */
    private void checkAutoRelease() {
        if ((autoReleaseBox != null) && (autoReleaseBox.isSelected())) {
            // Auto release of exited sections has been requested - because of possible noise in block detection 
            //    hardware, allocated sections are automatically released in the order they were allocated only
            // Only unoccupied sections that have been exited are tested.
            // The next allocated section must be assigned to the same train, and it must have been entered for 
            //    the exited Section to be released.
            // Extra allocated sections are not automatically released (allocation number = -1).
            boolean foundOne = true;
            while ((allocatedSections.size() > 0) && foundOne) {
                try {
                    foundOne = false;
                    AllocatedSection as = null;
                    for (int i = 0; (i < allocatedSections.size()) && !foundOne; i++) {
                        as = allocatedSections.get(i);
                        if (as.getExited() && (as.getSection().getOccupancy() != Section.OCCUPIED)
                                && (as.getAllocationNumber() != -1)) {
                            // possible candidate for deallocation - check order
                            foundOne = true;
                            for (int j = 0; (j < allocatedSections.size()) && foundOne; j++) {
                                if (j != i) {
                                    AllocatedSection asx = allocatedSections.get(j);
                                    if ((asx.getActiveTrain() == as.getActiveTrain())
                                            && (asx.getAllocationNumber() != -1)
                                            && (asx.getAllocationNumber() < as.getAllocationNumber())) {
                                        foundOne = false;
                                    }
                                }
                            }
                            if (foundOne) {
                                // check if the next section is allocated to the same train and has been entered
                                ActiveTrain at = as.getActiveTrain();
                                Section ns = as.getNextSection();
                                AllocatedSection nas = null;
                                for (int k = 0; (k < allocatedSections.size()) && (nas == null); k++) {
                                    if (allocatedSections.get(k).getSection() == ns) {
                                        nas = allocatedSections.get(k);
                                    }
                                }
                                if ((nas == null) || (at.getStatus() == ActiveTrain.WORKING)
                                        || (at.getStatus() == ActiveTrain.STOPPED)
                                        || (at.getStatus() == ActiveTrain.READY)
                                        || (at.getMode() == ActiveTrain.MANUAL)) {
                                    // do not autorelease allocated sections from an Active Train that is 
                                    //    STOPPED, READY, or WORKING, or is in MANUAL mode.
                                    foundOne = false;
                                    //But do so if the active train has reached its restart point
                                    if (at.reachedRestartPoint()) {
                                        foundOne = true;
                                    }
                                } else {
                                    if ((nas.getActiveTrain() != as.getActiveTrain()) || (!nas.getEntered())) {
                                        foundOne = false;
                                    }
                                }
                                if (foundOne) {
                                    // have section to release - delay before release
                                    try {
                                        Thread.sleep(500);
                                    } catch (InterruptedException e) {
                                        // ignore this exception
                                    }
                                    // if section is still allocated, release it
                                    foundOne = false;
                                    for (int m = 0; m < allocatedSections.size(); m++) {
                                        if ((allocatedSections.get(m) == as) && (as.getActiveTrain() == at)) {
                                            foundOne = true;
                                        }
                                    }
                                    if (foundOne) {
                                        releaseAllocatedSection(as, false);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("checkAutoRelease failed  - maybe the AllocatedSection was removed due to a terminating train?? "+e.toString());
                    continue;
                }
            }
        }
    }

    /**
     * Releases an allocated Section, and removes it from the Dispatcher Input
     * consists of the AllocatedSection object (returned by "allocateSection")
     * and whether this release is from a Terminate Train.
     */
    public void releaseAllocatedSection(AllocatedSection as, boolean terminatingTrain) {
        // check that section is not occupied if not terminating train
        if (!terminatingTrain && (as.getSection().getOccupancy() == Section.OCCUPIED)) {
            // warn the manual dispatcher that Allocated Section is occupied
            int selectedValue = JOptionPane.showOptionDialog(dispatcherFrame, java.text.MessageFormat.format(
                    rb.getString("Question5"), new Object[]{as.getSectionName()}), rb.getString("WarningTitle"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    new Object[]{rb.getString("ButtonYesX"), rb.getString("ButtonNoX")},
                    rb.getString("ButtonNoX"));
            if (selectedValue == 1) {
                return;   // return without releasing if "No" response
            }
        }
        // release the Allocated Section
        for (int i = allocatedSections.size(); i > 0; i--) {
            if (as == allocatedSections.get(i - 1)) {
                allocatedSections.remove(i - 1);
            }
        }
        as.getSection().setState(Section.FREE);
        as.getActiveTrain().removeAllocatedSection(as);
        as.dispose();
        if (allocatedSectionTableModel != null) {
            allocatedSectionTableModel.fireTableDataChanged();
        }
        allocationRequestTableModel.fireTableDataChanged();
        activeTrainsTableModel.fireTableDataChanged();
        if (_AutoAllocate) {
            autoAllocate.scanAllocationRequestList(allocationRequests);
        }
    }

    /**
     * Updates display when occupancy of an allocated section changes Also
     * drives auto release if it is selected
     */
    public void sectionOccupancyChanged() {
        checkAutoRelease();
        if (allocatedSectionTableModel != null) {
            allocatedSectionTableModel.fireTableDataChanged();
        }
        allocationRequestTableModel.fireTableDataChanged();
    }

    /**
     * Handle activity that is triggered by the fast clock
     */
    protected void newFastClockMinute() {
        for (int i = delayedTrains.size() - 1; i >= 0; i--) {
            ActiveTrain at = delayedTrains.get(i);
            // check if this Active Train is waiting to start
            if ((!at.getStarted()) && at.getDelayedStart() != ActiveTrain.NODELAY) {
                // is it time to start? 
                if (at.getDelayedStart() == ActiveTrain.TIMEDDELAY) {
                    if (isFastClockTimeGE(at.getDepartureTimeHr(), at.getDepartureTimeMin())) {
                        // allow this train to start
                        at.setStarted();
                        delayedTrains.remove(i);
                        if (_AutoAllocate) {
                            autoAllocate.scanAllocationRequestList(allocationRequests);
                        }
                    }
                }
            } else if (at.getStarted() && at.getStatus() == ActiveTrain.READY && at.reachedRestartPoint()) {
                if (isFastClockTimeGE(at.getRestartDepartHr(), at.getRestartDepartMin())) {
                    at.restart();
                    delayedTrains.remove(i);
                    if (_AutoAllocate) {
                        autoAllocate.scanAllocationRequestList(allocationRequests);
                    }
                }
            }
        }
    }
    private int nowMinutes = 0;    // last read fast clock minutes
    private int nowHours = 0;		// last read fast clock hours

    /**
     * This method tests time assuming both times are on the same day (ignoring
     * midnight) It also sets nowMinutes and nowHours to the latest fast clock
     * values
     */
    @SuppressWarnings("deprecation")
    protected boolean isFastClockTimeGE(int hr, int min) {
        Date now = fastClock.getTime();
        nowHours = now.getHours();
        nowMinutes = now.getMinutes();
        if (((nowHours * 60) + nowMinutes) >= ((hr * 60) + min)) {
            return true;
        }
        return false;
    }

    // option access methods
    protected LayoutEditor getLayoutEditor() {
        return _LE;
    }

    protected void setLayoutEditor(LayoutEditor editor) {
        _LE = editor;
    }

    protected boolean getUseConnectivity() {
        return _UseConnectivity;
    }

    protected void setUseConnectivity(boolean set) {
        _UseConnectivity = set;
    }

    protected void setSignalType(int type) {
        _SignalType = type;
    }

    protected int getSignalType() {
        return _SignalType;
    }

    protected boolean getTrainsFromRoster() {
        return _TrainsFromRoster;
    }

    protected void setTrainsFromRoster(boolean set) {
        _TrainsFromRoster = set;
    }

    protected boolean getTrainsFromTrains() {
        return _TrainsFromTrains;
    }

    protected void setTrainsFromTrains(boolean set) {
        _TrainsFromTrains = set;
    }

    protected boolean getTrainsFromUser() {
        return _TrainsFromUser;
    }

    protected void setTrainsFromUser(boolean set) {
        _TrainsFromUser = set;
    }

    protected boolean getAutoAllocate() {
        return _AutoAllocate;
    }

    protected void setAutoAllocate(boolean set) {
        if (set && (autoAllocate == null)) {
            if (_LE != null) {
                autoAllocate = new AutoAllocate(this);
            } else {
                JOptionPane.showMessageDialog(dispatcherFrame, rb.getString("Error39"),
                        rb.getString("MessageTitle"), JOptionPane.INFORMATION_MESSAGE);
                _AutoAllocate = false;
                if (autoAllocateBox != null) {
                    autoAllocateBox.setSelected(_AutoAllocate);
                }
                return;
            }
        }
        _AutoAllocate = set;
        if ((!_AutoAllocate) && (autoAllocate != null)) {
            autoAllocate.clearAllocationPlans();
        }
        if (autoAllocateBox != null) {
            autoAllocateBox.setSelected(_AutoAllocate);
        }
    }

    protected boolean getAutoTurnouts() {
        return _AutoTurnouts;
    }
    protected void setAutoTurnouts(boolean set) {
        _AutoTurnouts = set;
    }

    protected boolean getTrustKnownTurnouts() {
        return _TrustKnownTurnouts;
    }
    protected void setTrustKnownTurnouts(boolean set) {
        _TrustKnownTurnouts = set;
    }

    protected int getMinThrottleInterval() {
        return _MinThrottleInterval;
    }
    protected void setMinThrottleInterval(int set) {
        _MinThrottleInterval = set;
    }

    protected int getFullRampTime() {
        return _FullRampTime;
    }
    protected void setFullRampTime(int set) {
        _FullRampTime = set;
    }

    protected boolean getHasOccupancyDetection() {
        return _HasOccupancyDetection;
    }
    protected void setHasOccupancyDetection(boolean set) {
        _HasOccupancyDetection = set;
    }

    protected boolean getUseScaleMeters() {
        return _UseScaleMeters;
    }
    protected void setUseScaleMeters(boolean set) {
        _UseScaleMeters = set;
    }

    protected boolean getShortActiveTrainNames() {
        return _ShortActiveTrainNames;
    }
    protected void setShortActiveTrainNames(boolean set) {
        _ShortActiveTrainNames = set;
        if (allocatedSectionTableModel != null) {
            allocatedSectionTableModel.fireTableDataChanged();
        }
        if (allocationRequestTableModel != null) {
            allocationRequestTableModel.fireTableDataChanged();
        }
    }

    protected boolean getShortNameInBlock() {
        return _ShortNameInBlock;
    }
    protected void setShortNameInBlock(boolean set) {
        _ShortNameInBlock = set;
    }

    protected boolean getRosterEntryInBlock() {
        return _RosterEntryInBlock;
    }
    protected void setRosterEntryInBlock(boolean set) {
        _RosterEntryInBlock = set;
    }

    protected boolean getExtraColorForAllocated() {
        return _ExtraColorForAllocated;
    }
    protected void setExtraColorForAllocated(boolean set) {
        _ExtraColorForAllocated = set;
    }

    protected boolean getNameInAllocatedBlock() {
        return _NameInAllocatedBlock;
    }
    protected void setNameInAllocatedBlock(boolean set) {
        _NameInAllocatedBlock = set;
    }

    protected int getScale() {
        return _LayoutScale;
    }
    protected void setScale(int sc) {
        _LayoutScale = sc;
    }

    public ArrayList<ActiveTrain> getActiveTrainsList() {
        return activeTrainsList;
    }
    protected ArrayList<AllocatedSection> getAllocatedSectionsList() {
        return allocatedSections;
    }

    public ActiveTrain getActiveTrainForRoster(RosterEntry re) {
        if (!_TrainsFromRoster) {
            return null;
        }
        for (ActiveTrain at : activeTrainsList) {
            if (at.getRosterEntry().equals(re)) {
                return at;
            }
        }
        return null;

    }

    protected boolean getSupportVSDecoder() {
        return _SupportVSDecoder;
    }
    protected void setSupportVSDecoder(boolean set) {
        _SupportVSDecoder = set;
    }

    // called by ActivateTrainFrame after a new train is all set up
    //      Dispatcher side of activating a new train should be completed here
    protected void newTrainDone(ActiveTrain at) {
        if (at != null) {
            // a new active train was created, check for delayed start
            if (at.getDelayedStart() != ActiveTrain.NODELAY && (!at.getStarted())) {
                delayedTrains.add(at);
                fastClockWarn(true);
            }    
// djd needs work here    
            // check for delayed restart      
            else if (at.getDelayedRestart() == ActiveTrain.TIMEDDELAY) {
                fastClockWarn(false);
            }
        }
        newTrainActive = false;
    }

    protected void removeDelayedTrain(ActiveTrain at) {
        delayedTrains.remove(at);
    }

    private void fastClockWarn(boolean wMess) {
        if (fastClockSensor.getState() == Sensor.ACTIVE) {
            return;
        }
        // warn that the fast clock is not running
        String mess = "";
        if (wMess) {
            mess = rb.getString("FastClockWarn");
        }
        else {
            mess = rb.getString("FastClockWarn2");
        }
        int selectedValue = JOptionPane.showOptionDialog(dispatcherFrame,
                mess, rb.getString("WarningTitle"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new Object[]{rb.getString("ButtonYesStart"), rb.getString("ButtonNo")},
                rb.getString("ButtonNo"));
        if (selectedValue == 0) {
            try {
                fastClockSensor.setState(Sensor.ACTIVE);
            } catch (jmri.JmriException reason) {
                log.error("Exception when setting fast clock sensor");
            }
        }
    }

    protected AutoTrainsFrame getAutoTrainsFrame() {
        return _autoTrainsFrame;
    }

    static DispatcherFrame _instance = null;

    static public DispatcherFrame instance() {
        if (_instance == null) {
            _instance = new DispatcherFrame();
            jmri.InstanceManager.store(_instance, jmri.jmrit.dispatcher.DispatcherFrame.class);
        }
        return (_instance);
    }

    /**
     * Table model for Active Trains Table in Dispatcher window
     */
    public class ActiveTrainsTableModel extends javax.swing.table.AbstractTableModel implements
            java.beans.PropertyChangeListener {

        /**
         *
         */
        private static final long serialVersionUID = -612833010252140557L;
        public static final int TRANSIT_COLUMN = 0;
        public static final int TRAIN_COLUMN = 1;
        public static final int TYPE_COLUMN = 2;
        public static final int STATUS_COLUMN = 3;
        public static final int MODE_COLUMN = 4;
        public static final int ALLOCATED_COLUMN = 5;
        public static final int NEXTSECTION_COLUMN = 6;
        public static final int ALLOCATEBUTTON_COLUMN = 7;

        public ActiveTrainsTableModel() {
            super();
        }

        public void propertyChange(java.beans.PropertyChangeEvent e) {
            if (e.getPropertyName().equals("length")) {
                fireTableDataChanged();
            }
        }

        public Class<?> getColumnClass(int c) {
            if (c == ALLOCATEBUTTON_COLUMN) {
                return JButton.class;
            }
            return String.class;
        }

        public int getColumnCount() {
            return ALLOCATEBUTTON_COLUMN + 1;
        }

        public int getRowCount() {
            return (activeTrainsList.size());
        }

        public boolean isCellEditable(int r, int c) {
            if (c == ALLOCATEBUTTON_COLUMN) {
                return (true);
            }
            return (false);
        }

        public String getColumnName(int col) {
            switch (col) {
                case TRANSIT_COLUMN:
                    return rb.getString("TransitColumnTitle");
                case TRAIN_COLUMN:
                    return rb.getString("TrainColumnTitle");
                case TYPE_COLUMN:
                    return rb.getString("TrainTypeColumnTitle");
                case STATUS_COLUMN:
                    return rb.getString("TrainStatusColumnTitle");
                case MODE_COLUMN:
                    return rb.getString("TrainModeColumnTitle");
                case ALLOCATED_COLUMN:
                    return rb.getString("AllocatedSectionColumnTitle");
                case NEXTSECTION_COLUMN:
                    return rb.getString("NextSectionColumnTitle");
                case ALLOCATEBUTTON_COLUMN:
                    return (" "); // button columns have no names
                default:
                    return "";
            }
        }

        public int getPreferredWidth(int col) {
            switch (col) {
                case TRANSIT_COLUMN:
                    return new JTextField(17).getPreferredSize().width;
                case TRAIN_COLUMN:
                    return new JTextField(17).getPreferredSize().width;
                case TYPE_COLUMN:
                    return new JTextField(16).getPreferredSize().width;
                case STATUS_COLUMN:
                    return new JTextField(8).getPreferredSize().width;
                case MODE_COLUMN:
                    return new JTextField(11).getPreferredSize().width;
                case ALLOCATED_COLUMN:
                    return new JTextField(17).getPreferredSize().width;
                case NEXTSECTION_COLUMN:
                    return new JTextField(17).getPreferredSize().width;
                case ALLOCATEBUTTON_COLUMN:
                    return new JTextField(12).getPreferredSize().width;
            }
            return new JTextField(5).getPreferredSize().width;
        }

        public Object getValueAt(int r, int c) {
            int rx = r;
            if (rx >= activeTrainsList.size()) {
                return null;
            }
            ActiveTrain at = activeTrainsList.get(rx);
            switch (c) {
                case TRANSIT_COLUMN:
                    return (at.getTransitName());
                case TRAIN_COLUMN:
                    return (at.getTrainName());
                case TYPE_COLUMN:
                    return (at.getTrainTypeText());
                case STATUS_COLUMN:
                    return (at.getStatusText());
                case MODE_COLUMN:
                    return (at.getModeText());
                case ALLOCATED_COLUMN:
                    return (at.getLastAllocatedSectionName());
                case NEXTSECTION_COLUMN:
                    return (at.getNextSectionToAllocateName());
                case ALLOCATEBUTTON_COLUMN:
                    return rb.getString("AllocateButtonName");
                default:
                    return (" ");
            }
        }

        public void setValueAt(Object value, int row, int col) {
            if (col == ALLOCATEBUTTON_COLUMN) {
                // open an allocate window
                allocateNextRequested(row);
            }
        }
    }

    /**
     * Table model for Allocation Request Table in Dispatcher window
     */
    public class AllocationRequestTableModel extends javax.swing.table.AbstractTableModel implements
            java.beans.PropertyChangeListener {

        /**
         *
         */
        private static final long serialVersionUID = 1425670284957075595L;
        public static final int ACTIVE_COLUMN = 0;
        public static final int PRIORITY_COLUMN = 1;
        public static final int TRAINTYPE_COLUMN = 2;
        public static final int SECTION_COLUMN = 3;
        public static final int STATUS_COLUMN = 4;
        public static final int OCCUPANCY_COLUMN = 5;
        public static final int SECTIONLENGTH_COLUMN = 6;
        public static final int ALLOCATEBUTTON_COLUMN = 7;
        public static final int CANCELBUTTON_COLUMN = 8;

        public AllocationRequestTableModel() {
            super();
        }

        public void propertyChange(java.beans.PropertyChangeEvent e) {
            if (e.getPropertyName().equals("length")) {
                fireTableDataChanged();
            }
        }

        public Class<?> getColumnClass(int c) {
            if (c == CANCELBUTTON_COLUMN) {
                return JButton.class;
            }
            if (c == ALLOCATEBUTTON_COLUMN) {
                return JButton.class;
            }
            return String.class;
        }

        public int getColumnCount() {
            return CANCELBUTTON_COLUMN + 1;
        }

        public int getRowCount() {
            return (allocationRequests.size());
        }

        public boolean isCellEditable(int r, int c) {
            if (c == CANCELBUTTON_COLUMN) {
                return (true);
            }
            if (c == ALLOCATEBUTTON_COLUMN) {
                return (true);
            }
            return (false);
        }

        public String getColumnName(int col) {
            switch (col) {
                case ACTIVE_COLUMN:
                    return rb.getString("ActiveColumnTitle");
                case PRIORITY_COLUMN:
                    return rb.getString("PriorityLabel");
                case TRAINTYPE_COLUMN:
                    return rb.getString("TrainTypeColumnTitle");
                case SECTION_COLUMN:
                    return rb.getString("SectionColumnTitle");
                case STATUS_COLUMN:
                    return rb.getString("StatusColumnTitle");
                case OCCUPANCY_COLUMN:
                    return rb.getString("OccupancyColumnTitle");
                case SECTIONLENGTH_COLUMN:
                    return rb.getString("SectionLengthColumnTitle");
                case ALLOCATEBUTTON_COLUMN:
                    return (" "); // button columns have no names
                case CANCELBUTTON_COLUMN:
                    return (" "); // button columns have no names
                default:
                    return "";
            }
        }

        public int getPreferredWidth(int col) {
            switch (col) {
                case ACTIVE_COLUMN:
                    return new JTextField(30).getPreferredSize().width;
                case PRIORITY_COLUMN:
                    return new JTextField(8).getPreferredSize().width;
                case TRAINTYPE_COLUMN:
                    return new JTextField(15).getPreferredSize().width;
                case SECTION_COLUMN:
                    return new JTextField(25).getPreferredSize().width;
                case STATUS_COLUMN:
                    return new JTextField(15).getPreferredSize().width;
                case OCCUPANCY_COLUMN:
                    return new JTextField(10).getPreferredSize().width;
                case SECTIONLENGTH_COLUMN:
                    return new JTextField(8).getPreferredSize().width;
                case ALLOCATEBUTTON_COLUMN:
                    return new JTextField(12).getPreferredSize().width;
                case CANCELBUTTON_COLUMN:
                    return new JTextField(10).getPreferredSize().width;
            }
            return new JTextField(5).getPreferredSize().width;
        }

        public Object getValueAt(int r, int c) {
            int rx = r;
            if (rx >= allocationRequests.size()) {
                return null;
            }
            AllocationRequest ar = allocationRequests.get(rx);
            switch (c) {
                case ACTIVE_COLUMN:
                    if (_ShortActiveTrainNames) {
                        return (ar.getActiveTrain().getTrainName());
                    }
                    return (ar.getActiveTrainName());
                case PRIORITY_COLUMN:
                    return ("   " + ar.getActiveTrain().getPriority());
                case TRAINTYPE_COLUMN:
                    return (ar.getActiveTrain().getTrainTypeText());
                case SECTION_COLUMN:
                    return (ar.getSectionName());
                case STATUS_COLUMN:
                    if (ar.getSection().getState() == Section.FREE) {
                        return rb.getString("FREE");
                    }
                    return rb.getString("ALLOCATED");
                case OCCUPANCY_COLUMN:
                    if (!_HasOccupancyDetection) {
                        return rb.getString("UNKNOWN");
                    }
                    if (ar.getSection().getOccupancy() == Section.OCCUPIED) {
                        return rb.getString("OCCUPIED");
                    }
                    return rb.getString("UNOCCUPIED");
                case SECTIONLENGTH_COLUMN:
                    return ("  " + ar.getSection().getLengthI(_UseScaleMeters, _LayoutScale));
                case ALLOCATEBUTTON_COLUMN:
                    return rb.getString("AllocateButton");
                case CANCELBUTTON_COLUMN:
                    return rb.getString("CancelButton");
                default:
                    return (" ");
            }
        }

        public void setValueAt(Object value, int row, int col) {
            if (col == ALLOCATEBUTTON_COLUMN) {
                // open an allocate window
                allocateRequested(row);
            }
            if (col == CANCELBUTTON_COLUMN) {
                // open an allocate window
                cancelAllocationRequest(row);
            }
        }
    }

    /**
     * Table model for Allocated Section Table
     */
    public class AllocatedSectionTableModel extends javax.swing.table.AbstractTableModel implements
            java.beans.PropertyChangeListener {

        /**
         *
         */
        private static final long serialVersionUID = -7179461629851240834L;
        public static final int ACTIVE_COLUMN = 0;
        public static final int SECTION_COLUMN = 1;
        public static final int OCCUPANCY_COLUMN = 2;
        public static final int USESTATUS_COLUMN = 3;

        public static final int RELEASEBUTTON_COLUMN = 4;

        public AllocatedSectionTableModel() {
            super();
        }

        public void propertyChange(java.beans.PropertyChangeEvent e) {
            if (e.getPropertyName().equals("length")) {
                fireTableDataChanged();
            }
        }

        public Class<?> getColumnClass(int c) {
            if (c == RELEASEBUTTON_COLUMN) {
                return JButton.class;
            }
            return String.class;
        }

        public int getColumnCount() {
            return RELEASEBUTTON_COLUMN + 1;
        }

        public int getRowCount() {
            return (allocatedSections.size());
        }

        public boolean isCellEditable(int r, int c) {
            if (c == RELEASEBUTTON_COLUMN) {
                return (true);
            }
            return (false);
        }

        public String getColumnName(int col) {
            switch (col) {
                case ACTIVE_COLUMN:
                    return rb.getString("ActiveColumnTitle");
                case SECTION_COLUMN:
                    return rb.getString("AllocatedSectionColumnTitle");
                case OCCUPANCY_COLUMN:
                    return rb.getString("OccupancyColumnTitle");
                case USESTATUS_COLUMN:
                    return rb.getString("UseStatusColumnTitle");
                case RELEASEBUTTON_COLUMN:
                    return (" "); // button columns have no names
                default:
                    return "";
            }
        }

        public int getPreferredWidth(int col) {
            switch (col) {
                case ACTIVE_COLUMN:
                    return new JTextField(30).getPreferredSize().width;
                case SECTION_COLUMN:
                    return new JTextField(25).getPreferredSize().width;
                case OCCUPANCY_COLUMN:
                    return new JTextField(10).getPreferredSize().width;
                case USESTATUS_COLUMN:
                    return new JTextField(15).getPreferredSize().width;
                case RELEASEBUTTON_COLUMN:
                    return new JTextField(12).getPreferredSize().width;
            }
            return new JTextField(5).getPreferredSize().width;
        }

        public Object getValueAt(int r, int c) {
            int rx = r;
            if (rx >= allocatedSections.size()) {
                return null;
            }
            AllocatedSection as = allocatedSections.get(rx);
            switch (c) {
                case ACTIVE_COLUMN:
                    if (_ShortActiveTrainNames) {
                        return (as.getActiveTrain().getTrainName());
                    }
                    return (as.getActiveTrainName());
                case SECTION_COLUMN:
                    return (as.getSectionName());
                case OCCUPANCY_COLUMN:
                    if (!_HasOccupancyDetection) {
                        return rb.getString("UNKNOWN");
                    }
                    if (as.getSection().getOccupancy() == Section.OCCUPIED) {
                        return rb.getString("OCCUPIED");
                    }
                    return rb.getString("UNOCCUPIED");
                case USESTATUS_COLUMN:
                    if (!as.getEntered()) {
                        return rb.getString("NotEntered");
                    }
                    if (as.getExited()) {
                        return rb.getString("Exited");
                    }
                    return rb.getString("Entered");
                case RELEASEBUTTON_COLUMN:
                    return rb.getString("ReleaseButton");
                default:
                    return (" ");
            }
        }

        public void setValueAt(Object value, int row, int col) {
            if (col == RELEASEBUTTON_COLUMN) {
                releaseAllocatedSectionFromTable(row);
            }
        }
    }

    private final static Logger log = LoggerFactory.getLogger(DispatcherFrame.class.getName());

}
