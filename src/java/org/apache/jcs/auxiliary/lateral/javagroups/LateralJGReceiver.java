package org.apache.jcs.auxiliary.lateral.javagroups;


/*
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.auxiliary.lateral.behavior.ILateralCacheAttributes;
import org.apache.jcs.auxiliary.lateral.javagroups.behavior.IJGConstants;
import org.apache.jcs.auxiliary.lateral.javagroups.behavior.ILateralCacheJGListener;
import org.jgroups.Channel;
import org.jgroups.ChannelNotConnectedException;
import org.jgroups.Message;

/**
 * Processes commands from the server socket.
 *
 * @version $Id$
 */
public class LateralJGReceiver implements IJGConstants, Runnable
{
    private final static Log log =
        LogFactory.getLog( LateralJGReceiver.class );

    private ILateralCacheJGListener ilcl;
    private ILateralCacheAttributes ilca;


    /**
     * Main processing method for the LateralJGReceiver object
     */
    public void run()
    {
        try
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( "Listening" );
            }

            JGConnectionHolder holder = JGConnectionHolder.getInstance(ilca);
            Channel javagroups = holder.getChannel();
            /* RpcDispatcher disp = */ holder.getDispatcher();

            if ( javagroups == null )
            {
                log.error( "JavaGroups is null" );
                throw new IOException( "javagroups is null" );
            }

            int conProbCnt = 0;
            while ( true )
            {
                if ( log.isDebugEnabled() )
                {
                    log.debug( "Wating for messages." );
                }

                Message mes = null;
                try
                {
                    Object obj = javagroups.receive( 0 );
                    if ( obj != null && obj instanceof org.jgroups.Message )
                    {
                        mes = ( Message ) obj;
                        if ( log.isDebugEnabled() )
                        {
                          log.debug( "Starting new socket node." );
                        }
                        new Thread( new LateralJGReceiverConnection( mes, ilcl ) ).start();
                    }
                    else
                    {
                        if ( log.isDebugEnabled() )
                        {
                            log.debug( obj );
                        }
                    }
                }
                catch ( ChannelNotConnectedException cnce )
                {
                    if ( conProbCnt % 20 == 0 )
                    {
                      log.warn(cnce);
                    }
                    conProbCnt++;

                    if ( conProbCnt >= 2000 )
                    {
                      log.error( "Couldn't get connected to group after " + conProbCnt + " tries" );
                      break;
                    }
                    // slow the connection try process down
                    synchronized (this)
                    {
                      this.wait(10);
                    }
                    // this will cycle unitl connected and eat up the processor
                    // need to throw out and recover
                    // seems to periodically require about 50 tries.
                }
                catch ( Exception e )
                {
                    // should zombie
                    log.error( "problem receiving", e );
                }

            }
        }
        catch ( Exception e )
        {
            log.error( "Major connection problem", e );
        }
    }


    /**
     * Constructor for the LateralJGReceiver object
     *
     * @param lca
     * @param ilcl
     */
    public LateralJGReceiver( ILateralCacheAttributes ilca, ILateralCacheJGListener ilcl )
    {

        this.ilcl = ilcl;
        this.ilca = ilca;
        if ( log.isDebugEnabled() )
        {
            log.debug( "ilcl = " + ilcl );
        }
    }
}
