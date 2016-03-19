package tigerui.remote.event;

import java.util.UUID;

/**
 * A globally unique identifier for a topic, used by topic streams.
 *
 * @param <T>
 *            the type of data for the topic stream this id identifies
 */
public final class TopicId<T> {
	private final String uuid;
    
    public TopicId(String uuid) {
        this.uuid = uuid;
    }
    
    /**
     * Creates a topic id with random UUID.
     */
    TopicId() {
        this(UUID.randomUUID().toString());
    }
    
    public String getUuid() {
        return uuid;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TopicId<?> other = (TopicId<?>) obj;
        if (uuid == null) {
            if (other.uuid != null)
                return false;
        } else if (!uuid.equals(other.uuid))
            return false;
        return true;
    }
}
