package jmri.jmrit.logixng.implementation.swing;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.swing.*;

import jmri.jmrit.logixng.*;
import jmri.jmrit.logixng.implementation.DefaultMaleDigitalExpressionSocket;

/**
 * Configures an DefaultMaleDigitalActionSocket object with a Swing JPanel.
 */
public class DefaultMaleDigitalExpressionSocketSwing extends AbstractMaleSocketSwing {

    private JPanel _panel;
    private final JLabel _listenLabel = new JLabel(Bundle.getMessage("DefaultMaleDigitalExpressionSocketSwing_Listen"));
    private JCheckBox _listenCheckBox;
    
    @Override
    protected JPanel getSubPanel(@CheckForNull Base object) {
        if ((object != null) && (! (object instanceof DefaultMaleDigitalExpressionSocket))) {
            throw new IllegalArgumentException("object is not an DefaultMaleDigitalExpressionSocket: " + object.getClass().getName());
        }
        
        _panel = new JPanel();
        _listenCheckBox = new JCheckBox();
        
        DefaultMaleDigitalExpressionSocket maleSocket = (DefaultMaleDigitalExpressionSocket)object;
        if (maleSocket != null) {
            _listenCheckBox.setSelected(maleSocket.getListen());
        }
        
        _listenLabel.setLabelFor(_listenCheckBox);
        _panel.add(_listenLabel);
        _panel.add(_listenCheckBox);
        
        return _panel;
    }
    
    /** {@inheritDoc} */
    @Override
    public void updateObject(@Nonnull Base object) {
        if (! (object instanceof DefaultMaleDigitalExpressionSocket)) {
            throw new IllegalArgumentException("object is not an DefaultMaleDigitalExpressionSocket: " + object.getClass().getName());
        }
        
        DefaultMaleDigitalExpressionSocket maleSocket = (DefaultMaleDigitalExpressionSocket)object;
        maleSocket.setListen(_listenCheckBox.isSelected());
    }
    
//    private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrSwing.class);
    
}
