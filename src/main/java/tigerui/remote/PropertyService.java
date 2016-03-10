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
}
