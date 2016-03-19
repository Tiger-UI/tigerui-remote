package tigerui.remote;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleEvent.LifecycleState;
import com.hazelcast.core.LifecycleService;

import tigerui.EventLoop;
import tigerui.disposables.Disposable;
import tigerui.property.Property;
import tigerui.property.PropertyStream;

public class HazelcastServiceLifecycleMonitor implements ServiceLifecycleMonitor, Disposable {
	
	private final Property<State> state; 

	public HazelcastServiceLifecycleMonitor(HazelcastInstance hazelcast) {
        state = Property.create(getCurrentState(hazelcast));
        addLifecycleListener(hazelcast, state);
	}

	@Override
	public void dispose() {
		state.dispose();
	}

	@Override
	public boolean isRunning() {
		return state.get() == State.RUNNING;
	}

	@Override
	public PropertyStream<State> getState() {
		return state;
	}
	
	private static State getCurrentState(HazelcastInstance hazelcast) {
		if(hazelcast.getLifecycleService().isRunning())
			return State.RUNNING;
		
		return State.STOPPED;
	}
    
    private static void addLifecycleListener(HazelcastInstance hazelcast, Property<State> state) {
        LifecycleService lifecycleService = hazelcast.getLifecycleService();
        EventLoop eventLoop = EventLoop.createEventLoop();
        
		String listenerId = lifecycleService.addLifecycleListener(event -> {
			if (event.getState() == LifecycleState.SHUTDOWN) {
				eventLoop.invokeLater(() -> state.setValue(State.STOPPED));
			}
		});
		
		state.onDisposed(() -> lifecycleService.removeLifecycleListener(listenerId));
    }
}
