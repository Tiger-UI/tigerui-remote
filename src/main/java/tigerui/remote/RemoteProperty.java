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

import static java.util.Objects.requireNonNull;

import tigerui.disposables.Disposable;
import tigerui.property.PropertyStream;

/**
 * A {@link RemoteProperty} represents a property that exists on some machine or
 * process. Typically a remote property would be defined and exists on a server.
 * 
 * <p>
 * <b>NOTE:</b> The data that this property holds will need to be serialized and
 * sent over the wire. Refer to the {@link PropertyService} that is being used
 * to determine what needs to be done to ensure the data can be marshalled
 * without error.
 * 
 * <p>
 * TODO: a method to mutate the remote property must be added. I have not yet
 * decided on the signature of that method yet. It will come soon. There may
 * also be a need to add methods to lock and unlock a property.
 * 
 * @param <M>
 *            the type of data for the remote property
 */
public class RemoteProperty<M> extends PropertyStream<M> implements Disposable {

    private final RemotePropertyPublisher<M> propertyPublisher;

    private RemoteProperty(RemotePropertyPublisher<M> propertyPublisher) {
        super(propertyPublisher);
        this.propertyPublisher = requireNonNull(propertyPublisher);
    }
    
    public static <M> RemoteProperty<M> createRemoteProperty(PropertyService propertyService, PropertyId<M> id) {
        return new RemoteProperty<>(new RemotePropertyPublisher<>(propertyService, id));
    } 

    @Override
    public void dispose() {
        propertyPublisher.dispose();
    }
    
    public PropertyId<M> getId() {
        return propertyPublisher.getId();
    }
}
