package tigerui.remote;

/**
 * Exceptions that can be thrown when interacting with remote properties.
 */
public class RemotePropertyException extends Exception {
	private static final long serialVersionUID = 7488392215397242412L;

	public RemotePropertyException() {
		super();
	}

	public RemotePropertyException(String message, Throwable cause) {
		super(message, cause);
	}

	public RemotePropertyException(String message) {
		super(message);
	}

	public RemotePropertyException(Throwable cause) {
		super(cause);
	}
}
