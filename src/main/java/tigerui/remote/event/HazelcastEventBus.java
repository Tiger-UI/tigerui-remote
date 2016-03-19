package tigerui.remote.event;

import static java.util.Objects.requireNonNull;
import static tigerui.Preconditions.checkState;
import static tigerui.remote.ServiceLifecycleMonitor.State.STOPPED;

import java.util.function.Consumer;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ITopic;

import tigerui.Subscriber;
import tigerui.remote.HazelcastServiceLifecycleMonitor;
import tigerui.subscription.CompositeSubscription;

/**
 * An implementation of an {@link EventBus} that uses Hazelcast.
 */
public class HazelcastEventBus implements EventBus {
	
	private final HazelcastInstance hazelcast;
	private final HazelcastServiceLifecycleMonitor lifecycleMonitor;
	private final CompositeSubscription subscriptions;
	
	public HazelcastEventBus(HazelcastInstance hazelcast) {
		this.hazelcast = requireNonNull(hazelcast);
		this.lifecycleMonitor = new HazelcastServiceLifecycleMonitor(hazelcast);
		this.subscriptions = new CompositeSubscription();
		
        lifecycleMonitor.getState().is(STOPPED).then(subscriptions::dispose);
	}

	@Override
	public <T> void publish(TopicId<T> id, T message) {
		checkHazelcastIsRunning();
		
		hazelcast.getTopic(id.getUuid()).publish(message);
	}

	@Override
	public <T> Subscriber subscribe(TopicId<T> id, Consumer<T> messageConsumer) {
        checkHazelcastIsRunning();
        
        ITopic<Object> topic = hazelcast.getTopic(id.getUuid());
        
		String registrationId = 
        		topic.addMessageListener(message -> messageConsumer.accept((T) message.getMessageObject()));
        
        Subscriber subscriber = new Subscriber();
        subscriptions.add(subscriber);
        subscriber.doOnDispose(() -> topic.removeMessageListener(registrationId));
        subscriber.doOnDispose(() -> subscriptions.remove(subscriber));
        
        return subscriber;
	}

    private void checkHazelcastIsRunning() {
        checkState(lifecycleMonitor.isRunning(), "Hazelcast is not running");
    }
}
