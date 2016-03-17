/**
 * Copyright 2015 Mike Baum
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package tigerui.remote;

import java.util.Set;
import java.util.function.Consumer;

import rx.Single;
import tigerui.Subscriber;
import tigerui.subscription.Subscription;

/**
 * A property service can be used to interact with remote properties. To create
 * a new remote property call {@link #registerProperty(PropertyId, Object)}. To
 * get a remote property call {@link #getProperty(PropertyId)}.
 */
public interface PropertyService {
    
    /**
     * Gets the current value of the remote property with provided id.
     * 
     * @param id
     *            some id of a remote property
     * @return the current value of this remote property.
     */
    <T> T getValue(PropertyId<T> id);
    
    /**
     * Sets the value of a property in the remote
     * 
     * @param id
     *            some id of a remote property
     * @param value
     *            the value to set for the remote property
     */
    <T> void setValue(PropertyId<T> id, T value);
    
	/**
	 * Replaces the value of a remote property with the provided id to the
	 * provided value.
	 * 
	 * @param id
	 *            some id of a remote property
	 * @param value
	 *            the value to set for the remote property
	 */
    <T> T replaceValue(PropertyId<T> id, T value);
    
	/**
	 * Sets the value of some remote property asynchronously. If the current
	 * value changes before the set is attempted, the set will be aborted and
	 * the returned {@link Single} is failed.
	 * 
	 * @param id
	 *            the id of the remote property to set.
	 * @param value
	 *            the new value for the remote property
	 * @param expectedRemoteValue
	 *            the expected value of the remote property.
	 * @return a {@link Single} that will be completed with an error
	 *         or success when the set operation is finished
	 */
    <T> Single<Void> setValueAsync(PropertyId<T> id, T value, T expectedRemoteValue);
    
    /**
     * Registers the provided property in the remote.
     * 
     * @param property some property to register.
     */
    <T> void registerProperty(PropertyId<T> id, T initialValue);
    
    /**
     * Retrieves a set of all the known properties.
     * 
     * @return a {@link Set} of the known properties
     */
    Set<PropertyId<?>> getRegisteredProperties();
    
    /**
     * Checks if a property exists for the provided Id.
     * 
     * @param id
     *            some property id to check if it exists.
     * @return boolean true if the property exists, false otherwise
     */
    <T> boolean hasProperty(PropertyId<T> id);
    
    /**
     * Registers a listener for the provided remoteProperty
     * 
     * @param id
     *            some property id to add a listener to
     * @param listener
     *            some listener to respond to value updates.
     * @return a {@link Subscription}
     */
    <T> Subscriber registerListener(PropertyId<T> id, Consumer<T> listener);
    
    /**
     * Connects to a remote property with the provided id and retrieves RemoteProperty.
     * 
     * @param id
     *            some property id to listen on.
     * @return a {@link RemoteProperty} that is connected to a remote property
     *         with the provided id.
     */
    <T> RemoteProperty<T> getProperty(PropertyId<T> id);

    /**
     * Attempts to acquire a lock on the remote property with the provided id.
     * This call will block until the lock is acquired. Locks are reentrant, so
     * if you lock this property N times you must unlock it N times for another
     * process to acquire the lock to this remote property.
     * 
     * @param id
     *            the id of the property to lock
     */
    void lock(PropertyId<?> id);

	/**
	 * Decrements the lock count on this property. If the lock count becomes 0,
	 * then this remote property can be locked by some other process.
	 * 
	 * @param id
	 *            the id of the remote property to unlock
	 * 
	 * @throws IllegalMonitorStateException
	 *             if called from a thread other than the one that acquired the
	 *             lock
	 */
    void unlock(PropertyId<?> id);

	/**
	 * Checks if the remote property with the provided id is locked. If this is
	 * a reentrant call the remote property could be locked by the current
	 * thread, so a return value of true does not mean you cannot acquire the
	 * lock.
	 * 
	 * @param id
	 *            the id of the property to check if it is locked.
	 * @return true if the property is locked, false otherwise.
	 * 
	 * TODO: Reconsider if this is needed. The problem is that isLocked
	 *       will return true regardless of who owns the lock. On top of
	 *       that, if this returns false, then it's entirely possible that
	 *       some other process acquires the lock immediately after calling
	 *       this method. So the information is not terribly useful. It
	 *       would be better if the server would notify the remote properties
	 *       when the lock state changed.
	 */
    boolean isLocked(PropertyId<?> id);
}
