/*
 * Copyright 2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.xerces.xs.datatypes;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * <p><b>EXPERIMENTAL: This interface should not be considered stable.
 * It is likely it may be altered or replaced in the future.</b></p>
 * 
 * <p>Interface to expose the value of 'decimal' and related datatypes.</p>
 * 
 * @author Naela Nissar, IBM
 * 
 * @version $Id$
 */
public interface XSDecimal {
     
    /**
     * @return the <code>BigDecimal</code> representation of this object
     */
    public BigDecimal getBigDecimal();
    
    /**
     * @return the <code>BigInteger</code> representation of this object 
     * @exception NumberFormatException if the decimal value cannot be represented as a <code>BigInteger</code> 
     */
    public BigInteger getBigInteger() throws NumberFormatException;
    
    /**
     * @return the long value representation of this object
     * @exception NumberFormatException if the decimal value cannot be represented as a <code>long</code>
     */
    public long getLong() throws NumberFormatException;
    
    /**
     * @return the int value representation of this object
     * @exception NumberFormatException if the decimal value cannot be represented as a int
     */
    public int getInt() throws NumberFormatException;
    
    /**
     * @return the short value representation of this object
     * @exception NumberFormatException if the decimal value cannot be represented as a short
     */
    public short getShort() throws NumberFormatException;
    
    /**
     * @return the byte value representation of this object
     * @exception NumberFormatException if the decimal value cannot be represented as a byte
     */
    public byte getByte() throws NumberFormatException;
}