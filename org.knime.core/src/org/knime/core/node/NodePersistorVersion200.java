/*
 * ------------------------------------------------------------------------
 *
 *  Copyright (C) 2003 - 2009
 *  University of Konstanz, Germany and
 *  KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Feb 13, 2008 (wiswedel): created
 */
package org.knime.core.node;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.knime.core.data.container.ContainerTable;
import org.knime.core.eclipseUtil.GlobalClassCreator;
import org.knime.core.internal.ReferencedFile;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortObjectSpecZipInputStream;
import org.knime.core.node.port.PortObjectSpecZipOutputStream;
import org.knime.core.node.port.PortObjectZipInputStream;
import org.knime.core.node.port.PortObjectZipOutputStream;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.PortUtil;
import org.knime.core.node.port.PortObject.PortObjectSerializer;
import org.knime.core.node.port.PortObjectSpec.PortObjectSpecSerializer;
import org.knime.core.node.workflow.SingleNodeContainerPersistorVersion200;
import org.knime.core.util.FileUtil;

/**
 *
 * @author wiswedel, University of Konstanz
 */
public class NodePersistorVersion200 extends NodePersistorVersion1xx {

    /** Prefix of associated port folders.
     * (Also used in export wizard, public declaration here.) */
    public static final String PORT_FOLDER_PREFIX = "port_";

    /** Prefix of associated port folders.
     * (Also used in export wizard, public declaration here.) */
    public static final String INTERNAL_TABLE_FOLDER_PREFIX = "internalTables";

    /** Invokes super constructor.
     * @param sncPersistor Forwared.*/
    public NodePersistorVersion200(
            final SingleNodeContainerPersistorVersion200 sncPersistor) {
        super(sncPersistor);
    }

    /**
     * Saves the node, node settings, and all internal structures, spec, data,
     * and models, to the given node directory (located at the node file).
     *
     * @param nodeFile To write node settings to.
     * @param execMon Used to report progress during saving.
     * @throws IOException If the node file can't be found or read.
     * @throws CanceledExecutionException If the saving has been canceled.
     */
    public void save(final Node node, final ReferencedFile nodeFile,
            final ExecutionMonitor execMon, final boolean isSaveData)
            throws IOException, CanceledExecutionException {
        NodeSettings settings = new NodeSettings(SETTINGS_FILE_NAME);
        final ReferencedFile nodeDirRef = nodeFile.getParent();
        if (nodeDirRef == null) {
            throw new IOException("parent file of file \"" + nodeFile
                    + "\" is not represented as object of class "
                    + ReferencedFile.class.getSimpleName());
        }
        saveCustomName(node, settings);
        node.saveSettingsTo(settings);
        saveHasContent(node, settings);
        saveWarningMessage(node, settings);
        ReferencedFile nodeInternDirRef = getNodeInternDirectory(nodeDirRef);
        File nodeInternDir = nodeInternDirRef.getFile();
        if (nodeInternDir.exists()) {
            FileUtil.deleteRecursively(nodeInternDir);
        }
        ExecutionMonitor internalMon = execMon.createSilentSubProgress(0.2);
        ExecutionMonitor portMon = execMon.createSilentSubProgress(0.6);
        ExecutionMonitor intTblsMon = execMon.createSilentSubProgress(0.1);
        execMon.setMessage("Internals");
        if (isSaveData) {
            saveNodeInternDirectory(node, nodeInternDir, settings, internalMon);
        }
        internalMon.setProgress(1.0);
        execMon.setMessage("Ports");
        savePorts(node, nodeDirRef, settings, portMon, isSaveData);
        portMon.setProgress(1.0);
        execMon.setMessage("Internal Tables");
        saveInternalHeldTables(node, nodeDirRef, settings, portMon, isSaveData);
        intTblsMon.setProgress(1.0);
        settings.saveToXML(new BufferedOutputStream(new FileOutputStream(
                nodeFile.getFile())));
        execMon.setProgress(1.0);
    }

    protected void savePorts(final Node node, final ReferencedFile nodeDirRef,
            final NodeSettingsWO settings, final ExecutionMonitor exec,
            final boolean saveData) throws IOException,
            CanceledExecutionException {
        if (node.getNrOutPorts() == 0) {
            return;
        }
        final int portCount = node.getNrOutPorts();
        NodeSettingsWO portSettings = settings.addNodeSettings("ports");
        exec.setMessage("Saving outport data");
        for (int i = 0; i < portCount; i++) {
            String portName = PORT_FOLDER_PREFIX + i;
            ExecutionMonitor subProgress =
                    exec.createSubProgress(1.0 / portCount);
            NodeSettingsWO singlePortSetting =
                    portSettings.addNodeSettings(portName);
            singlePortSetting.addInt("index", i);
            PortObject object = node.getOutputObject(i);
            String portDirName;
            if (object != null && saveData) {
                portDirName = portName;
                ReferencedFile portDirRef =
                        new ReferencedFile(nodeDirRef, portDirName);
                File portDir = portDirRef.getFile();
                subProgress.setMessage("Cleaning directory "
                        + portDir.getAbsolutePath());
                FileUtil.deleteRecursively(portDir);
                portDir.mkdir();
                if (!portDir.isDirectory() || !portDir.canWrite()) {
                    throw new IOException("Can not write port directory "
                            + portDir.getAbsolutePath());
                }
                savePort(node, portDir, singlePortSetting, subProgress, i,
                        saveData);
            } else {
                portDirName = null;
            }
            singlePortSetting.addString("port_dir_location", portDirName);
            subProgress.setProgress(1.0);
        }
    }

    protected void saveInternalHeldTables(final Node node,
            final ReferencedFile nodeDirRef, final NodeSettingsWO settings,
            final ExecutionMonitor exec, final boolean saveData)
            throws IOException, CanceledExecutionException {
        BufferedDataTable[] internalTbls = node.getInternalHeldTables();
        if (internalTbls == null) {
            return;
        }
        final int internalTblsCount = internalTbls.length;
        NodeSettingsWO subSettings = settings.addNodeSettings("internalTables");
        String subDirName = INTERNAL_TABLE_FOLDER_PREFIX;
        ReferencedFile subDirFile = new ReferencedFile(nodeDirRef, subDirName);
        subSettings.addString("location", subDirName);
        NodeSettingsWO portSettings = subSettings.addNodeSettings("content");
        FileUtil.deleteRecursively(subDirFile.getFile());
        subDirFile.getFile().mkdirs();

        exec.setMessage("Saving internally held data");
        for (int i = 0; i < internalTblsCount; i++) {
            BufferedDataTable t = internalTbls[i];
            String tblName = "table_" + i;
            ExecutionMonitor subProgress =
                    exec.createSubProgress(1.0 / internalTblsCount);
            NodeSettingsWO singlePortSetting =
                    portSettings.addNodeSettings(tblName);
            singlePortSetting.addInt("index", i);
            String tblDirName;
            if (t != null) {
                tblDirName = tblName;
                ReferencedFile portDirRef =
                        new ReferencedFile(subDirFile, tblDirName);
                File portDir = portDirRef.getFile();
                portDir.mkdir();
                if (!portDir.isDirectory() || !portDir.canWrite()) {
                    throw new IOException("Can not write table directory "
                            + portDir.getAbsolutePath());
                }
                t.save(portDir, exec);
            } else {
                tblDirName = null;
            }
            singlePortSetting.addString("table_dir_location", tblDirName);
            subProgress.setProgress(1.0);
        }
    }

    protected void savePort(final Node node, final File portDir,
            final NodeSettingsWO settings, final ExecutionMonitor exec,
            final int portIdx, final boolean saveData) throws IOException,
            CanceledExecutionException {
        PortObjectSpec spec = node.getOutputSpec(portIdx);
        settings.addString("port_spec_class", spec != null ? spec.getClass()
                .getName() : null);
        PortObject object = node.getOutputObject(portIdx);
        String summary = node.getOutputObjectSummary(portIdx);
        boolean isSaveObject = saveData && object != null;
        settings.addString("port_object_class", isSaveObject ? object
                .getClass().getName() : null);
        if (saveData && object != null) {
            settings.addString("port_object_summary", summary);
        }
        boolean isBDT = object instanceof BufferedDataTable
            || node.getOutputType(portIdx).equals(BufferedDataTable.TYPE);
        if (isBDT) {
            assert object == null || object instanceof BufferedDataTable
                : "Expected BufferedDataTable, got "
                    + object.getClass().getSimpleName();
            // executed and instructed to save data
            if (saveData && object != null) {
                ((BufferedDataTable)object).save(portDir, exec);
            }
        } else {
            exec.setMessage("Saving specification");
            if (isSaveObject) {
                assert spec != null
                : "Spec is null but port object is non-null (port "
                    + portIdx + " of node " + node.getName() + ")";
                if (!(object instanceof BufferedDataTable)) {
                    String specDirName = "spec";
                    String specFileName = "spec.zip";
                    String specPath = specDirName + "/" + specFileName;
                    File specDir = new File(portDir, specDirName);
                    specDir.mkdir();
                    if (!specDir.isDirectory() || !specDir.canWrite()) {
                        throw new IOException("Can't create directory "
                                + specDir.getAbsolutePath());
                    }

                    File specFile = new File(specDir, specFileName);
                    PortObjectSpecZipOutputStream out =
                        PortUtil.getPortObjectSpecZipOutputStream(
                                new BufferedOutputStream(
                                        new FileOutputStream(specFile)));
                    settings.addString("port_spec_location", specPath);
                    PortObjectSpecSerializer serializer =
                        PortUtil.getPortObjectSpecSerializer(spec.getClass());
                    serializer.savePortObjectSpec(spec, out);
                    out.close();
                }
                String objectDirName = null;
                objectDirName = "object";
                File objectDir = new File(portDir, objectDirName);
                objectDir.mkdir();
                if (!objectDir.isDirectory() || !objectDir.canWrite()) {
                    throw new IOException("Can't create directory "
                            + objectDir.getAbsolutePath());
                }
                String objectPath;
                // object is BDT, but port type is not BDT.TYPE - still though..
                if (object instanceof BufferedDataTable) {
                    objectPath = objectDirName;
                    saveBufferedDataTable((BufferedDataTable)object, objectDir,
                            exec);
                } else {
                    String objectFileName = "portobject.zip";
                    objectPath = objectDirName + "/" + objectFileName;
                    File file = new File(objectDir, objectFileName);
                    PortObjectZipOutputStream out =
                        PortUtil.getPortObjectZipOutputStream(
                                new BufferedOutputStream(
                                        new FileOutputStream(file)));
                    PortObjectSerializer serializer =
                            PortUtil.getPortObjectSerializer(object.getClass());
                    serializer.savePortObject(object, out, exec);
                    out.close();
                }
                settings.addString("port_object_location", objectPath);
            }
        }
    }

    private void saveBufferedDataTable(final BufferedDataTable table,
            final File directory, final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        table.save(directory, exec);
    }

    protected void saveHasContent(final Node node, final NodeSettingsWO settings) {
        boolean hasContent = node.hasContent();
        settings.addBoolean("hasContent", hasContent);
    }

    protected void saveWarningMessage(
            final Node node, final NodeSettingsWO settings) {
        String warnMessage = node.getWarningMessageFromModel();
        if (warnMessage != null) {
            settings.addString(CFG_NODE_MESSAGE, warnMessage);
        }
    }

    protected void saveNodeInternDirectory(final Node node,
            final File nodeInternDir, final NodeSettingsWO settings,
            final ExecutionMonitor exec) throws CanceledExecutionException {
        node.saveInternals(nodeInternDir, exec);
    }

    protected void saveCustomName(final Node node, final NodeSettingsWO settings) {
        settings.addString(CFG_NAME, node.getName());
    }

    /** {@inheritDoc} */
    @Override
    protected boolean loadIsExecuted(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected String loadWarningMessage(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        return settings.getString(CFG_NODE_MESSAGE, null);
    }

    /** {@inheritDoc} */
    @Override
    public LoadNodeModelSettingsFailPolicy getModelSettingsFailPolicy() {
        LoadNodeModelSettingsFailPolicy result =
            getSingleNodeContainerPersistor().getModelSettingsFailPolicy();
        assert result != null : "fail policy is null";
        return result;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean loadHasContent(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        return settings.getBoolean("hasContent");
    }

    /** {@inheritDoc} */
    @Override
    protected boolean loadIsConfigured(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected void loadInternalHeldTables(final Node node, final ExecutionMonitor execMon,
            final NodeSettingsRO settings,
            final Map<Integer, BufferedDataTable> loadTblRep,
            final HashMap<Integer, ContainerTable> tblRep) throws IOException,
            InvalidSettingsException, CanceledExecutionException {
        if (!settings.containsKey("internalTables")) {
            return;
        }
        NodeSettingsRO subSettings = settings.getNodeSettings("internalTables");
        String subDirName = subSettings.getString("location");
        ReferencedFile subDirFile =
            new ReferencedFile(getNodeDirectory(), subDirName);
        NodeSettingsRO portSettings = subSettings.getNodeSettings("content");
        Set<String> keySet = portSettings.keySet();
        BufferedDataTable[] result = new BufferedDataTable[keySet.size()];
        for (String s : keySet) {
            ExecutionMonitor subProgress =
                execMon.createSubProgress(1.0 / result.length);
            NodeSettingsRO singlePortSetting =
                portSettings.getNodeSettings(s);
            int index = singlePortSetting.getInt("index");
            if (index < 0 || index >= result.length) {
                throw new InvalidSettingsException("Invalid index: " + index);
            }
            String location = singlePortSetting.getString("table_dir_location");
            if (location == null) {
                result[index] = null;
            } else {
                ReferencedFile portDirRef =
                    new ReferencedFile(subDirFile, location);
                File portDir = portDirRef.getFile();
                if (!portDir.isDirectory() || !portDir.canRead()) {
                    throw new IOException("Can not read table directory "
                            + portDir.getAbsolutePath());
                }
                BufferedDataTable t = loadBufferedDataTable(
                        portDirRef, subProgress, loadTblRep, tblRep);
                result[index] = t;
            }
            subProgress.setProgress(1.0);
        }
        setInternalHeldTables(result);
    }

    /** {@inheritDoc} */
    @Override
    protected void loadPorts(final Node node, final ExecutionMonitor exec,
            final NodeSettingsRO settings,
            final Map<Integer, BufferedDataTable> loadTblRep,
            final HashMap<Integer, ContainerTable> tblRep) throws IOException,
            InvalidSettingsException, CanceledExecutionException {
        if (node.getNrOutPorts() == 0) {
            return;
        }
        final int portCount = node.getNrOutPorts();
        NodeSettingsRO portSettings = settings.getNodeSettings("ports");
        exec.setMessage("Reading outport data");
        for (String key : portSettings.keySet()) {
            NodeSettingsRO singlePortSetting =
                    portSettings.getNodeSettings(key);
            ExecutionMonitor subProgress =
                    exec.createSubProgress(1 / (double)portCount);
            int index = singlePortSetting.getInt("index");
            if (index < 0 || index >= node.getNrOutPorts()) {
                throw new InvalidSettingsException(
                        "Invalid outport index in settings: " + index);
            }
            String portDirN = singlePortSetting.getString("port_dir_location");
            if (portDirN != null) {
                ReferencedFile portDir =
                        new ReferencedFile(getNodeDirectory(), portDirN);
                subProgress.setMessage("Port " + index);
                loadPort(node, portDir, singlePortSetting, subProgress, index,
                        loadTblRep, tblRep);
            }
            subProgress.setProgress(1.0);
        }
    }

    protected void loadPort(final Node node, final ReferencedFile portDir,
            final NodeSettingsRO settings, final ExecutionMonitor exec,
            final int portIdx,
            final Map<Integer, BufferedDataTable> loadTblRep,
            final HashMap<Integer, ContainerTable> tblRep) throws IOException,
            InvalidSettingsException, CanceledExecutionException {
        String specClass = settings.getString("port_spec_class");
        String objectClass = settings.getString("port_object_class");
        PortType designatedType = node.getOutputType(portIdx);
        PortObjectSpec spec = null;
        PortObject object = null;
        boolean isBDT =
                (BufferedDataTable.TYPE.getPortObjectClass().getName().equals(
                        objectClass) && BufferedDataTable.TYPE
                        .getPortObjectSpecClass().getName().equals(specClass))
                        || node.getOutputType(portIdx).equals(
                                BufferedDataTable.TYPE);
        if (isBDT) {
            if (specClass != null
                    && !specClass.equals(BufferedDataTable.TYPE
                            .getPortObjectSpecClass().getName())) {
                throw new IOException("Actual spec class \""
                        + specClass
                        + "\", expected \""
                        + BufferedDataTable.TYPE.getPortObjectSpecClass()
                                .getName() + "\"");
            }
            if (objectClass != null
                    && !objectClass.equals(BufferedDataTable.TYPE
                            .getPortObjectClass().getName())) {
                throw new IOException("Actual object class \"" + objectClass
                        + "\", expected \""
                        + BufferedDataTable.TYPE.getPortObjectClass().getName()
                        + "\"");
            }
            if (objectClass != null) {
                object = loadBufferedDataTable(
                        portDir, exec, loadTblRep, tblRep);
                ((BufferedDataTable)object).setOwnerRecursively(node);
                spec = ((BufferedDataTable)object).getDataTableSpec();
            } else if (specClass != null) {
                spec = BufferedDataTable.loadSpec(portDir);
            }
        } else {
            exec.setMessage("Loading specification");
            if (specClass != null) {
                Class<?> cl;
                try {
                    cl = GlobalClassCreator.createClass(specClass);
                } catch (ClassNotFoundException e) {
                    throw new IOException("Can't load class \"" + specClass
                            + "\"", e);
                }
                if (!PortObjectSpec.class.isAssignableFrom(cl)) {
                    throw new IOException("Class \"" + cl.getSimpleName()
                            + "\" does not a sub-class \""
                            + PortObjectSpec.class.getSimpleName() + "\"");
                }
                ReferencedFile specDirRef =
                        new ReferencedFile(portDir, settings
                                .getString("port_spec_location"));
                File specFile = specDirRef.getFile();
                if (!specFile.isFile()) {
                    throw new IOException("Can't read spec file "
                            + specFile.getAbsolutePath());
                }
                PortObjectSpecZipInputStream in =
                    PortUtil.getPortObjectSpecZipInputStream(
                            new BufferedInputStream(
                                    new FileInputStream(specFile)));
                PortObjectSpecSerializer<?> serializer =
                        PortUtil.getPortObjectSpecSerializer(cl
                                .asSubclass(PortObjectSpec.class));
                spec = serializer.loadPortObjectSpec(in);
                in.close();
                if (spec == null) {
                    throw new IOException("Serializer \""
                            + serializer.getClass().getName()
                            + "\" restored null spec ");
                }
            }
            if (spec != null && objectClass != null) {
                Class<?> cl;
                try {
                    cl = GlobalClassCreator.createClass(objectClass);
                } catch (ClassNotFoundException e) {
                    throw new IOException("Can't load port object class \""
                            + objectClass + "\"", e);
                }
                if (!PortObject.class.isAssignableFrom(cl)) {
                    throw new IOException("Class \"" + cl.getSimpleName()
                            + "\" does not a sub-class \""
                            + PortObject.class.getSimpleName() + "\"");
                }
                ReferencedFile objectFileRef =
                        new ReferencedFile(portDir, settings
                                .getString("port_object_location"));
                if (BufferedDataTable.class.equals(cl)) {
                    File objectDir = objectFileRef.getFile();
                    if (!objectDir.isDirectory()) {
                        throw new IOException("Can't read directory "
                                + objectDir.getAbsolutePath());
                    }
                    // can't be true, however as BDT can only be saved
                    // for adequate port types (handled above)
                    // we leave the code here for future versions..
                    object = loadBufferedDataTable(objectFileRef, exec,
                                    loadTblRep, tblRep);
                    ((BufferedDataTable)object).setOwnerRecursively(node);
                } else {
                    File objectFile = objectFileRef.getFile();
                    if (!objectFile.isFile()) {
                        throw new IOException("Can't read file "
                                + objectFile.getAbsolutePath());
                    }
                    // buffering both disc I/O and the gzip stream pays off
                    PortObjectZipInputStream in =
                        PortUtil.getPortObjectZipInputStream(
                                new BufferedInputStream(
                                        new FileInputStream(objectFile)));
                    PortObjectSerializer<?> serializer =
                            PortUtil.getPortObjectSerializer(cl
                                    .asSubclass(PortObject.class));
                    object = serializer.loadPortObject(in, spec, exec);
                    in.close();
                }
            }
        }
        if (spec != null) {
            if (!designatedType.getPortObjectSpecClass().isInstance(spec)) {
                throw new IOException("Actual port spec type (\""
                        + spec.getClass().getSimpleName()
                        + "\") does not match designated one (\""
                        + designatedType.getPortObjectSpecClass()
                                .getSimpleName() + "\")");
            }
        }
        String summary = null;
        if (object != null) {
            if (!designatedType.getPortObjectClass().isInstance(object)) {
                throw new IOException("Actual port object type (\""
                        + object.getClass().getSimpleName()
                        + "\") does not match designated one (\""
                        + designatedType.getPortObjectClass().getSimpleName()
                        + "\")");
            }
            summary = settings.getString("port_object_summary", null);
            if (summary == null) {
                summary = object.getSummary();
            }
        }
        setPortObjectSpec(portIdx, spec);
        setPortObject(portIdx, object);
        setPortObjectSummary(portIdx, summary);
    }

    private BufferedDataTable loadBufferedDataTable(
            final ReferencedFile objectDir, final ExecutionMonitor exec,
            final Map<Integer, BufferedDataTable> loadTblRep,
            final HashMap<Integer, ContainerTable> tblRep)
            throws CanceledExecutionException, IOException,
            InvalidSettingsException {
        return BufferedDataTable.loadFromFile(objectDir, /* ignored in 1.2+ */
        null, exec, loadTblRep, tblRep);
    }

}
