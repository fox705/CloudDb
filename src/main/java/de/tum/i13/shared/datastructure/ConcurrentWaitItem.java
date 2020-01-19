package de.tum.i13.shared.datastructure;

import java.util.Optional;

public class ConcurrentWaitItem<E> {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<E> item = Optional.empty();
    @SuppressWarnings("UnusedAssignment")
    private Object lock = this; //Default lock object, but outside can provide lock for this
    
    public ConcurrentWaitItem(Object lockObject) {
        this.lock = lockObject;
    }
    
    @SuppressWarnings("SynchronizeOnNonFinalField")
    public void push(E item) {
        synchronized (lock) {
            assert item != null;
            this.item = Optional.of(item);
            lock.notifyAll();
        }
    }
    
    @SuppressWarnings("SynchronizeOnNonFinalField")
    public E pop() {
        synchronized (lock) {
            if (item.isEmpty()) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted");
                }
            }
            var top = item.get();
            item = Optional.empty();
            return top;
        }
    }
}
