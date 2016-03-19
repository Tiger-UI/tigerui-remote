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
package tigerui.remote.property;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tigerui.ThreadedTestHelper.EDT_TEST_HELPER;
import static tigerui.ThreadedTestHelper.waitForConditionOnEDT;
import static tigerui.remote.HazelcastTestUtils.createLightHazelcastInstance;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import rx.Single;
import tigerui.Subscriber;
import tigerui.ThreadedTestHelper;
import tigerui.remote.HazelcastTestUtils;
import tigerui.remote.property.HazelcastPropertyService;
import tigerui.remote.property.PropertyId;
import tigerui.remote.property.RemoteProperty;
import tigerui.remote.property.SetFailedException;
import tigerui.subscription.Subscription;

public class TestHazelcastPropertyService {
    private static final PropertyId<String> ID = new PropertyId<>("23412342234");
    private HazelcastPropertyService service;
    private HazelcastInstance hazelcast;
    
    @Before
    public void setup() throws Exception {
    	hazelcast = createLightHazelcastInstance();
        service = ThreadedTestHelper.createOnEDT(() -> new HazelcastPropertyService(hazelcast));
        SwingUtilities.invokeAndWait(() -> service.registerProperty(ID, "tacos"));
    }
    
    @After
    public void tearDown() {
        hazelcast.shutdown();
    }
    
    @Test
    public void testGetValue() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> assertEquals("tacos", service.getValue(ID)));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testGetValueForUnknownPropertyThrows() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> service.getValue(new PropertyId<>("Random")));
    }
    
    @Test
    public void testRegisterProperty() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> {    		
    		PropertyId<String> id = new PropertyId<String>("property");
    		service.registerProperty(id, "burritos");
    		
    		assertEquals("burritos", service.getValue(id));
    	});
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testRegisterPropertyForSameIdThrows() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> service.registerProperty(ID, "burritos"));
    }
    
    @Test
    public void testSetValue() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> {    		
    		service.setValue(ID, "burritos");
    		assertEquals("burritos", service.getValue(ID));
    	});
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSetValueFailsForUnknownProperty() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> service.setValue(new PropertyId<String>("234235232352"), "hello"));
    }
    
    @Test
    public void testGetRegisteredProperties() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> {    		
    		Set<PropertyId<?>> actualRegisteredProperties = service.getRegisteredProperties();
    		Set<PropertyId<?>> expectedRegisteredProperties = new HashSet<>();
    		expectedRegisteredProperties.add(ID);
    		
    		assertEquals(expectedRegisteredProperties, actualRegisteredProperties);
    		
    		PropertyId<String> id = new PropertyId<String>("2342342342");
    		service.registerProperty(id, "burritos");
    		
    		expectedRegisteredProperties.add(id);
    		actualRegisteredProperties = service.getRegisteredProperties();
    		assertEquals(expectedRegisteredProperties, actualRegisteredProperties);
    	});
    }
    
    @Test
    public void testHasProperty() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> {    		
    		assertTrue(service.hasProperty(ID));
    		assertFalse(service.hasProperty(new PropertyId<String>("blass")));
    	});
    }
    
    @Test
    public void testRegisterListener() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> {
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
    	});
    }
    
    @Test
    public void testShutdownHazelcastDisposesSubscriber() throws Throwable {
    	AtomicReference<Subscriber> subscriberRef = new AtomicReference<>();
    	AtomicReference<Runnable> onDisposeActionRef = new AtomicReference<>();
    	
    	EDT_TEST_HELPER.runTest(() -> {
    		Consumer<String> consumer = Mockito.mock(Consumer.class);
    		Subscriber subscriber = service.registerListener(ID, consumer);
    		subscriberRef.set(subscriber);
    		
    		Runnable onDisposeAction = mock(Runnable.class);
    		onDisposeActionRef.set(onDisposeAction);
    		subscriber.doOnDispose(onDisposeAction);
    		
    		assertFalse(subscriber.isDisposed());
    		
    		hazelcast.shutdown();
    	});
    	
    	waitForConditionOnEDT(subscriberRef.get()::isDisposed, 
    			              isDisposed -> isDisposed, 
    			              Duration.ofSeconds(10), 
    			              Duration.ofMillis(100));
    	
    	EDT_TEST_HELPER.runTest(() -> verify(onDisposeActionRef.get()).run());
    }
    
    @Test
    public void testShutdownHazelcastDisposesRemoteProperty() throws Throwable {
    	AtomicReference<Subscription> subscriptionRef = new AtomicReference<>();
    	AtomicReference<Runnable> onDisposeActionRef = new AtomicReference<>();
    	
    	RemoteProperty<String> property = ThreadedTestHelper.createOnEDT(() -> service.getProperty(ID));
    	
        EDT_TEST_HELPER.runTest(() -> {
            Runnable onDisposedAction = mock(Runnable.class);
            onDisposeActionRef.set(onDisposedAction);
            
            subscriptionRef.set(property.onDisposed(onDisposedAction));
            
            hazelcast.shutdown();
        });
        
        waitForConditionOnEDT(subscriptionRef.get()::isDisposed, 
	              			  isDisposed -> isDisposed, 
	              			  Duration.ofSeconds(10), 
	              			  Duration.ofMillis(100));
        
        verify(onDisposeActionRef.get()).run();
        EDT_TEST_HELPER.runTest(() -> assertEquals("tacos", property.get()));
    }
    
    @Test
    public void testLockUnlock() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> {    		
    		assertFalse(service.isLocked(ID));
    		
    		// lock once
    		service.lock(ID);
    		assertTrue(service.isLocked(ID));
    		
    		// lock twice
    		service.lock(ID);
    		assertTrue(service.isLocked(ID));
    		
    		// unlocking once should not release the lock
    		service.unlock(ID);
    		assertTrue(service.isLocked(ID));
    		
    		// unlocking a second time should release the lock
    		service.unlock(ID);
    		assertFalse(service.isLocked(ID));
    	});
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCallLockForNonExistentPropertyThrows() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> service.lock(new PropertyId<>("skljdlkajsd")));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCallUnLockForNonExistentPropertyThrows() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> service.unlock(new PropertyId<>("skljdlkajsd")));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCallIsLockedForNonExistentPropertyThrows() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> service.isLocked(new PropertyId<>("skljdlkajsd")));
    }
    
    @Test
	public void testSetValueAsync() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> {    		
    		Single<Void> result = service.setValueAsync(ID, "burritos", "tacos");
    		
    		result.toBlocking().value();
    		
    		assertEquals("burritos", service.getValue(ID));
    	});
	}
    
    @Test
	public void testSetValueAsyncFails() throws Throwable {
    	EDT_TEST_HELPER.runTest(() -> {    		
    		Single<Void> result = service.setValueAsync(ID, "burritos", "fajitas");
    		
    		try {			
    			result.toBlocking().value();
    			fail("The setValueAsync call should have failed.");
    		} catch ( RuntimeException exception ) {
    			Throwable throwable = exception.getCause();
    			
    			assertEquals(SetFailedException.class, throwable.getClass());
    			
    			SetFailedException setFailedException = (SetFailedException) throwable;
    			
    			assertEquals("tacos", setFailedException.getActualRemoteValue());
    			assertEquals("fajitas", setFailedException.getExpectedRemoteValue());
    			assertEquals("burritos", setFailedException.getNewRemoteValue());
    		}
    	});
	}
}
