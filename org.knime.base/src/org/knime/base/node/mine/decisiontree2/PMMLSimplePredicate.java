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
 *   Sep 4, 2009 (morent): created
 */
package org.knime.base.node.mine.decisiontree2;

import javax.xml.transform.sax.TransformerHandler;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DoubleValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.config.Config;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Implements a SimplePredicate as specified in PMML
 * (<a>http://www.dmg.org/v4-0/TreeModel.html</a>).
 *
 * @author Dominik Morent, KNIME.com, Zurich, Switzerland
 */
public class PMMLSimplePredicate extends PMMLPredicate {
    /** The string representation of the predicate's XML-element. */
    public static final String NAME = "SimplePredicate";
    /** The key to store the threshold in configurations. */
    protected static final String THRESHOLD_KEY = "threshold";



    /** The threshold to compare against. */
    private String m_threshold;
    /* Used for storing numerical thresholds to avoid unnecessary casts. */
    private Double m_thresholdNumerical;

    /**
     * Build a new simple predicate.
     *
     * @param attribute the field the predicate is applied on
     * @param operator the string representation of the operator
     * @param value the value to be compared with (the threshold)
     */
    public PMMLSimplePredicate(final String attribute, final String operator,
            final String value) {
        setSplitAttribute(attribute);
        setOperator(operator);
        m_threshold = value;
    }

    /**
     * Build a new simple predicate.
     *
     * @param attribute the field the predicate is applied on
     * @param operator the PMML operator to be set
     * @param value the value to be compared with (the threshold)
     */
    public PMMLSimplePredicate(final String attribute,
            final PMMLOperator operator, final String value) {
        setSplitAttribute(attribute);
        setOperator(operator);
        setThreshold(value);
    }


    /**
     * @param threshold the threshold to set
     */
    public void setThreshold(final String threshold) {
        m_threshold = threshold;
        try {
            m_thresholdNumerical = Double.valueOf(threshold);
        } catch (final NumberFormatException e) {
            // no numerical threshold
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean evaluate(final DataRow row, final DataTableSpec spec) {
        cacheSpec(spec);
        assert getPreviousIndex() != -1;
        DataCell cell = row.getCell(getPreviousIndex());
        if (cell.isMissing()) {
            return null;
        }
        if (cell.getType().isCompatible(DoubleValue.class)) {
            if (m_thresholdNumerical == null) {
                m_thresholdNumerical = Double.valueOf(m_threshold);
            }

            Double value = ((DoubleValue)cell).getDoubleValue();
            return getOperator().evaluate(value, m_thresholdNumerical);
        } else {
            String s = cell.toString();
            return getOperator().evaluate(s, m_threshold);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        if (m_thresholdNumerical != null) {
            return getSplitAttribute() + " " + getOperator().getSymbol() + " "
            + NUMBERFORMAT.format(m_thresholdNumerical);
        } else {
            return getSplitAttribute() + " " + getOperator().getSymbol() + " "
            + m_threshold;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writePMML(final TransformerHandler handler)
            throws SAXException {
        AttributesImpl predAtts = new AttributesImpl();
        predAtts.addAttribute(null, null, "field", CDATA, getSplitAttribute());
        predAtts.addAttribute(null, null, "operator", CDATA,
                getOperator().toString());
        predAtts.addAttribute(null, null, "value", CDATA, m_threshold);
        handler.startElement(null, null, "SimplePredicate", predAtts);
        handler.endElement(null, null, "SimplePredicate");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadFromPredParams(final Config conf)
            throws InvalidSettingsException {
        assert conf.getString(PMMLPredicate.TYPE_KEY).equals(NAME);
        setSplitAttribute(conf.getString(PMMLPredicate.ATTRIBUTE_KEY));
        setOperator(conf.getString(PMMLPredicate.OPERATOR_KEY));
        setThreshold(conf.getString(THRESHOLD_KEY));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveToPredParams(final Config conf) {
        conf.addString(PMMLPredicate.TYPE_KEY, NAME);
        conf.addString(PMMLPredicate.ATTRIBUTE_KEY, getSplitAttribute());
        conf.addString(PMMLPredicate.OPERATOR_KEY, getOperator().toString());
        conf.addString(THRESHOLD_KEY, m_threshold);
    }

   }
