package tigerui.remote.event;

import java.util.Optional;

/**
 * A property catalog holds a list of all the properties that are available from some property service.
 */
public interface TopicCatalog {
	
	/**
	 * Retrieves a property id for a given property uuid.
	 * 
	 * @param uuid
	 *            some uuid for a property id to retrieve
	 * @return an property id if it is defined for the provided uuid or
	 *         {@link Optional#empty()} is the provided uuid does not map to a
	 *         defined property id.
	 * @param <T> the type of the property id
	 */
	Optional<TopicId<?>> getTopicId(String uuid);
}
