package tigerui.remote.event;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static tigerui.ThreadedTestHelper.EDT_TEST_HELPER;
import static tigerui.remote.HazelcastTestUtils.createLightHazelcastInstance;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.hazelcast.core.HazelcastInstance;

import tigerui.Subscriber;
import tigerui.ThreadedTestHelper;

public class TestHazelcastEventBus {
	private static final TopicId<String> ID  = new TopicId<>();
	
	private HazelcastEventBus eventBus;

	private HazelcastInstance hazelcast;
	
	@Before
	public void setup() throws Exception {
		hazelcast = createLightHazelcastInstance();
		eventBus = ThreadedTestHelper.createOnEDT(() -> new HazelcastEventBus(hazelcast));
	}
	
	@Test
	public void testRegisterOnTopic() throws Throwable {
		Consumer<String> messageConsumer = mock(Consumer.class);
		Consumer<String> messageConsumerLatch = mock(Consumer.class);
		AtomicReference<Subscriber> subscriberRef = new AtomicReference<>();
		
		InOrder inOrder = inOrder(messageConsumer);
		
		EDT_TEST_HELPER.runTest(() -> {
			subscriberRef.set(eventBus.subscribe(ID, messageConsumer));
			eventBus.subscribe(ID, messageConsumerLatch);
			eventBus.publish(ID, "I love tacos");
			eventBus.publish(ID, "burritos");
		});
		
		// only used to wait for the callbacks to be finished
		Mockito.verify(messageConsumerLatch, Mockito.timeout(1000)).accept("burritos");
		
		inOrder.verify(messageConsumer).accept("I love tacos");
		inOrder.verify(messageConsumer).accept("burritos");
	}
}
