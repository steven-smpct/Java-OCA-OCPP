package eu.chargetime.ocpp;

/*
 ChargeTime.eu - Java-OCA-OCPP
 Copyright (C) 2015-2016 Thomas Volden <tv@chargetime.eu>

 MIT License

 Copyright (C) 2016-2018 Thomas Volden

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.extensions.IExtension;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.util.Collections;
/**
 * Web Socket implementation of the Transmitter.
 */
public class WebSocketTransmitter implements Transmitter
{
    private static final Logger logger = LogManager.getLogger(WebSocketTransmitter.class);
    private SSLContext sslContext;

    private WebSocketClient client;
    
    public WebSocketTransmitter(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public WebSocketTransmitter() {
        this(null);
    }

    @Override
    public void connect(String uri, RadioEvents events) {
    	Draft_6455 draft =  new Draft_6455(Collections.<IExtension>emptyList(), Collections.<IProtocol>singletonList(new Protocol("ocpp1.6")));
        client = new WebSocketClient(URI.create(uri), draft) {
            @Override
            public void onOpen(ServerHandshake serverHandshake)
            {
                events.connected();
            }

            @Override
            public void onMessage(String s)
            {
                events.receivedMessage(s);
            }
            
            @Override
            public void onClose(int i, String s, boolean b)
            {
            	logger.debug("WebSocketClient.onClose: code = " + i + ", message = " + s + ", host closed = " + b);
                events.disconnected();
            }

            @Override
            public void onError(Exception ex)
            {
            	if(ex instanceof ConnectException) {
                	logger.warn("onError() triggered caused by: " +  ex);
            	} else {
            		logger.warn("onError() triggered", ex);
            	}
            }
        };
        
        if(sslContext != null) {
            try {
                SSLSocketFactory factory = sslContext.getSocketFactory();
    			client.setSocket(factory.createSocket());
    		} catch (IOException ex) {
    			logger.error("client.setSocket() failed", ex);
    		}
        }
        
        try {
            client.connectBlocking();
        } catch (Exception ex) {
        	logger.warn("client.connectBlocking() failed", ex);
        }
    }
    
    public void setPingInterval(int interval) {
        client.setConnectionLostTimeout(interval);
    }

    @Override
    public void disconnect()
    {
        try {
            client.closeBlocking();
        } catch (Exception ex) {
        	logger.info("client.closeBlocking() failed", ex);
        }
    }

    @Override
    public void send(Object request) throws NotConnectedException {
    	logger.debug("Sending: " + request);
        try {
            client.send(request.toString());
        } catch (WebsocketNotConnectedException ex) {
            throw new NotConnectedException();
        }
    }
}
