package com.wunderlore.client.theone;

// Used when an authenticated request is attempted without THE_ONE_API_ACCESS_TOKEN configured.
public class TheOneApiNotConfiguredException extends RuntimeException {

    public TheOneApiNotConfiguredException() {
        super("THE_ONE_API_ACCESS_TOKEN is not configured");
    }
}
