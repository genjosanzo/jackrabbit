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

import org.apache.commons.collections.ReferenceMap;
import org.apache.jackrabbit.core.ItemId;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * <code>ItemState</code> represents the state of an <code>Item</code>.
 */
public abstract class ItemState implements ItemStateListener, Serializable {

    static final long serialVersionUID = -6632715936921139872L;

    private static Logger log = Logger.getLogger(ItemState.class);

    /**
     * flags defining the current status of this <code>ItemState</code> instance
     */

    public static final int STATUS_UNDEFINED = 0;
    /**
     * 'existing', i.e. persistent state
     */
    public static final int STATUS_EXISTING = 1;
    /**
     * 'existing', i.e. persistent state that has been transiently modified (copy-on-write)
     */
    public static final int STATUS_EXISTING_MODIFIED = 2;
    /**
     * 'existing', i.e. persistent state that has been transiently removed (copy-on-write)
     */
    public static final int STATUS_EXISTING_REMOVED = 3;
    /**
     * 'new' state
     */
    public static final int STATUS_NEW = 4;
    /**
     * 'existing', i.e. persistent state that has been persistently modified by somebody else
     */
    public static final int STATUS_STALE_MODIFIED = 5;
    /**
     * 'existing', i.e. persistent state that has been destroyed by somebody else
     */
    public static final int STATUS_STALE_DESTROYED = 6;

    protected int status = STATUS_UNDEFINED;

    /**
     * the uuid of the (primary) parent node or <code>null</code> if this is the root node
     */
    protected String parentUUID;

    protected long lastModified;

    protected String baseVersionID;

    protected final ItemId id;

    /**
     * Listeners (soft references)
     */
    protected final transient Map listeners = Collections.synchronizedMap(new ReferenceMap(ReferenceMap.SOFT, ReferenceMap.SOFT));

    // the backing persistent item state (may be null)
    private transient ItemState overlayedState;

    /**
     * Protected constructor
     *
     * @param parentUUID    the UUID of the (primary) parent node or <code>null</code>
     * @param id            the id of the item state object
     * @param initialStatus the initial status of the item state object
     */
    protected ItemState(String parentUUID, ItemId id, int initialStatus) {
        switch (initialStatus) {
            case STATUS_EXISTING:
            case STATUS_NEW:
                status = initialStatus;
                break;
            default:
                String msg = "illegal status: " + initialStatus;
                log.error(msg);
                throw new IllegalArgumentException(msg);
        }
        this.id = id;
        this.parentUUID = parentUUID;
        baseVersionID = "v0.0";
        // @todo use modification count instead of ms (not precise enough)
        lastModified = System.currentTimeMillis();
        overlayedState = null;
    }

    /**
     * Protected constructor
     *
     * @param overlayedState backing persistent item state
     * @param initialStatus  the initial status of the new <code>ItemState</code> instance
     */
    protected ItemState(ItemState overlayedState, int initialStatus) {
        switch (initialStatus) {
            case STATUS_EXISTING_MODIFIED:
            case STATUS_EXISTING_REMOVED:
                status = initialStatus;
                break;
            default:
                String msg = "illegal status: " + initialStatus;
                log.error(msg);
                throw new IllegalArgumentException(msg);
        }
        parentUUID = overlayedState.parentUUID;
        baseVersionID = overlayedState.getBaseVersionID();
        lastModified = overlayedState.getLastModified();
        id = overlayedState.getId();
        this.overlayedState = overlayedState;
        // add this transient state as a listener on the overlayed state
        this.overlayedState.addListener(this);
    }

    /**
     * Called by <code>TransientItemStateManager</code> when this item state
     * is disposed.
     */
    void onDisposed() {
        // prepare this instance so it can be gc'ed
        listeners.clear();
        if (overlayedState != null) {
            overlayedState.removeListener(this);
            overlayedState = null;
        }
        status = STATUS_UNDEFINED;
    }

    /**
     * Notify the listeners that the persistent state this object is
     * representing has been discarded.
     */
    protected void notifyStateDiscarded() {
        // copy listeners to array to avoid ConcurrentModificationException
        ItemStateListener[] la = new ItemStateListener[listeners.size()];
        Iterator iter = listeners.values().iterator();
        int cnt = 0;
        while (iter.hasNext()) {
            la[cnt++] = (ItemStateListener) iter.next();
        }
        for (int i = 0; i < la.length; i++) {
            if (la[i] != null) {
                la[i].stateDiscarded(this);
            }
        }
    }

    /**
     * Notify the listeners that the persistent state this object is
     * representing has been created.
     */
    protected void notifyStateCreated() {
        // copy listeners to array to avoid ConcurrentModificationException
        ItemStateListener[] la = new ItemStateListener[listeners.size()];
        Iterator iter = listeners.values().iterator();
        int cnt = 0;
        while (iter.hasNext()) {
            la[cnt++] = (ItemStateListener) iter.next();
        }
        for (int i = 0; i < la.length; i++) {
            if (la[i] != null) {
                la[i].stateCreated(this);
            }
        }
    }

    /**
     * Notify the listeners that the persistent state this object is
     * representing has been changed.
     */
    protected void notifyStateModified() {
        // copy listeners to array to avoid ConcurrentModificationException
        ItemStateListener[] la = new ItemStateListener[listeners.size()];
        Iterator iter = listeners.values().iterator();
        int cnt = 0;
        while (iter.hasNext()) {
            la[cnt++] = (ItemStateListener) iter.next();
        }
        for (int i = 0; i < la.length; i++) {
            if (la[i] != null) {
                la[i].stateModified(this);
            }
        }
    }

    /**
     * Notify the listeners that the persistent state this object is
     * representing has been destroyed.
     */
    protected void notifyStateDestroyed() {
        // copy listeners to array to avoid ConcurrentModificationException
        ItemStateListener[] la = new ItemStateListener[listeners.size()];
        Iterator iter = listeners.values().iterator();
        int cnt = 0;
        while (iter.hasNext()) {
            la[cnt++] = (ItemStateListener) iter.next();
        }
        for (int i = 0; i < la.length; i++) {
            if (la[i] != null) {
                la[i].stateDestroyed(this);
            }
        }
    }

    //-------------------------------------------------------< public methods >
    /**
     * Determines if this item state represents a node.
     *
     * @return true if this item state represents a node, otherwise false.
     */
    public abstract boolean isNode();

    /**
     * Returns the identifier of this item state.
     *
     * @return the identifier of this item state..
     */
    public ItemId getId() {
        return id;
    }

    /**
     * Returns <code>true</code> if this item state represents new or modified
     * state (i.e. the result of copy-on-write) or <code>false</code> if it
     * represents existing, unmodified state.
     *
     * @return <code>true</code> if this item state is modified or new,
     *         otherwise <code>false</code>
     */
    public boolean isTransient() {
        return status != STATUS_EXISTING;

    }

    /**
     * Returns the UUID of the parent <code>NodeState</code> or <code>null</code>
     * if either this item state represents the root node or this item state is
     * 'free floating', i.e. not attached to the repository's hierarchy.
     *
     * @return the parent <code>NodeState</code>'s UUID
     */
    public String getParentUUID() {
        return parentUUID;
    }

    /**
     * Sets the UUID of the parent <code>NodeState</code>.
     *
     * @param parentUUID the parent <code>NodeState</code>'s UUID or <code>null</code>
     *                   if either this item state should represent the root node or this item state
     *                   should be 'free floating', i.e. detached from the repository's hierarchy.
     */
    public void setParentUUID(String parentUUID) {
        this.parentUUID = parentUUID;
    }

    /**
     * Returns the status of this item.
     *
     * @return the status of this item.
     */
    public int getStatus() {
        return status;
    }

    /**
     * Sets the new status of this item.
     *
     * @param newStatus the new status
     */
    public void setStatus(int newStatus) {
        switch (newStatus) {
            case ItemState.STATUS_NEW:
            case ItemState.STATUS_EXISTING:
            case ItemState.STATUS_EXISTING_REMOVED:
            case ItemState.STATUS_EXISTING_MODIFIED:
            case ItemState.STATUS_STALE_MODIFIED:
            case ItemState.STATUS_STALE_DESTROYED:
            case ItemState.STATUS_UNDEFINED:
                status = newStatus;
                return;
        }
        String msg = "illegal status: " + newStatus;
        log.error(msg);
        throw new IllegalArgumentException(msg);
    }

    /**
     * Discards this instance, i.e. renders it 'invalid'.
     */
    public void discard() {
        if (status != STATUS_UNDEFINED) {
            // notify listeners
            notifyStateDiscarded();
            // reset status
            status = STATUS_UNDEFINED;
        }
    }

    /**
     * Determines if this item state is overlying persistent state.
     *
     * @return <code>true</code> if this item state is overlying persistent
     *         state, otherwise <code>false</code>.
     */
    public boolean hasOverlayedState() {
        return overlayedState != null;
    }

    /**
     * Returns the persistent state backing <i>this</i> transient state or
     * <code>null</code> if there is no persistent state (i.e.. <i>this</i>
     * state is purely transient).
     *
     * @return the persistent item state or <code>null</code> if there is
     *         no persistent state.
     */
    public ItemState getOverlayedState() {
        return overlayedState;
    }

    /**
     * Returns the timestamp when this item state was last modified.
     *
     * @return the timestamp when this item state was last modified.
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Returns the id of the version this item state is based on.
     *
     * @return the id of the version this item state is based on.
     */
    public String getBaseVersionID() {
        return baseVersionID;
    }

    /**
     * Add an <code>ItemStateListener</code>
     *
     * @param listener the new listener to be informed on modifications
     */
    public void addListener(ItemStateListener listener) {
        if (!listeners.containsKey(listener)) {
            listeners.put(listener, listener);
        }
    }

    /**
     * Remove an <code>ItemStateListener</code>
     *
     * @param listener an existing listener
     */
    public void removeListener(ItemStateListener listener) {
        listeners.remove(listener);
    }

    //----------------------------------------------------< ItemStateListener >
    /**
     * @see ItemStateListener#stateCreated
     */
    public void stateCreated(ItemState created) {
    }

    /**
     * @see ItemStateListener#stateDestroyed
     */
    public void stateDestroyed(ItemState destroyed) {
        // underlying persistent state has been permanently destroyed
        status = STATUS_STALE_DESTROYED;
    }

    /**
     * @see ItemStateListener#stateModified
     */
    public void stateModified(ItemState modified) {
        // underlying state has been modified
        status = STATUS_STALE_MODIFIED;
    }

    /**
     * @see ItemStateListener#stateDiscarded
     */
    public void stateDiscarded(ItemState discarded) {
    }

    //-------------------------------------------------< Serializable support >
    private void writeObject(ObjectOutputStream out) throws IOException {
        // delegate to default implementation
        out.defaultWriteObject();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        // delegate to default implementation
        in.defaultReadObject();
    }
}
