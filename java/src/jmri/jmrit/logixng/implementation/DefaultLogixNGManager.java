package jmri.jmrit.logixng.implementation;

import java.io.PrintWriter;
import java.util.*;

import jmri.*;
import jmri.jmrit.logixng.*;
import jmri.jmrit.logixng.Stack;
import jmri.managers.AbstractManager;
import jmri.util.*;


/**
 * Class providing the basic logic of the LogixNG_Manager interface.
 * 
 * @author Dave Duchamp       Copyright (C) 2007
 * @author Daniel Bergqvist   Copyright (C) 2018
 */
public class DefaultLogixNGManager extends AbstractManager<LogixNG>
        implements LogixNG_Manager {

    
    private final Map<String, Manager<? extends MaleSocket>> _managers = new HashMap<>();
    private final Clipboard _clipboard = new DefaultClipboard();
    private final Stack _stack = new DefaultStack();
    private SymbolTable _symbolTable;
    
    
    public DefaultLogixNGManager() {
        // The LogixNGPreferences class may load plugins so we must ensure
        // it's loaded here.
        InstanceManager.getDefault(LogixNGPreferences.class);
    }

    @Override
    public int getXMLOrder() {
        return LOGIXNGS;
    }

    @Override
    public char typeLetter() {
        return 'Q';
    }

    /**
     * Test if parameter is a properly formatted system name.
     *
     * @param systemName the system name
     * @return enum indicating current validity, which might be just as a prefix
     */
    @Override
    public NameValidity validSystemNameFormat(String systemName) {
        return LogixNG_Manager.validSystemNameFormat(
                getSubSystemNamePrefix(), systemName);
//        if (systemName.matches(getSubSystemNamePrefix()+"(:AUTO:)?\\d+")) {
//            return NameValidity.VALID;
//        } else {
//            return NameValidity.INVALID;
//        }
    }

    /**
     * Method to create a new LogixNG if the LogixNG does not exist.
     * <p>
     * Returns null if
     * a Logix with the same systemName or userName already exists, or if there
     * is trouble creating a new LogixNG.
     */
    @Override
    public LogixNG createLogixNG(String systemName, String userName)
            throws IllegalArgumentException {
        
        // Check that LogixNG does not already exist
        LogixNG x;
        if (userName != null && !userName.equals("")) {
            x = getByUserName(userName);
            if (x != null) {
                return null;
            }
        }
        x = getBySystemName(systemName);
        if (x != null) {
            return null;
        }
        // Check if system name is valid
        if (this.validSystemNameFormat(systemName) != NameValidity.VALID) {
            throw new IllegalArgumentException("SystemName " + systemName + " is not in the correct format");
        }
        // LogixNG does not exist, create a new LogixNG
        x = new DefaultLogixNG(systemName, userName);
        // save in the maps
        register(x);
        
        // Keep track of the last created auto system name
        updateAutoNumber(systemName);
        
        return x;
    }

    @Override
    public LogixNG createLogixNG(String userName) throws IllegalArgumentException {
        return createLogixNG(getAutoSystemName(), userName);
    }
    
    @Override
    public LogixNG getLogixNG(String name) {
        LogixNG x = getByUserName(name);
        if (x != null) {
            return x;
        }
        return getBySystemName(name);
    }

    @Override
    public LogixNG getByUserName(String name) {
        return _tuser.get(name);
    }

    @Override
    public LogixNG getBySystemName(String name) {
        return _tsys.get(name);
    }

    /** {@inheritDoc} */
    @Override
    public String getBeanTypeHandled(boolean plural) {
        return Bundle.getMessage(plural ? "BeanNameLogixNGs" : "BeanNameLogixNG");
    }

    /** {@inheritDoc} */
    @Override
    public void resolveAllTrees() {
        for (LogixNG logixNG : _tsys.values()) {
            logixNG.setParentForAllChildren();
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public void setupAllLogixNGs() {
        for (LogixNG logixNG : _tsys.values()) {
            logixNG.setup();
            logixNG.setParentForAllChildren();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void activateAllLogixNGs() {
        for (LogixNG logixNG : _tsys.values()) {
            logixNG.setEnabled(true);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void deleteLogixNG(LogixNG x) {
        // delete the LogixNG
        deregister(x);
        x.dispose();
    }

    /** {@inheritDoc} */
    @Override
    public void setLoadDisabled(boolean s) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    /** {@inheritDoc} */
    @Override
    public void printTree(PrintWriter writer, String indent) {
        printTree(Locale.getDefault(), writer, indent);
    }
    
    /** {@inheritDoc} */
    @Override
    public void printTree(Locale locale, PrintWriter writer, String indent) {
        for (LogixNG logixNG : getNamedBeanSet()) {
            logixNG.printTree(locale, writer, indent, "");
            writer.println();
        }
    }
    
    
    static volatile DefaultLogixNGManager _instance = null;

    @InvokeOnGuiThread  // this method is not thread safe
    static public DefaultLogixNGManager instance() {
        if (!ThreadingUtil.isGUIThread()) {
            LoggingUtil.warnOnce(log, "instance() called on wrong thread");
        }
        
        if (_instance == null) {
            _instance = new DefaultLogixNGManager();
        }
        return (_instance);
    }

    /** {@inheritDoc} */
    @Override
    public Class<LogixNG> getNamedBeanClass() {
        return LogixNG.class;
    }
    
    /** {@inheritDoc} */
    @Override
    public Clipboard getClipboard() {
        return _clipboard;
    }
    
    /** {@inheritDoc} */
    @Override
    public Stack getStack() {
        return _stack;
    }
    
    /** {@inheritDoc} */
    @Override
    public SymbolTable getSymbolTable() {
        return _symbolTable;
    }
    
    /** {@inheritDoc} */
    @Override
    public void setSymbolTable(SymbolTable symbolTable) {
        _symbolTable = symbolTable;
    }
    
    /** {@inheritDoc} */
    @Override
    public void registerManager(Manager<? extends MaleSocket> manager) {
        _managers.put(manager.getClass().getName(), manager);
    }
    
    /** {@inheritDoc} */
    @Override
    public Manager<? extends MaleSocket> getManager(String className) {
        return _managers.get(className);
    }
    
    private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DefaultLogixNGManager.class);

}
