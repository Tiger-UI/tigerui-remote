package tigerui.remote.event;

import java.util.function.Consumer;

import tigerui.Subscriber;

/**
 * The event bus adds classical pub/sub capability between distributed actors.
 */
public interface EventBus {
	
	/**
	 * Publishes a message to the provided topic id.
	 * 
	 * @param id
	 *            the id of the topic to publish the message on.
	 * @param message
	 *            some message to publish on the provided topic.
	 */
	public <T> void publish(TopicId<T> id, T message);

	/**
	 * Subscribes to messages for the provided topic.
	 * 
	 * @param id
	 *            some topic to subscribe to.
	 * @return a {@link Subscriber} that can be used to stop consuming messages on the
	 *         provided topic.
	 */
    public <T> Subscriber subscribe(TopicId<T> id, Consumer<T> messageConsumer);
}
