/* -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 * 
 * Copyright, 2003 - 2006
 * Universitaet Konstanz, Germany.
 * Lehrstuhl fuer Angewandte Informatik
 * Prof. Dr. Michael R. Berthold
 * 
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner.
 * -------------------------------------------------------------------
 * 
 * History
 *   07.07.2005 (mb): created
 */
package de.unikn.knime.core.data;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import de.unikn.knime.core.data.renderer.DataValueRendererFamily;
import de.unikn.knime.core.data.renderer.DefaultDataValueRendererFamily;
import de.unikn.knime.core.data.renderer.StringValueRenderer;

/** 
 * Interface of a <code>StringCell</code>, forces method to return string value.
 * 
 * @author M. Berthold, University of Konstanz
 */
public interface StringValue extends DataValue {
    
    /** Meta information to this value type.
     * @see DataValue#UTILITY
     */
    public static final UtilityFactory UTILITY = new StringUtilityFactory();

    /**
     * @return A String value.
     */
    String getStringValue();
    
    /** Implementations of the meta information of this value class. */
    public static class StringUtilityFactory extends UtilityFactory {
        /** Singleton icon to be used to display this cell type. */
        private static final Icon ICON;

        /** Load icon, use <code>null</code> if not available. */
        static {
            ImageIcon icon;
            try {
                ClassLoader loader = StringValue.class.getClassLoader();
                String path = 
                    StringValue.class.getPackage().getName().replace('.', '/');
                icon = new ImageIcon(
                        loader.getResource(path + "/icon/stringicon.png"));
            } catch (Exception e) {
                icon = null;
            }
            ICON = icon;
        }

        private static final StringCellComparator STRING_COMPARATOR = 
            new StringCellComparator();
        
        /** Only subclasses are allowed to instantiate this class. */
        protected StringUtilityFactory() {
        }

        /**
         * @see DataValue.UtilityFactory#getIcon()
         */
        @Override
        public Icon getIcon() {
            return ICON;
        }

        /**
         * @see UtilityFactory#getComparator()
         */
        @Override
        protected DataCellComparator getComparator() {
            return STRING_COMPARATOR;
        }
        
        /**
         * @see DataValue.UtilityFactory#getRendererFamily(DataColumnSpec)
         */
        @Override
        protected DataValueRendererFamily getRendererFamily(
                final DataColumnSpec spec) {
            return new DefaultDataValueRendererFamily(
                    StringValueRenderer.INSTANCE);
        }

    }

}
