package com.thelastwar.gateway.integration;

import quickfix.ConfigError;
import quickfix.SessionID;
import quickfix.SessionSettings;

/**
 * Configuration helper for FIX server settings.
 */
final class FixServerConfig {
    
    private FixServerConfig() {}
    
    static SessionSettings createClientSettings(int port, String senderCompId, 
                                                String targetCompId, String suffix) throws ConfigError {
        SessionSettings settings = new SessionSettings();
        
        settings.setString("ConnectionType", "initiator");
        settings.setString("SocketConnectHost", "localhost");
        settings.setString("SocketConnectPort", String.valueOf(port));
        settings.setString("StartTime", "00:00:00");
        settings.setString("EndTime", "23:59:59");
        settings.setString("HeartBtInt", "30");
        settings.setString("ReconnectInterval", "5");
        settings.setString("FileStorePath", "build/tmp/fix-" + suffix + "-store");
        settings.setString("FileLogPath", "build/tmp/fix-" + suffix + "-log");
        
        SessionID sessionID = new SessionID("FIX.4.4", senderCompId, targetCompId);
        settings.setString(sessionID, "BeginString", "FIX.4.4");
        settings.setString(sessionID, "SenderCompID", senderCompId);
        settings.setString(sessionID, "TargetCompID", targetCompId);
        settings.setString(sessionID, "ConnectionType", "initiator");
        settings.setString(sessionID, "ResetOnLogon", "Y");
        settings.setString(sessionID, "ResetOnLogout", "Y");
        settings.setString(sessionID, "ResetOnDisconnect", "Y");
        
        return settings;
    }
    
    static SessionSettings createServerSettings(int port, String senderCompId, 
                                                String targetCompId, String suffix) throws ConfigError {
        SessionSettings settings = new SessionSettings();
        
        settings.setString("ConnectionType", "acceptor");
        settings.setString("SocketAcceptPort", String.valueOf(port));
        settings.setString("StartTime", "00:00:00");
        settings.setString("EndTime", "23:59:59");
        settings.setString("HeartBtInt", "30");
        settings.setString("FileStorePath", "build/tmp/fix-" + suffix + "-store");
        settings.setString("FileLogPath", "build/tmp/fix-" + suffix + "-log");
        
        SessionID sessionID = new SessionID("FIX.4.4", senderCompId, targetCompId);
        settings.setString(sessionID, "BeginString", "FIX.4.4");
        settings.setString(sessionID, "SenderCompID", senderCompId);
        settings.setString(sessionID, "TargetCompID", targetCompId);
        
        return settings;
    }
}
