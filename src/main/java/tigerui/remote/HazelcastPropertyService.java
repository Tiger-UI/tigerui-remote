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
import static tigerui.Preconditions.checkArgument;
import static tigerui.Preconditions.checkState;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.hazelcast.core.HazelcastException;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.LifecycleEvent.LifecycleState;
import com.hazelcast.core.LifecycleService;
import com.hazelcast.map.listener.EntryUpdatedListener;

import tigerui.Subscriber;
import tigerui.subscription.CompositeSubscription;

/**
 * A property service that is backed by Hazelcast.
 * 
 * TODO: what about locking and checked exceptions. Right now Hazelcast throws
 * instances of {@link HazelcastException} which are not checked exceptions.
 */
public class HazelcastPropertyService implements PropertyService {

    private static final String PROPERTY_SERVICE_MAP_NAME = "Property";
    
    private final IMap<String, Object> propertyMap;
    private final CompositeSubscription subscriptions;
    
    private volatile boolean isRunning;

    public HazelcastPropertyService(HazelcastInstance hazelcast) {
        this.propertyMap = requireNonNull(hazelcast.getMap(PROPERTY_SERVICE_MAP_NAME));
        isRunning = hazelcast.getLifecycleService().isRunning();
        subscriptions = new CompositeSubscription();
        addLifecycleListener(hazelcast);
    }

    @Override
    public <T> T getValue(PropertyId<T> id) {
        checkPropertyExists(id);
        checkHazelcastIsRunning();
        
        @SuppressWarnings("unchecked") // this should not throw, since the property id guarantees the type
        T value = (T) propertyMap.get(id.getUuid());
        
        return value;
    }

    @Override
    public <T> void setValue(PropertyId<T> id, T value) {
        checkPropertyExists(id);
        checkHazelcastIsRunning();
        
        propertyMap.set(id.getUuid(), value);
    }

    @Override
    public <T> void registerProperty(PropertyId<T> id, T initialValue) {
        checkPropertyDoesNotExists(id);
        checkHazelcastIsRunning();
        
        propertyMap.put(id.getUuid(), initialValue);
    }

    @Override
    public Set<PropertyId<?>> getRegisteredProperties() {
        checkHazelcastIsRunning();
        
        return propertyMap.keySet().stream().map(PropertyId::new).collect(Collectors.toSet());
    }
    
    @Override
    public <T> boolean hasProperty(PropertyId<T> id) {
        checkHazelcastIsRunning();
        
        return propertyMap.containsKey(id.getUuid());
    }

    @Override
    public <T> Subscriber registerListener(PropertyId<T> id, Consumer<T> listener) {
        checkPropertyExists(id);
        checkHazelcastIsRunning();
        
        String registrationId = propertyMap.addEntryListener(createEntryUpdatedListener(listener), id.getUuid(), true);
        
        Subscriber subscriber = new Subscriber();
        subscriptions.add(subscriber);
        subscriber.doOnDispose(() -> propertyMap.removeEntryListener(registrationId));
        subscriber.doOnDispose(() -> subscriptions.remove(subscriber));
        
        return subscriber;
    }

    @Override
    public <T> RemoteProperty<T> getProperty(PropertyId<T> id) {
        checkHazelcastIsRunning();
        
        return RemoteProperty.createRemoteProperty(this, id);
    }

    private <T> void checkPropertyExists(PropertyId<T> id) {
        checkArgument(hasProperty(id), "A property with the id: [" + id + "] does not exist.");
    }
    
    private <T> void checkPropertyDoesNotExists(PropertyId<T> id) {
        checkArgument(!hasProperty(id), "A property with the id: [" + id + "] already exists.");
    }

    private void checkHazelcastIsRunning() {
        checkState(isRunning, "Hazelcast is not running");
    }
    
    private <T> EntryUpdatedListener<String, T> createEntryUpdatedListener(Consumer<T> listener) {
        return event -> listener.accept(event.getValue());
    }
    
    private void addLifecycleListener(HazelcastInstance hazelcast) {
        LifecycleService lifecycleService = hazelcast.getLifecycleService();
        
        lifecycleService.addLifecycleListener(event -> {
            if (event.getState() == LifecycleState.SHUTDOWN) {
                isRunning = false;
                subscriptions.dispose();
            }
        });
    }
}
