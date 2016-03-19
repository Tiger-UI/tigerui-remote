package tigerui.remote;

import tigerui.property.PropertyStream;

/**
 * Monitors the lifecycle of some service.
 */
public interface ServiceLifecycleMonitor {
	
	enum State { STOPPED, RUNNING }
	
	/**
	 * Checks if the underlying service is running.
	 * @return true if the service is running, false otherwise
	 */
	boolean isRunning();
	
	/**
	 * @return a {@link PropertyStream} for the service state.
	 */
	PropertyStream<State> getState();
}
