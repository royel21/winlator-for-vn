package com.winlator.xenvironment.components;

import com.winlator.alsaserver.ALSAClient;
import com.winlator.alsaserver.ALSAClientConnectionHandler;
import com.winlator.alsaserver.ALSARequestHandler;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xconnector.XConnectorEpoll;
import com.winlator.xenvironment.EnvironmentComponent;

import android.content.Context;
import androidx.preference.PreferenceManager;
import android.content.SharedPreferences;

public class ALSAServerComponent extends EnvironmentComponent {
    private XConnectorEpoll connector;
    private final UnixSocketConfig socketConfig;
    private final ALSAClient.Options options;

    public ALSAServerComponent(UnixSocketConfig socketConfig, ALSAClient.Options options) {
        this.socketConfig = socketConfig;
        this.options = options;
    }

    @Override
    public void start() {
        if (connector != null) return;
        ALSAClient.assignFramesPerBuffer(environment.getContext());
        connector = new XConnectorEpoll(socketConfig, new ALSAClientConnectionHandler(options), new ALSARequestHandler());
        connector.setMultithreadedClients(true);
        connector.start();
    }

    @Override
    public void stop() {
        if (connector != null) {
            connector.destroy();
            connector = null;
        }
    }

    public void setMuted(boolean muted) {
        if (connector != null) {
            for (com.winlator.xconnector.ConnectedClient client : connector.getClients()) {
                ALSAClient alsaClient = (ALSAClient)client.getTag();
                if (alsaClient != null) alsaClient.setVolume(muted ? 0 : options.volume);
            }
        }
    }

    @Override
    public void onPause() {
        Context context = environment.getContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean("enable_background_protection", false)) {
            setMuted(true);
            return;
        }

        if (connector != null) {
            for (com.winlator.xconnector.ConnectedClient client : connector.getClients()) {
                ALSAClient alsaClient = (ALSAClient)client.getTag();
                if (alsaClient != null) alsaClient.pause();
            }
        }
    }

    @Override
    public void onResume() {
        Context context = environment.getContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean("enable_background_protection", false)) {
            setMuted(false);
            return;
        }

        if (connector != null) {
            for (com.winlator.xconnector.ConnectedClient client : connector.getClients()) {
                ALSAClient alsaClient = (ALSAClient)client.getTag();
                if (alsaClient != null) alsaClient.start();
            }
        }
    }
}
