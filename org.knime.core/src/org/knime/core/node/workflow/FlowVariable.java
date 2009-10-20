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
 *   Mar 15, 2007 (mb): created
 */
package org.knime.core.node.workflow;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

/**
 * FlowVariable holding local variables of basic types which can
 * be passed along connections in a workflow.
 * 
 * @author M. Berthold, University of Konstanz
 */
public final class FlowVariable extends FlowObject {
    
    /** reserved prefix for global flow variables. */
    public static final String GLOBAL_CONST_ID = "knime";

    /** Type of a variable, supports currently only scalars. */
    public static enum Type {
        /** double type. */
        DOUBLE,
        /** int type. */
        INTEGER,
        /** String type. */
        STRING
    };
    
    private final Type m_type;
    private final String m_name;
    private String m_valueS = null;
    private double m_valueD = Double.NaN;
    private int m_valueI = 0;
    

    private FlowVariable(final String name, final Type type,
            final boolean isGlobalConstant) {
        if (name == null || type == null) {
            throw new NullPointerException("Argument must not be null");
        }
        if (!isGlobalConstant && name.startsWith(GLOBAL_CONST_ID)) {
            throw new IllegalFlowObjectStackException(
                    "Name of flow variables must not start with \""
                    + GLOBAL_CONST_ID + "\": " + name);
        }
        if (isGlobalConstant && !name.startsWith(GLOBAL_CONST_ID)) {
            throw new IllegalFlowObjectStackException(
                    "Name of global flow constant must start with \"" 
                    + GLOBAL_CONST_ID + "\": " + name);
        }
        m_name = name;
        m_type = type;
    }
    
    /** create new FlowVariable representing a string.
     * 
     * @param name of the variable
     * @param valueS string value
     */
    public FlowVariable(final String name, final String valueS) {
        this(name, valueS, false);
    }

    /** create new FlowVariable representing a double.
     * 
     * @param name of the variable
     * @param valueD double value
     */
    public FlowVariable(final String name, final double valueD) {
        this(name, valueD, false);
    }

    /** create new FlowVariable representing an integer.
     * 
     * @param name of the variable
     * @param valueI int value
     */
    public FlowVariable(final String name, final int valueI) {
        this(name, valueI, false);
    }

    /** create new FlowVariable representing a string which can either
     * be a global workflow variable or a local one.
     * 
     * @param name of the variable
     * @param valueS string value
     * @param isGlobalConstant indicating if the variable is global or not
     */
    FlowVariable(final String name, final String valueS, 
            final boolean isGlobalConstant) {
        this(name, Type.STRING, isGlobalConstant);
        m_valueS = valueS;
    }
    
    /** create new FlowVariable representing a double which can either
     * be a global workflow variable or a local one.
     * 
     * @param name of the variable
     * @param valueD double value
     * @param isGlobalConstant indicating if the variable is global or not
     */
    FlowVariable(final String name, final double valueD,
            final boolean isGlobalConstant) {
        this(name, Type.DOUBLE, isGlobalConstant);
        m_valueD = valueD;
    }
    
    /** create new FlowVariable representing an integer which can either
     * be a global workflow variable or a local one.
     * 
     * @param name of the variable
     * @param valueI int value
     * @param isGlobalConstant indicating if the variable is global or not
     */
    FlowVariable(final String name, final int valueI,
            final boolean isGlobalConstant) {
        this(name, Type.INTEGER, isGlobalConstant);
        m_valueI = valueI;
    }

    /**
     * @return name of variable.
     */
    public String getName() {
        return m_name;
    }

    /**
     * @return true if the variable is a global workflow variable.
     */
    public boolean isGlobalConstant() {
        return m_name.startsWith(GLOBAL_CONST_ID);
    }
    
    /** @return the type */
    public Type getType() {
        return m_type;
    }

    /**
     * @return get string value of the variable or null if it's not a string.
     */
    public String getStringValue() {
        if (m_type != Type.STRING) {
            return null;
        }
        return m_valueS;
    }

    /**
     * @return get double value of the variable or Double.NaN if it's not a
     * double.
     */
    public double getDoubleValue() {
        if (m_type != Type.DOUBLE) {
            return Double.NaN;
        }
        return m_valueD;
    }

    /**
     * @return get int value of the variable or 0 if it's not an integer.
     */
    public int getIntValue() {
        if (m_type != Type.INTEGER) {
            return 0;
        }
        return m_valueI;
    }
    
    /** Saves this flow variable to a settings object. This method writes
     * directly into the argument object (no creating of intermediate child).
     * @param settings To save to.
     */
    void save(final NodeSettingsWO settings) {
        settings.addString("name", getName());
        settings.addString("class", getType().name());
        switch (getType()) {
        case INTEGER:
            settings.addInt("value", getIntValue());
            break;
        case DOUBLE:
            settings.addDouble("value", getDoubleValue());
            break;
        case STRING:
            settings.addString("value", getStringValue());
            break;
        default:
            assert false : "Unknown variable type: " + getType();
        }
    }
    
    /**
     * Read a flow variable from a settings object. This is the counterpart
     * to {@link #save(NodeSettingsWO)}.
     * @param sub To load from.
     * @return A new {@link FlowVariable} read from the settings object.
     * @throws InvalidSettingsException If that fails for any reason.
     */
    static FlowVariable load(final NodeSettingsRO sub)
        throws InvalidSettingsException {
        String name = sub.getString("name");
        String typeS = sub.getString("class");
        if (typeS == null || name == null) {
            throw new InvalidSettingsException("name or type is null");
        }
        Type varType;
        try {
            varType = Type.valueOf(typeS);
        } catch (final IllegalArgumentException e) {
            throw new InvalidSettingsException("invalid type " + typeS);
        }
        FlowVariable v;
        switch (varType) {
        case DOUBLE:
            v = new FlowVariable(name, sub.getDouble("value"));
            break;
        case INTEGER:
            v = new FlowVariable(name, sub.getInt("value"));
            break;
        case STRING:
            v = new FlowVariable(name, sub.getString("value"));
            break;
        default:
            throw new InvalidSettingsException("Unknown type " + varType);
        }
        return v;
    }
    
    /** {@inheritDoc} */
    @Override
    public String toString() {
        String value;
        switch (m_type) {
        case DOUBLE: value = Double.toString(m_valueD); break;
        case INTEGER: value = Integer.toString(m_valueI); break;
        case STRING: value = m_valueS; break;
        default: throw new InternalError("m_type must not be null");
        }
        return "SV: \"" + m_name + "\" (" + m_type + ": " + value + ")"; 
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof FlowVariable)) {
            return false;
        }
        FlowVariable v = (FlowVariable)obj;
        return v.getType().equals(getType()) && v.getName().equals(getName());
    }
    
    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return getType().hashCode() ^ getName().hashCode();
    }
    
}
