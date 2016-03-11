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

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tigerui.ThreadedTestHelper.EDT_TEST_HELPER;
import static tigerui.ThreadedTestHelper.createOnEDT;
import static tigerui.ThreadedTestHelper.waitForConditionOnEDT;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import tigerui.Subscriber;
import tigerui.subscription.Subscription;

public class TestHazelcastPropertyService {
    private static final PropertyId<String> ID = new PropertyId<>("23412342234");
    private HazelcastPropertyService service;
    private HazelcastInstance hazelcast;
    
    @Before
    public void setup() {
        Config config = new Config();
        
        JoinConfig joinConfig = config.getNetworkConfig().getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(false);
        joinConfig.getAwsConfig().setEnabled(false);
        
        hazelcast = Hazelcast.newHazelcastInstance(config);
        service = new HazelcastPropertyService(hazelcast);
        service.registerProperty(ID, "tacos");
    }
    
    @After
    public void tearDown() {
        hazelcast.shutdown();
    }
    
    @Test
    public void testGetValue() {
        assertEquals("tacos", service.getValue(ID));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGetValueForUnknownPropertyThrows() {
        service.getValue(new PropertyId<>("Random"));
    }
    
    @Test
    public void testRegisterProperty() {
        PropertyId<String> id = new PropertyId<String>("property");
        service.registerProperty(id, "burritos");
        
        assertEquals("burritos", service.getValue(id));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRegisterPropertyForSameIdThrows() {
        service.registerProperty(ID, "burritos");
    }
    
    @Test
    public void testSetValue() {
        service.setValue(ID, "burritos");
        
        assertEquals("burritos", service.getValue(ID));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSetValueFailsForUnknownProperty() {
        service.setValue(new PropertyId<String>("234235232352"), "hello");
    }
    
    @Test
    public void testGetRegisteredProperties() {
        Set<PropertyId<?>> actualRegisteredProperties = service.getRegisteredProperties();
        Set<PropertyId<?>> expectedRegisteredProperties = new HashSet<>();
        expectedRegisteredProperties.add(ID);
        
        assertEquals(expectedRegisteredProperties, actualRegisteredProperties);
        
        PropertyId<String> id = new PropertyId<String>("2342342342");
        service.registerProperty(id, "burritos");
        
        expectedRegisteredProperties.add(id);
        actualRegisteredProperties = service.getRegisteredProperties();
        assertEquals(expectedRegisteredProperties, actualRegisteredProperties);
    }
    
    @Test
    public void testHasProperty() {
        assertTrue(service.hasProperty(ID));
        assertFalse(service.hasProperty(new PropertyId<String>("blass")));
    }
    
    @Test
    public void testRegisterListener() throws InterruptedException {
        Consumer<String> consumer = Mockito.mock(Consumer.class);
        Subscription subscription = service.registerListener(ID, consumer);
        
        // needed since Hazelcast is multithreaded
        CountDownLatch latch = new CountDownLatch(1);
        service.registerListener(ID, value -> latch.countDown());
        
        assertFalse(subscription.isDisposed());
        
        IMap<Object, Object> properties = hazelcast.getMap("Property");
        properties.set(ID.getUuid(), "burritos");
        
        latch.await();
        
        verify(consumer).accept("burritos");
        
        subscription.dispose();
        properties.set(ID.getUuid(), "fajitas");
        verifyNoMoreInteractions(consumer);
    }
    
    @Test
    public void testGetProperty() throws Throwable {
        Consumer<String> onChanged = mock(Consumer.class);
        Runnable onDisposed = mock(Runnable.class);
        
        RemoteProperty<String> remoteProperty = createOnEDT(() -> service.getProperty(ID));

        EDT_TEST_HELPER.runTest(() -> {            
            assertEquals("tacos", remoteProperty.get());
            
            remoteProperty.observe(onChanged, onDisposed);
            
            verify(onChanged).accept("tacos");
            verifyNoMoreInteractions(onDisposed);
            
            IMap<Object, Object> properties = hazelcast.getMap("Property");
            properties.set(ID.getUuid(), "burritos");
            
            assertEquals(ID, remoteProperty.getId());
        });
        
        waitForConditionOnEDT(remoteProperty::get, value -> value.equals("burritos"), ofSeconds(10), ofMillis(100));
        
        EDT_TEST_HELPER.runTest(() -> {
            verify(onChanged).accept("burritos");
            
            Consumer<Integer> onChanged2 = mock(Consumer.class);
            remoteProperty.map(String::length).take(1).onChanged(onChanged2);
            
            verify(onChanged2).accept(8);
            
            remoteProperty.dispose();
            verify(onDisposed).run();
            
            Runnable onDisposedAction = Mockito.mock(Runnable.class);
            remoteProperty.onDisposed(onDisposedAction);
            verify(onDisposedAction).run();
        });
    }
    
    @Test
    public void testShutdownHazelcastDisposesSubscriber() {
        Consumer<String> consumer = Mockito.mock(Consumer.class);
        Subscriber subscriber = service.registerListener(ID, consumer);
        Runnable onDiposeAction = mock(Runnable.class);
        subscriber.doOnDispose(onDiposeAction);
        
        assertFalse(subscriber.isDisposed());
        
        hazelcast.shutdown();
        
        assertTrue(subscriber.isDisposed());
        verify(onDiposeAction).run();
    }
    
    @Test
    public void testShutdownHazelcastDisposesRemoteProperty() throws Throwable {
        EDT_TEST_HELPER.runTest(() -> {
            RemoteProperty<String> property = service.getProperty(ID);
            Runnable onDisposedAction = mock(Runnable.class);
            property.onDisposed(onDisposedAction);
            
            hazelcast.shutdown();
            
            verify(onDisposedAction).run();
            assertEquals("tacos", property.get());
        });
    }
}
