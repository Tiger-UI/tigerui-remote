package tigerui.remote;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.junit.Assert.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tigerui.ThreadedTestHelper.EDT_TEST_HELPER;
import static tigerui.ThreadedTestHelper.awaitLatch;
import static tigerui.ThreadedTestHelper.createOnEDT;
import static tigerui.ThreadedTestHelper.waitForConditionOnEDT;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import rx.Single;
import tigerui.ThreadedTestHelper;

public class TestRemoteProperty {
	
	private static final PropertyId<String> ID = new PropertyId<>("23412342234");
	
    private HazelcastPropertyService service;
    private HazelcastInstance hazelcast;
    private RemoteProperty<String> remoteProperty;
    
    @Before
    public void setup() throws Exception {
        Config config = new Config();
        
        JoinConfig joinConfig = config.getNetworkConfig().getJoin();
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(false);
        joinConfig.getAwsConfig().setEnabled(false);
        
        hazelcast = Hazelcast.newHazelcastInstance(config);
        service = new HazelcastPropertyService(hazelcast);
        service.registerProperty(ID, "tacos");
        remoteProperty = createOnEDT(() -> service.getProperty(ID));
    }
    
    @After
    public void tearDown() {
        hazelcast.shutdown();
    }
	
	@Test
    public void testPropertyDetectsRemoteChanges() throws Throwable {
        Consumer<String> onChanged = mock(Consumer.class);
        Runnable onDisposed = mock(Runnable.class);
        
        EDT_TEST_HELPER.runTest(() -> {            
            assertEquals("tacos", remoteProperty.get());
            
            remoteProperty.observe(onChanged, onDisposed);
            
            verify(onChanged).accept("tacos");
            verifyNoMoreInteractions(onDisposed);
            
            IMap<Object, Object> properties = hazelcast.getMap("Property");
            properties.set(ID.getUuid(), "burritos");
            
            assertEquals(ID, remoteProperty.getId());
        });
        
        // need to do this since the property service is threaded.
        waitForConditionOnEDT(remoteProperty::get, value -> value.equals("burritos"), ofSeconds(10), ofMillis(100));
        
        verify(onChanged).accept("burritos");
    }
	
	@Test
	public void testMap() throws Throwable {
		EDT_TEST_HELPER.runTest(() -> {			
			Consumer<Integer> onChanged = mock(Consumer.class);
			remoteProperty.map(String::length).take(1).onChanged(onChanged);
			
			verify(onChanged).accept(5);
		});
	}
	
	@Test
	public void testDispose() throws Throwable {
		EDT_TEST_HELPER.runTest(() -> {
			Runnable onDisposed = mock(Runnable.class);
			remoteProperty.onDisposed(onDisposed);
			
	        remoteProperty.dispose();
	        verify(onDisposed).run();
	        
	        Runnable onDisposedAction = Mockito.mock(Runnable.class);
	        remoteProperty.onDisposed(onDisposedAction);
	        verify(onDisposedAction).run();
		});
	}
	
	@Test
	public void testSetValue() throws Throwable {
		Consumer<String> onChanged = mock(Consumer.class);

		EDT_TEST_HELPER.runTest(() -> {
			remoteProperty.onChanged(onChanged);
			remoteProperty.setValue("burritos");
		});
		
		waitForConditionOnEDT(remoteProperty::get, "burritos"::equals, ofSeconds(5), ofMillis(50));
		
		EDT_TEST_HELPER.runTest(() -> verify(onChanged).accept("burritos"));
	}
	
	@Test
	public void testCannotUpdateSetReentrantly() throws Throwable {
		Consumer<String> onChanged = mock(Consumer.class);
		
		InOrder inOrder = inOrder(onChanged);

		EDT_TEST_HELPER.runTest(() -> {
			remoteProperty.onChanged(onChanged);
			
			// add a reentrant on change observer
			remoteProperty.onChanged(value -> {
				try{ remoteProperty.setValue("fajitas"); } catch( Exception e ) {} 
			});
			
			remoteProperty.setValue("burritos");
		});
		
		waitForConditionOnEDT(remoteProperty::get, "burritos"::equals, ofSeconds(5), ofMillis(50));
		
		EDT_TEST_HELPER.runTest(() -> {
			inOrder.verify(onChanged).accept("tacos");
			inOrder.verify(onChanged).accept("burritos");
			inOrder.verifyNoMoreInteractions();
		});
	}
	
	@Test
	public void testUpdateIgnoredIfAlreadyDisposed() throws Throwable {
		EDT_TEST_HELPER.runTest(() -> {
			remoteProperty.dispose();
			remoteProperty.setValue("burritos");
			assertEquals("tacos", service.getValue(ID));
		});
	}
	
	@Test
	public void testSetValueIgnoredIfValueIsTheSameAsCurrentValue() throws Throwable {
		EDT_TEST_HELPER.runTest(() -> {
			Consumer<String> onChanged = mock(Consumer.class);
			remoteProperty.onChanged(onChanged);
			remoteProperty.setValue("tacos");
			verify(onChanged).accept("tacos");
		});
	}
	
	@Test(expected = RemotePropertyException.class)
	public void testSetValueThrowsIfValueChangedWhileSetting() throws Throwable {
		EDT_TEST_HELPER.runTest(() -> {
			service.setValue(ID, "burritos");
			remoteProperty.setValue("fajitas");
		});
	}
	
	@Test
	public void testReplaceValueSucceedsEvenIfRemotePropertyChanged() throws Throwable {
		Consumer<String> onChanged = mock(Consumer.class);
		
		EDT_TEST_HELPER.runTest(() -> {
			service.setValue(ID, "fajiats");
			remoteProperty.onChanged(onChanged);
			remoteProperty.replaceValue("burritos");
		});
		
		waitForConditionOnEDT(remoteProperty::get, "burritos"::equals, ofSeconds(5), ofMillis(50));
		
		EDT_TEST_HELPER.runTest(() -> verify(onChanged).accept("burritos"));
	}
	
	@Test
	public void testSetValueAsync() throws Throwable {
		Single<Void> setResult = createOnEDT(() -> remoteProperty.setValueAsync("burritos"));

		// wait for the set to finish
		setResult.toBlocking().value();
		
		EDT_TEST_HELPER.runTest(() -> assertEquals("burritos", remoteProperty.get()));
	}
	
	@Test(expected = SetFailedException.class)
	public void testSetValueAsyncThrows() throws Throwable {
		CountDownLatch waitForSet = new CountDownLatch(1);
		CountDownLatch waitForSetAsync = new CountDownLatch(1);
		
		Single<Void> setResult = createOnEDT(() -> {
			assertTrue(awaitLatch(waitForSet));
			try {				
				return remoteProperty.setValueAsync("burritos");
			} finally {
				waitForSetAsync.countDown();
			}
		});
		
		service.setValue(ID, "fajitas");
		waitForSet.countDown();
		waitForSetAsync.await();

		// wait for the set to finish
		try {			
			setResult.toBlocking().value();
			fail();
		} catch ( RuntimeException exception ) {
			throw exception.getCause();
		}
	}

	private void waitForLatch(CountDownLatch waitForSet) {
		try {
			waitForSet.await();
		} catch (Exception e) {
			fail();
		}
	}
	
	@Test
	public void testIsLocked() throws Throwable {
		EDT_TEST_HELPER.runTest(() -> assertFalse(remoteProperty.isLocked()));
		
		EDT_TEST_HELPER.runTest(() -> {
			remoteProperty.lock();
			assertTrue(remoteProperty.isLocked());
		});
		
		EDT_TEST_HELPER.runTest(() -> {
			remoteProperty.unlock();
			assertFalse(remoteProperty.isLocked());
		});
		
		service.lock(ID);
		
		EDT_TEST_HELPER.runTest(() ->assertTrue(remoteProperty.isLocked()));
	}
}
