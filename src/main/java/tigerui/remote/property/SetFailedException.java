package tigerui.remote.property;

/**
 * Indicates that an attempt to set the value of a remote property failed.
 */
public class SetFailedException extends RemotePropertyException {

	private final Object newRemoteValue;
	private final Object expectedRemoteValue;
	private final Object actualRemoteValue;

	public <T> SetFailedException(T newRemoteValue, T expectedRemoteValue, T actualRemoteValue) {
		super("Failed to set the remote value to [" + newRemoteValue + "] because the actual remote value ["
				+ actualRemoteValue + "] was different than expected [" + expectedRemoteValue + "]");
		this.newRemoteValue = newRemoteValue;
		this.expectedRemoteValue = expectedRemoteValue;
		this.actualRemoteValue = actualRemoteValue;
	}

	public Object getNewRemoteValue() {
		return newRemoteValue;
	}

	public Object getExpectedRemoteValue() {
		return expectedRemoteValue;
	}

	public Object getActualRemoteValue() {
		return actualRemoteValue;
	}
}
