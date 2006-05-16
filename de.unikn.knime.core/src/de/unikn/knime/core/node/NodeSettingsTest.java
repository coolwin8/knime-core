/* @(#)$RCSfile$ 
 * $Revision$ $Date$ $Author$
 * 
 * -------------------------------------------------------------------
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
 */
package de.unikn.knime.core.node;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Set;

import junit.framework.TestCase;
import de.unikn.knime.core.data.DataCell;
import de.unikn.knime.core.data.DataType;
import de.unikn.knime.core.data.def.DoubleCell;
import de.unikn.knime.core.data.def.DefaultFuzzyIntervalCell;
import de.unikn.knime.core.data.def.DefaultFuzzyNumberCell;
import de.unikn.knime.core.data.def.IntCell;
import de.unikn.knime.core.data.def.StringCell;
import de.unikn.knime.core.node.config.Config;

/**
 * Test the <code>Config</code> class.
 * 
 * @author Thomas Gabriel, University of Konstanz
 */
public final class NodeSettingsTest extends TestCase {

    private static final NodeSettings SETT = new NodeSettings("test-settings");

    /**
     * @see junit.framework.TestCase#tearDown()
     */
    public void tearDown() {
        System.out.println(SETT.toString());
        testFile();
        testXML();
    }

    /**
     * Test write/read of ints.
     * 
     * @throws Exception Should not happen.
     */
    public void testInt() throws Exception {
        try {
            SETT.addInt(null, 11);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        String key = "kint";
        SETT.addInt(key, 5);
        assertTrue(SETT.containsKey(key));
        assertTrue(5 == SETT.getInt(key));
        assertTrue(5 == SETT.getInt(key, -1));
        key += "array";
        SETT.addIntArray(key, new int[]{42, 13});
        assertTrue(SETT.containsKey(key));
        int[] a = SETT.getIntArray(key);
        assertTrue(a[0] == 42 && a[1] == 13);
        a = SETT.getIntArray(key, new int[0]);
        assertTrue(a[0] == 42 && a[1] == 13);
        key = "kint_array_0";
        SETT.addIntArray(key, new int[0]);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getIntArray(key).length == 0);
        assertTrue(SETT.getIntArray(key, new int[1]).length == 0);
        key = "kint-";
        SETT.addIntArray(key, null);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getIntArray(key) == null);
    }

    /**
     * Test write/read of doubles.
     * 
     * @throws Exception Should not happen.
     */

    public void testDouble() throws Exception {
        try {
            SETT.addDouble(null, 11.11);
            fail();
        } catch (IllegalArgumentException iae) {
            assertTrue(true);
        }
        String key = "kdouble";
        SETT.addDouble(key, 5.5);
        assertTrue(SETT.containsKey(key));
        assertTrue(5.5 == SETT.getDouble(key));
        assertTrue(5.5 == SETT.getDouble(key, -1.0));
        key += "array";
        SETT.addDoubleArray(key, new double[]{42.42, 13.13});
        assertTrue(SETT.containsKey(key));
        double[] a = SETT.getDoubleArray(key);
        assertTrue(a[0] == 42.42 && a[1] == 13.13);
        a = SETT.getDoubleArray(key, new double[0]);
        assertTrue(a[0] == 42.42 && a[1] == 13.13);
        key = "kdouble_array_0";
        SETT.addDoubleArray(key, new double[0]);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getDoubleArray(key).length == 0);
        assertTrue(SETT.getDoubleArray(key, new double[1]).length == 0);
        key = "kdouble-";
        SETT.addDoubleArray(key, null);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getDoubleArray(key) == null);
    }

    /**
     * Test write/read of ints.
     * 
     * @throws Exception Should not happen.
     */
    public void testBoolean() throws Exception {
        try {
            SETT.addBoolean(null, true);
            fail();
        } catch (IllegalArgumentException iae) {
            assertTrue(true);
        }
        String key = "kboolean";
        SETT.addBoolean(key, true);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getBoolean(key));
        assertTrue(SETT.getBoolean(key, false));
        key += "array";
        SETT.addBooleanArray(key, new boolean[]{false, true});
        assertTrue(SETT.containsKey(key));
        boolean[] a = SETT.getBooleanArray(key);
        assertTrue(!a[0] && a[1]);
        a = SETT.getBooleanArray(key, new boolean[0]);
        assertTrue(!a[0] && a[1]);
        key = "kboolean_array_0";
        SETT.addBooleanArray(key, new boolean[0]);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getBooleanArray(key).length == 0);
        assertTrue(SETT.getBooleanArray(key, new boolean[1]).length == 0);
        key = "kboolean-";
        SETT.addBooleanArray(key, null);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getBooleanArray(key) == null);
    }

    /**
     * Test write/read of chars.
     * 
     * @throws Exception Should not happen.
     */
    public void testChar() throws Exception {
        try {
            SETT.addChar(null, '5');
            fail();
        } catch (IllegalArgumentException iae) {
            assertTrue(true);
        }
        String key = "kchar";
        SETT.addChar(key, '5');
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getChar(key) == '5');
        assertTrue(SETT.getChar(key, 'n') == '5');
        key += "array";
        SETT.addCharArray(key, new char[]{'4', '2'});
        assertTrue(SETT.containsKey(key));
        char[] a = SETT.getCharArray(key);
        assertTrue(a[0] == '4' && a[1] == '2');
        a = SETT.getCharArray(key, new char[0]);
        assertTrue(a[0] == '4' && a[1] == '2');
        key = "kchar_array_0";
        SETT.addCharArray(key, new char[0]);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getCharArray(key).length == 0);
        assertTrue(SETT.getCharArray(key, new char[1]).length == 0);
        key = "kchar-";
        SETT.addCharArray(key, null);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getCharArray(key) == null);
    }

    /**
     * Test write/read of shorts.
     * 
     * @throws Exception Should not happen.
     */
    public void testShort() throws Exception {
        try {
            SETT.addShort(null, (short)'5');
            fail();
        } catch (IllegalArgumentException iae) {
            assertTrue(true);
        }
        String key = "kshort";
        SETT.addShort(key, (short)'5');
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getShort(key) == '5');
        assertTrue(SETT.getShort(key, (short)'n') == (short)'5');
        key += "array";
        SETT.addShortArray(key, new short[]{'4', '2'});
        assertTrue(SETT.containsKey(key));
        short[] a = SETT.getShortArray(key);
        assertTrue(a[0] == '4' && a[1] == '2');
        a = SETT.getShortArray(key, new short[0]);
        assertTrue(a[0] == '4' && a[1] == '2');
        key = "kshort_array_0";
        SETT.addShortArray(key, new short[0]);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getShortArray(key).length == 0);
        assertTrue(SETT.getShortArray(key, new short[1]).length == 0);
        key = "kshort-";
        SETT.addShortArray(key, null);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getShortArray(key) == null);
    }

    /**
     * Test write/read of bytes.
     * 
     * @throws Exception Should not happen.
     */
    public void testByte() throws Exception {
        try {
            SETT.addByte(null, (byte)'n');
            fail();
        } catch (IllegalArgumentException iae) {
            assertTrue(true);
        }
        String key = "kbyte";
        SETT.addByte(key, (byte)'b');
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getByte(key) == (byte)'b');
        assertTrue(SETT.getByte(key, (byte)'n') == (byte)'b');
        key += "array";
        SETT.addByteArray(key, new byte[]{'4', '2'});
        assertTrue(SETT.containsKey(key));
        byte[] a = SETT.getByteArray(key);
        assertTrue(a[0] == '4' && a[1] == '2');
        a = SETT.getByteArray(key, new byte[0]);
        assertTrue(a[0] == '4' && a[1] == '2');
        key = "kbyte_array_0";
        SETT.addByteArray(key, new byte[0]);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getByteArray(key).length == 0);
        assertTrue(SETT.getByteArray(key, new byte[1]).length == 0);
        key = "kbyte-";
        SETT.addByteArray(key, null);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getByteArray(key) == null);
    }

    /**
     * Test write/read of DataCells.
     * 
     * @throws Exception Should not happen.
     */
    public void testDataCell() throws Exception {
        try {
            SETT.addDataCell(null, new StringCell("null"));
            fail();
        } catch (IllegalArgumentException iae) {
            assertTrue(true);
        }
        String key = "nullDataCell";
        SETT.addDataCell(key, null);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getDataCell(key) == null);
        DataCell nullCell = new StringCell("null");
        assertTrue(SETT.getDataCell(key, nullCell) == null);
        key = "kDataCell";
        SETT.addDataCell(key, new StringCell("B"));
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getDataCell(key).equals(new StringCell("B")));
        assertTrue(SETT.getDataCell(key, null).equals(
                new StringCell("B")));
        key += "array";
        SETT.addDataCellArray(key, new DataCell[]{new StringCell("T"),
                new StringCell("P"), new StringCell("M")});
        assertTrue(SETT.containsKey(key));
        DataCell[] a = SETT.getDataCellArray(key);
        assertTrue(a[0].equals(new StringCell("T")));
        assertTrue(a[1].equals(new StringCell("P")));
        assertTrue(a[2].equals(new StringCell("M")));
        a = SETT.getDataCellArray(key, new DataCell[0]);
        assertTrue(a[0].equals(new StringCell("T")));
        assertTrue(a[1].equals(new StringCell("P")));
        assertTrue(a[2].equals(new StringCell("M")));
        key = "kDataCell_array_0";
        SETT.addDataCellArray(key, new DataCell[0]);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getDataCellArray(key).length == 0);
        assertTrue(SETT.getDataCellArray(key, new DataCell[1]).length == 0);
        key = "kDataCell-";
        SETT.addDataCellArray(key, null);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getDataCellArray(key) == null);
        key = "unknownDataCell";
        DataCell unknownCell = new DefaultFuzzyNumberCell(0.0, 1.0, 2.0);
        SETT.addDataCell(key, unknownCell);
        assertTrue(SETT.containsKey(key));
        assertTrue(unknownCell.equals(SETT.getDataCell(key)));
    }

    /**
     * Test write/read of DataType elements.
     * 
     * @throws Exception Should not happen.
     */
    public void testDataType() throws Exception {
        try {
            SETT.addDataType(null, StringCell.TYPE);
            fail();
        } catch (IllegalArgumentException iae) {
            assertTrue(true);
        }
        String key = "nullDataType";
        SETT.addDataType(key, null);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getDataType(key) == null);
        assertTrue(SETT.getDataType(key, StringCell.TYPE) == null);
        key = "kDataType";
        SETT.addDataType(key, DoubleCell.TYPE);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getDataType(key).equals(DoubleCell.TYPE));
        assertTrue(SETT.getDataType(key, null).equals(DoubleCell.TYPE));
        key += "array";
        SETT.addDataTypeArray(key, new DataType[]{DoubleCell.TYPE,
                StringCell.TYPE, IntCell.TYPE});
        assertTrue(SETT.containsKey(key));
        DataType[] a = SETT.getDataTypeArray(key);
        assertTrue(a[0].equals(DoubleCell.TYPE));
        assertTrue(a[1].equals(StringCell.TYPE));
        assertTrue(a[2].equals(IntCell.TYPE));
        a = SETT.getDataTypeArray(key, new DataType[0]);
        assertTrue(a[0].equals(DoubleCell.TYPE));
        assertTrue(a[1].equals(StringCell.TYPE));
        assertTrue(a[2].equals(IntCell.TYPE));
        key = "kDataType_array_0";
        SETT.addDataTypeArray(key, new DataType[0]);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getDataTypeArray(key).length == 0);
        assertTrue(SETT.getDataTypeArray(key, new DataType[1]).length == 0);
        key = "kDataType-";
        SETT.addDataTypeArray(key, null);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getDataTypeArray(key) == null);
        key = "unknownDataType";
        SETT.addDataType(key, DefaultFuzzyIntervalCell.TYPE);
        assertTrue(SETT.containsKey(key));
        DataType unknownType = SETT.getDataType(key);
        assertTrue(DefaultFuzzyIntervalCell.TYPE.equals(unknownType));
    }

    /**
     * Test write/read of Strings.
     * 
     * @throws Exception Should not happen.
     */
    public void testString() throws Exception {
        try {
            SETT.addString(null, "null");
            fail();
        } catch (IllegalArgumentException iae) {
            assertTrue(true);
        }
        String key = "nullString";
        SETT.addString(key, null);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getString(key) == null);
        assertTrue(SETT.getString(key, "null") == null);
        key = "kString";
        SETT.addString(key, "B");
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getString(key).equals("B"));
        assertTrue(SETT.getString(key, null).equals("B"));
        key += "array";
        SETT.addStringArray(key, new String[]{"T", "P", "M"});
        assertTrue(SETT.containsKey(key));
        String[] a = SETT.getStringArray(key);
        assertTrue(a[0].equals("T"));
        assertTrue(a[1].equals("P"));
        assertTrue(a[2].equals("M"));
        a = SETT.getStringArray(key, new String[0]);
        assertTrue(a[0].equals("T"));
        assertTrue(a[1].equals("P"));
        assertTrue(a[2].equals("M"));
        key = "kString_array_0";
        SETT.addStringArray(key, new String[0]);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getStringArray(key).length == 0);
        assertTrue(SETT.getStringArray(key, new String[1]).length == 0);
        key = "kString-";
        SETT.addStringArray(key, null);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getStringArray(key) == null);
    }

    /**
     * Test write/read of NodeSettings.
     * 
     * @throws Exception Should not happen.
     */
    public void testConfig() throws Exception {
        try {
            SETT.addConfig((String)null);
            fail();
        } catch (IllegalArgumentException iae) {
            assertTrue(true);
        }
        String key = "kConfig";
        Config c = SETT.addConfig(key);
        assertTrue(SETT.containsKey(key));
        assertTrue(SETT.getConfig(key) == c);
        c.addString("kString_plus", "6");
        c.containsKey("kString_plus");
        assertTrue(c.getString("kString_plus", "-1").equals("6"));
    }

    /**
     * Tests <code>getKeySet()</code> and <code>getKeySet(String)</code>.
     */
    public void testKeySets() {
        Config key = SETT.addConfig("test_key_set");
        key.addConfig("ConfigA");
        key.addConfig("ConfigB");
        key.addConfig("ConfigC");
        key.addInt("intA", 0);
        key.addInt("intB", 1);
        assertTrue(key.containsKey("ConfigA"));
        assertTrue(key.containsKey("ConfigB"));
        assertTrue(key.containsKey("ConfigC"));
        assertTrue(key.containsKey("intA"));
        assertTrue(key.containsKey("intB"));
        Set<String> keys = key.keySet();
        assertFalse(keys == null);
        assertTrue("" + keys.size(), keys.size() == 5);
        String[] k = keys.toArray(new String[0]);
        assertTrue(k[0].equals("ConfigA"));
        assertTrue(k[1].equals("ConfigB"));
        assertTrue(k[2].equals("ConfigC"));
        assertTrue(k[3].equals("intA"));
        assertTrue(k[4].equals("intB"));
    }

    /**
     * Test \n, \r, and \t.
     * 
     * @throws Exception Should not happen.
     */
    public void testSpecialStrings() throws Exception {
        Config key = SETT.addConfig("special_strings");
        key.addString("N", "\n");
        key.addString("R", "\r");
        key.addString("T", "\t");
        key.addString("EMPTY", "");
        key.addString("LENGTH1", " ");
        key.addString("null", null);
        key.addString("NULL", "null");
        assertTrue(key.containsKey("N"));
        assertTrue(key.containsKey("R"));
        assertTrue(key.containsKey("T"));
        assertTrue(key.containsKey("EMPTY"));
        assertTrue(key.containsKey("null"));
        assertTrue(key.containsKey("NULL"));
        assertTrue(key.containsKey("LENGTH1"));
        assertTrue(key.getString("N").equals("\n"));
        assertTrue(key.getString("R").equals("\r"));
        assertTrue(key.getString("T").equals("\t"));
        assertTrue(key.getString("EMPTY").equals(""));
        assertTrue(key.getString("null"), key.getString("null") == null);
        assertTrue(key.getString("NULL").equals("null"));
        assertTrue(key.getString("LENGTH1").equals(" "));
    }
    
    /**
     * Test a 3x2x3 int array.
     * @throws InvalidSettingsException If a value could not be read.
     */
    public void testInt3DMatrix() throws InvalidSettingsException { 
        Config config = SETT.addConfig("matrix");
        // write int matrix
        int[][][] array = new int[][][]{{{1, 2, 4}, {5, 2, 6}, {7, 1, 9}}, 
                {{7, 6, 4}, {8, 2, 9}, {0, 1, 2}}};
        for (int r = 0; r < array.length; r++) {
            for (int i = 0; i < array[r].length; i++) {
                String key = "int_array_" + r + "_" + i;
                config.addIntArray(key, array[r][i]);
                assertTrue(config.containsKey(key));
                assertTrue(Arrays.equals(config.getIntArray(key), array[r][i]));
            }
        }
        // read and test int matrix
        for (int r = 0; r < array.length; r++) {
            for (int i = 0; i < array[r].length; i++) {
                String key = "int_array_" + r + "_" + i;
                assertTrue(config.containsKey(key));
                assertTrue(Arrays.equals(config.getIntArray(key), array[r][i]));
            }
        }
    }

    /**
     * Tests multiple keys for different types.
     */
    public void testKeys() {
        String key = "key-test";
        SETT.addConfig(key);
        SETT.addString(key, "string");
        SETT.addBoolean(key, true);
        SETT.addInt(key, -42);
        SETT.addDouble(key, 5.0);
        SETT.addShort(key, (short)'s');
        SETT.addChar(key, 'c');
        SETT.addByte(key, (byte)'b');
        SETT.addDataCell(key, null);
        SETT.addDataType(key, null);
    }

    /**
     * Test serialize/deserialize.
     */
    public void testFile() {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(os);
            // write this NodeSettings
            SETT.writeToFile(oos);
            // and read the NodeSettings again
            byte[] bytes = os.toByteArray();
            ByteArrayInputStream is = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(is);
            NodeSettings settings = NodeSettings.readFromFile(ois);
            assertTrue(settings.isIdentical(SETT));
        } catch (IOException ioe) {
            ioe.printStackTrace();
            fail();
        }
    }

    /**
     * Test XML read/write.
     */
    public void testXML() {
        // store to XML
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            SETT.saveToXML(os);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
        // read from XML
        try {
            InputStream is = new ByteArrayInputStream(os.toByteArray());
            NodeSettings settings = NodeSettings.loadFromXML(is);
            assertTrue(settings.isIdentical(SETT));
        } catch (IOException ioe) {
            ioe.printStackTrace();
            fail();
        }
    }

    /**
     * System entry point.
     * 
     * @param args The command line arguments.
     */
    public static void main(final String[] args) {
        junit.textui.TestRunner.run(NodeSettingsTest.class);
    }

} // NodeSettingsTest
