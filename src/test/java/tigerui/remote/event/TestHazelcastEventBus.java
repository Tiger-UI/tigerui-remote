package tigerui.remote.event;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static tigerui.ThreadedTestHelper.EDT_TEST_HELPER;
import static tigerui.remote.HazelcastTestUtils.createLightHazelcastInstance;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.hazelcast.core.HazelcastInstance;

import rx.Observable;
import rx.schedulers.Schedulers;
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
		AtomicReference<Subscriber> subscriberRef = new AtomicReference<>();
		
		InOrder inOrder = inOrder(messageConsumer);
		
		EDT_TEST_HELPER.runTest(() -> {
			subscriberRef.set(eventBus.subscribe(ID, messageConsumer));
			eventBus.publish(ID, "I love tacos");
			eventBus.publish(ID, "burritos");
		});
		
		hazelcast.shutdown();
		
		waitForConditionOnEDT(subscriberRef.get()::isDisposed, 
				              isDisposed -> {
				            	  System.out.println("polling, disposed = " + isDisposed);
				            	  return isDisposed;
				              }, 
				              Duration.ofSeconds(10), 
				              Duration.ofMillis(100));
		
		inOrder.verify(messageConsumer).accept("I love tacos");
		inOrder.verify(messageConsumer).accept("burritos");
	}
	
	// TODO: move to ThreadedTestHelper
	public static <T> void waitForConditionOnEDT(Supplier<T> getter, 
			                                     Predicate<T> predicate,
			                                     Duration timeout,
			                                     Duration pollInterval) {
		Observable.interval(pollInterval.toMillis(), MILLISECONDS, Schedulers.from(SwingUtilities::invokeLater))
				  .map(tick -> getter.get())
				  .timeout(timeout.toMillis(), MILLISECONDS)
				  .takeUntil(predicate::test)
				  .toBlocking()
				  .last();
	}
}
