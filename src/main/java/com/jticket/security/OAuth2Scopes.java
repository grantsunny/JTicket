package com.jticket.security;

public final class OAuth2Scopes {

    public static final String EVENT_READ = "event:read";
    public static final String EVENT_WRITE = "event:write";
    public static final String ORDER_PAY = "order:pay";
    public static final String ORDER_READ = "order:read";
    public static final String ORDER_READ_ALL = "order:read:all";
    public static final String ORDER_WRITE = "order:write";
    public static final String ORDER_WRITE_ALL = "order:write:all";
    public static final String SEAT_READ = "seat:read";
    public static final String TEMPLATE_WRITE = "template:write";
    public static final String VENUE_READ = "venue:read";

    private OAuth2Scopes() {
    }
}
