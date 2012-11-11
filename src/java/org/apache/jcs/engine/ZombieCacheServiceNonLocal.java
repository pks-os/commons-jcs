package org.apache.jcs.engine;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.engine.behavior.ICacheElement;
import org.apache.jcs.engine.behavior.ICacheServiceNonLocal;
import org.apache.jcs.utils.struct.BoundedQueue;
import org.apache.jcs.utils.timing.ElapsedTimer;

/**
 * Zombie adapter for the non local cache services. It just balks if there is no queue configured.
 * <p>
 * If a queue is configured, then events will be added to the queue. The idea is that when proper
 * operation is restored, the non local cache will walk the queue. The queue must be bounded so it
 * does not eat memory.
 * <p>
 * This originated in the remote cache.
 */
public class ZombieCacheServiceNonLocal<K extends Serializable, V extends Serializable>
    extends ZombieCacheService<K, V>
    implements ICacheServiceNonLocal<K, V>
{
    /** The logger */
    private final static Log log = LogFactory.getLog( ZombieCacheServiceNonLocal.class );

    /** How big can the queue grow. */
    private int maxQueueSize = 0;

    /** The queue */
    private final BoundedQueue<ZombieEvent> queue;

    /**
     * Default.
     */
    public ZombieCacheServiceNonLocal()
    {
        queue = new BoundedQueue<ZombieEvent>( 0 );
    }

    /**
     * Sets the maximum number of items that will be allowed on the queue.
     * <p>
     * @param maxQueueSize
     */
    public ZombieCacheServiceNonLocal( int maxQueueSize )
    {
        this.maxQueueSize = maxQueueSize;
        queue = new BoundedQueue<ZombieEvent>( maxQueueSize );
    }

    /**
     * Gets the number of items on the queue.
     * <p>
     * @return size of the queue.
     */
    public int getQueueSize()
    {
        return queue.size();
    }

    /**
     * Adds an update event to the queue if the maxSize is greater than 0;
     * <p>
     * @param item ICacheElement
     * @param listenerId - identifies the caller.
     */
    public void update( ICacheElement<K, V> item, long listenerId )
    {
        if ( maxQueueSize > 0 )
        {
            PutEvent<K, V> event = new PutEvent<K, V>( item, listenerId );
            queue.add( event );
        }
        // Zombies have no inner life
    }

    /**
     * Adds a removeAll event to the queue if the maxSize is greater than 0;
     * <p>
     * @param cacheName - region name
     * @param key - item key
     * @param listenerId - identifies the caller.
     */
    public void remove( String cacheName, K key, long listenerId )
    {
        if ( maxQueueSize > 0 )
        {
            RemoveEvent<K, V> event = new RemoveEvent<K, V>( cacheName, key, listenerId );
            queue.add( event );
        }
        // Zombies have no inner life
    }

    /**
     * Adds a removeAll event to the queue if the maxSize is greater than 0;
     * <p>
     * @param cacheName - name of the region
     * @param listenerId - identifies the caller.
     */
    public void removeAll( String cacheName, long listenerId )
    {
        if ( maxQueueSize > 0 )
        {
            RemoveAllEvent event = new RemoveAllEvent( cacheName, listenerId );
            queue.add( event );
        }
        // Zombies have no inner life
    }

    /**
     * Does nothing. Gets are synchronous and cannot be added to a queue.
     * <p>
     * @param cacheName - region name
     * @param key - item key
     * @param requesterId - identifies the caller.
     * @return null
     * @throws IOException
     */
    public ICacheElement<K, V> get( String cacheName, K key, long requesterId )
        throws IOException
    {
        // Zombies have no inner life
        return null;
    }

    /**
     * Does nothing.
     * <p>
     * @param cacheName
     * @param pattern
     * @param requesterId
     * @return empty map
     * @throws IOException
     */
    public Map<K, ICacheElement<K, V>> getMatching( String cacheName, String pattern, long requesterId )
        throws IOException
    {
        return Collections.emptyMap();
    }

    /**
     * @param cacheName - region name
     * @param keys - item key
     * @param requesterId - identity of the caller
     * @return an empty map. zombies have no internal data
     */
    public Map<K, ICacheElement<K, V>> getMultiple( String cacheName, Set<K> keys, long requesterId )
    {
        return new HashMap<K, ICacheElement<K, V>>();
    }

    /**
     * Does nothing.
     * <p>
     * @param cacheName - region name
     * @param groupName - group name
     * @return empty set
     */
    public Set<K> getGroupKeys( String cacheName, String groupName )
    {
        return Collections.emptySet();
    }

    /**
     * Does nothing.
     * <p>
     * @param cacheName - region name
     * @return empty set
     */
    public Set<String> getGroupNames( String cacheName )
    {
        return Collections.emptySet();
    }

    /**
     * Walk the queue, calling the service for each queue operation.
     * <p>
     * @param service
     * @throws Exception
     */
    public synchronized void propagateEvents( ICacheServiceNonLocal<K, V> service )
        throws Exception
    {
        int cnt = 0;
        if ( log.isInfoEnabled() )
        {
            log.info( "Propagating events to the new ICacheServiceNonLocal." );
        }
        ElapsedTimer timer = new ElapsedTimer();
        while ( !queue.isEmpty() )
        {
            cnt++;

            // for each item, call the appropriate service method
            ZombieEvent event = queue.take();

            if ( event instanceof PutEvent )
            {
                @SuppressWarnings("unchecked") // Type checked by instanceof
                PutEvent<K, V> putEvent = (PutEvent<K, V>) event;
                service.update( putEvent.element, event.requesterId );
            }
            else if ( event instanceof RemoveEvent )
            {
                @SuppressWarnings("unchecked") // Type checked by instanceof
                RemoveEvent<K, V> removeEvent = (RemoveEvent<K, V>) event;
                service.remove( event.cacheName, removeEvent.key, event.requesterId );
            }
            else if ( event instanceof RemoveAllEvent )
            {
                service.removeAll( event.cacheName, event.requesterId );
            }
        }
        if ( log.isInfoEnabled() )
        {
            log.info( "Propagated " + cnt + " events to the new ICacheServiceNonLocal in "
                + timer.getElapsedTimeString() );
        }
    }

    /**
     * Base of the other events.
     */
    protected static abstract class ZombieEvent
    {
        /** The name of the region. */
        String cacheName;

        /** The id of the requester */
        long requesterId;
    }

    /**
     * A basic put event.
     */
    private static class PutEvent<K extends Serializable, V extends Serializable>
        extends ZombieEvent
    {
        /** The element to put */
        ICacheElement<K, V> element;

        /**
         * Set the element
         * @param element
         * @param requesterId
         */
        public PutEvent( ICacheElement<K, V> element, long requesterId )
        {
            this.requesterId = requesterId;
            this.element = element;
        }
    }

    /**
     * A basic Remove event.
     */
    private static class RemoveEvent<K extends Serializable, V extends Serializable>
        extends ZombieEvent
    {
        /** The key to remove */
        K key;

        /**
         * Set the element
         * @param cacheName
         * @param key
         * @param requesterId
         */
        public RemoveEvent( String cacheName, K key, long requesterId )
        {
            this.cacheName = cacheName;
            this.requesterId = requesterId;
            this.key = key;
        }
    }

    /**
     * A basic RemoveAll event.
     */
    private static class RemoveAllEvent
        extends ZombieEvent
    {
        /**
         * @param cacheName
         * @param requesterId
         */
        public RemoveAllEvent( String cacheName, long requesterId )
        {
            this.cacheName = cacheName;
            this.requesterId = requesterId;
        }
    }
}
