/*
 * Copyright 2004 The Apache Software Foundation.
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
package org.apache.jackrabbit.core.state;

import org.apache.jackrabbit.core.BLOBFileValue;
import org.apache.jackrabbit.core.InternalValue;
import org.apache.jackrabbit.core.PropertyId;
import org.apache.jackrabbit.core.QName;
import org.apache.jackrabbit.core.nodetype.PropDefId;
import org.apache.jackrabbit.core.util.Base64;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.io.*;

/**
 * <code>PropertyState</code> represents the state of a <code>Property</code>.
 */
public class PropertyState extends ItemState {

    static final long serialVersionUID = 5169077648295079463L;

    protected QName name;
    protected InternalValue[] values;
    protected int type;

    protected PropDefId defId;

    /**
     * Package private constructor
     *
     * @param overlayedState the backing property state being overlayed
     * @param initialStatus  the initial status of the property state object
     */
    PropertyState(PropertyState overlayedState, int initialStatus) {
        super(overlayedState, initialStatus);
        name = overlayedState.getName();
        type = overlayedState.getType();
        defId = overlayedState.getDefinitionId();
        values = overlayedState.getValues();
    }

    /**
     * Package private constructor
     *
     * @param name          name of the property
     * @param parentUUID    the uuid of the parent node
     * @param initialStatus the initial status of the property state object
     */
    PropertyState(QName name, String parentUUID, int initialStatus) {
        super(parentUUID, new PropertyId(parentUUID, name), initialStatus);
        this.name = name;
        type = PropertyType.UNDEFINED;
        values = new InternalValue[0];
    }

    //-------------------------------------------------------< public methods >
    /**
     * Determines if this item state represents a node.
     *
     * @return always false
     * @see ItemState#isNode
     */
    public boolean isNode() {
        return false;
    }

    /**
     * Returns the name of this property.
     *
     * @return the name of this property.
     */
    public QName getName() {
        return name;
    }

    /**
     * Sets the type of this property.
     *
     * @param type the type to be set
     * @see PropertyType
     */
    public void setType(int type) {
        this.type = type;
    }

    /**
     * Returns the type of this property.
     *
     * @return the type of this property.
     * @see PropertyType
     */
    public int getType() {
        return type;
    }

    /**
     * Returns the id of the definition applicable to this property state.
     *
     * @return the id of the definition
     */
    public PropDefId getDefinitionId() {
        return defId;
    }

    /**
     * Sets the id of the definition applicable to this property state.
     *
     * @param defId the id of the definition
     */
    public void setDefinitionId(PropDefId defId) {
        this.defId = defId;
    }

    /**
     * Sets the value(s) of this property.
     *
     * @param values the new values
     */
    public void setValues(InternalValue[] values) {
        this.values = values;
    }

    /**
     * Returns the value(s) of this property.
     *
     * @return the value(s) of this property.
     */
    public InternalValue[] getValues() {
        return values;
    }

    //-------------------------------------------------< Serializable support >
    private void writeObject(ObjectOutputStream out) throws IOException {
        // important: fields must be written in same order as they are
        // read in readObject(ObjectInputStream)
        out.writeObject(name);
        out.writeInt(type);
        if (values == null) {
            out.writeObject(null);
        } else {
            String[] strings = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                InternalValue val = values[i];
                try {
                    if (type == PropertyType.BINARY) {
                        // special handling required for binary value
                        BLOBFileValue blob = (BLOBFileValue) val.internalValue();
                        InputStream in = blob.getStream();
                        // use 32k initial buffer size as binary data is
                        // probably not just a couple of bytes
                        StringWriter writer = new StringWriter(32768);
                        try {
                            Base64.encode(in, writer);
                        } finally {
                            in.close();
                            writer.close();
                        }
                        strings[i] = writer.toString();
                    } else {
                        strings[i] = val.toString();
                    }
                } catch (IllegalStateException ise) {
                    throw new IOException(ise.getMessage());
                } catch (RepositoryException re) {
                    throw new IOException(re.getMessage());
                }
            }
            out.writeObject(strings);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        // important: fields must be read in same order as they are
        // written in writeObject(ObjectOutputStream)
        name = (QName) in.readObject();
        type = in.readInt();
        Object obj = in.readObject();
        if (obj == null) {
            values = null;
        } else {
            String[] strings = (String[]) obj;
            values = new InternalValue[strings.length];
            for (int i = 0; i < strings.length; i++) {
                String str = strings[i];
                if (type == PropertyType.BINARY) {
                    // special handling required for binary value
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(str.length());
                    Base64.decode(str, bos);
                    bos.close();
                    values[i] = InternalValue.create(new ByteArrayInputStream(bos.toByteArray()));
                } else {
                    values[i] = InternalValue.valueOf(str, type);
                }
            }
        }
    }
}
