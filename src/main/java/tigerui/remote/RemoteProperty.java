/**
 * Copyright 2015 Mike Baum
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package tigerui.remote;

import static java.util.Objects.requireNonNull;
import static tigerui.dispatcher.Dispatchers.checkCanDispatch;

import java.util.Objects;

import rx.Single;
import rx.schedulers.Schedulers;
import tigerui.EventLoop;
import tigerui.dispatcher.Dispatcher;
import tigerui.dispatcher.PropertyDispatcher;
import tigerui.disposables.Disposable;
import tigerui.property.PropertyStream;

/**
 * A {@link RemoteProperty} represents a property that exists on some machine or
 * process. Typically a remote property would be defined and exists on a server.
 * 
 * <p>
 * <b>NOTE:</b> The data that this property holds will need to be serialized and
 * sent over the wire. Refer to the {@link PropertyService} that is being used
 * to determine what needs to be done to ensure the data can be marshalled
 * without error.
 * 
 * <p>
 * TODO: a method to mutate the remote property must be added. I have not yet
 * decided on the signature of that method yet. It will come soon. There may
 * also be a need to add methods to lock and unlock a property.
 * 
 * @param <M>
 *            the type of data for the remote property
 */
public class RemoteProperty<M> extends PropertyStream<M> implements Disposable {

    private final RemotePropertyPublisher<M> propertyPublisher;
    private final PropertyService propertyService;
    private final EventLoop eventLoop;
	private final PropertyDispatcher<M> dispatcher;

    private RemoteProperty(RemotePropertyPublisher<M> propertyPublisher, PropertyService propertyService, PropertyDispatcher<M> dispatcher) {
        super(propertyPublisher);
        this.propertyPublisher = requireNonNull(propertyPublisher);
        this.propertyService = requireNonNull(propertyService);
        this.dispatcher = requireNonNull(dispatcher);
        this.eventLoop = EventLoop.createEventLoop();
    }
    
	public static <M> RemoteProperty<M> createRemoteProperty(PropertyService propertyService, PropertyId<M> id) {
		PropertyDispatcher<M> propertyDispatcher = Dispatcher.createPropertyDispatcher();
		RemotePropertyPublisher<M> publisher = new RemotePropertyPublisher<>(propertyService, propertyDispatcher, id);
		return new RemoteProperty<>(publisher, propertyService, propertyDispatcher);
	}

    @Override
    public final void dispose() {
        propertyPublisher.dispose();
    }
    
	/**
	 * Gets the id of this property
	 * 
	 * @return the unique id of this property.
	 */
    public final PropertyId<M> getId() {
        return propertyPublisher.getId();
    }
    
	/**
	 * Sets the value of this remote property. Calling this method will
	 * perform IO and will block. Consider using {@link #setValueAsync(Object)}
	 * if you do not want to block the UI thread. This method will throw an
	 * exception when attempting to update the value, if the current value
	 * remotely differs from the current value locally.
	 * 
	 * @param value
	 *            the new value to set on the remote property.
	 * @throws RemotePropertyException
	 *             if the current value of this remote value changed remotely
	 *             while trying to set the value.
	 * @throws IllegalStateException see {@link #isSetPossible(Object)}
	 */
	public final void setValue(M value) throws RemotePropertyException {
		eventLoop.checkInEventLoop();

		if (!isSetPossible(value))
			return;

		updateRemoteValue(value);
	}
    
	/**
	 * Replaces the value of this property. This differs than
	 * {@link #setValue(Object)} since it does not lock the property while
	 * setting. It also ignores the current value, so it could override a change
	 * that was set from some other remote actor.
	 * 
	 * @param newValue the new value to set
	 * @return the old value.
	 */
    public final M replaceValue(M newValue) {
    	eventLoop.checkInEventLoop();
    	
    	if (isSetPossible(newValue))
			return propertyService.replaceValue(getId(), newValue);
		
		return get();
    }

	/**
	 * Acquires the lock, confirms the value is unchanged, then updates the
	 * value of this remote property and releases the lock.
	 * 
	 * @param value
	 *            the new value to set for this remote property
	 * @throws RemotePropertyException
	 *             if the remote value of this property changed after acquiring
	 *             the lock.
	 */
	private void updateRemoteValue(M value) throws RemotePropertyException {
		try {
			lock();
			checkRemoteValueUnchanged();
			propertyService.setValue(getId(), value);
		} finally {
			unlock();
		}
	}
	
	/**
	 * Checks if it is possible (and/or required) to set the value. A value can
	 * be set if the none of the following are true:
	 * <ol>
	 * <li>This property is currently dispatching. Attempting to set during a
	 * dispatch indicates that a reentrant call was made. Reentrant calls are
	 * not allowed.
	 * <li>This property is currently disposed. Once disposed a property cannot
	 * be changed.
	 * <li>The new value is equal to the current value. In this case there is
	 * nothing to do. The update is prevented in order to reduce unnecessary
	 * event propagation.
	 * <li>Another dispatcher is currently dispatching and the observer that was
	 * used to update this property was not a binding. All bindings between
	 * properties must be handled in a special manner in order to maintain
	 * "glitch" protection.
	 * </ol>
	 * 
	 * @param newValue
	 *            the new value to set
	 * @return true if it is okay to set the new value, false otherwise.
	 * @throws IllegalStateException
	 *             if an attempt is made to update this property by observing
	 *             another property but not by a binding.
	 */
	private boolean isSetPossible(M newValue) {
        // blocks reentrant calls
        if (dispatcher.isDispatching())
            return false;
    
        // once a property is disposed it is frozen
        if (dispatcher.isDisposed())
            return false;
        
        // don't update the value if it's the same as the current value
        if (get().equals(newValue))
            return false;
        
        // blows up with an illegal state exception if an attempt is made to set the value via a non-binding callback.
        checkCanDispatch();
        
        return true;
	}

	/**
	 * Verifies that at this moment the remote value has the same value as this
	 * remote property. The value could change if another remote property
	 * attempted to set this value at the same time.
	 * 
	 * @throws RemotePropertyException
	 *             if the value remotely of this remote property differs than
	 *             this remote property.
	 */
	private void checkRemoteValueUnchanged() throws RemotePropertyException {
		M currentRemoteValue = propertyService.getValue(getId());
		M currentValue = get();
		if (!Objects.equals(currentRemoteValue, currentValue))
			throw new RemotePropertyException("Remote property value changed, during a set operation. "
					+ "Expected the value to be: [" + currentValue + "] " + "but it was [" + currentRemoteValue + "]");
	}

	/**
	 * Sets the value of this remote property. This method is non-blocking. If
	 * the remote property value changed before this set operation is performed
	 * the resultant {@link Single} will be failed with a
	 * {@link SetFailedException}.
	 * 
	 * @param value
	 *            the new value to set
	 * @return a {@link Single} that will be completed when the value has been
	 *         set. All subscribers will be called back on the UI thread by the
	 *         {@link EventLoop}.
	 */
    public final Single<Void> setValueAsync(M value) {
        eventLoop.checkInEventLoop();
        return propertyService.setValueAsync(getId(), value, get())
        		              .subscribeOn(Schedulers.io())
        		              .observeOn(Schedulers.from(eventLoop::invokeLater));
    }

    /**
     * Checks if this property is currently locked.
     * 
     * @return true if any process or thread holds the lock, false otherwise.
     */
    public final boolean isLocked(){
        eventLoop.checkInEventLoop();
        return propertyService.isLocked(getId());
    }

	/**
	 * Locks this property. This method will bloc if some other process holds
	 * the lock. Locks are reentrant, so from the same thread, you can lock the
	 * property many times. Therefore you must be careful to always unlock as
	 * many times as you lock.
	 */
    public final void lock() {
        eventLoop.checkInEventLoop();
        propertyService.lock(getId());
    }

	/**
	 * Decrements the lock count by one. If the lock count is zero after
	 * decrementing the property will be unlocked .
	 */
    public final void unlock() {
        eventLoop.checkInEventLoop();
        propertyService.unlock(getId());
    }
}
