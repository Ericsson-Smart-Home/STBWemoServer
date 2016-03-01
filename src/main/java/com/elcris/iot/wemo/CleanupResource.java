package com.elcris.iot.wemo;

import java.io.IOException;
import java.net.URI;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;

public class CleanupResource extends ClientResource {



    public CleanupResource() {
        super();
    }

    public CleanupResource(ClientResource resource) {
        super(resource);
    }

    public CleanupResource(Context context, Method method, Reference reference) {
        super(context, method, reference);
    }

    public CleanupResource(Context context, Method method, String uri) {
        super(context, method, uri);
    }

    public CleanupResource(Context context, Method method, URI uri) {
        super(context, method, uri);
    }

    public CleanupResource(Context context, Reference reference) {
        super(context, reference);
    }

    public CleanupResource(Context context, Request request, Response response) {
        super(context, request, response);
    }

    public CleanupResource(Context context, Request request) {
        super(context, request);
    }

    public CleanupResource(Context context, String uri) {
        super(context, uri);
    }

    public CleanupResource(Context context, URI uri) {
        super(context, uri);
    }

    public CleanupResource(Method method, Reference reference) {
        super(method, reference);
    }

    public CleanupResource(Method method, String uri) {
        super(method, uri);
    }

    public CleanupResource(Method method, URI uri) {
        super(method, uri);
    }

    public CleanupResource(Reference reference) {
        super(reference);
    }

    public CleanupResource(Request request, Response response) {
        super(request, response);
    }

    public CleanupResource(Request request) {
        super(request);
    }

    public CleanupResource(String uri) {
        super(uri);
    }

    public CleanupResource(URI uri) {
        super(uri);
    }

    @Override
    protected void doRelease() throws ResourceException {
        try {
            if( getResponseEntity() != null ) {
                getResponseEntity().exhaust();
                getResponseEntity().release();
            }
        } catch (IOException ignore) {}
    }
}
