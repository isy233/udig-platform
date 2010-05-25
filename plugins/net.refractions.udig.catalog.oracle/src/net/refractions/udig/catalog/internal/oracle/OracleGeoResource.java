/*
 *    uDig - User Friendly Desktop Internet GIS client
 *    http://udig.refractions.net
 *    (C) 2004, Refractions Research Inc.
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 */
package net.refractions.udig.catalog.internal.oracle;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import net.refractions.udig.catalog.CatalogPlugin;
import net.refractions.udig.catalog.IGeoResource;
import net.refractions.udig.catalog.IGeoResourceInfo;
import net.refractions.udig.catalog.IService;
import net.refractions.udig.catalog.oracle.internal.Messages;
import net.refractions.udig.ui.graphics.Glyph;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.geotools.data.FeatureSource;
import org.geotools.data.FeatureStore;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.jdbc.JDBCDataStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Envelope;

/**
 * Provides ...TODO summary sentence
 * <p>
 * TODO Description
 * </p>
 * 
 * @author David Zwiers, Refractions Research
 * @since 0.6
 */
public class OracleGeoResource extends IGeoResource {
    String typename = null;

    /**
     * Construct <code>OracleGeoResource</code>.
     * 
     * @param parent
     * @param typename
     */
    public OracleGeoResource( OracleServiceImpl parent, String typename ) {
        this.service = parent;
        this.typename = typename;
    }

    OracleServiceImpl getService() {
        return (OracleServiceImpl) service;
    }
    public URL getIdentifier() {
        try {
            return new URL(service.getIdentifier().toString() + "#" + typename); //$NON-NLS-1$
        } catch (MalformedURLException e) {
            return service.getIdentifier();
        }
    }

    /*
     * @see net.refractions.udig.catalog.IGeoResource#getStatus()
     */
    public Status getStatus() {
        return service.getStatus();
    }

    /*
     * @see net.refractions.udig.catalog.IGeoResource#getStatusMessage()
     */
    public Throwable getMessage() {
        return service.getMessage();
    }

    /*
     * Required adaptions: <ul> <li>IGeoResourceInfo.class <li>IService.class </ul>
     * @see net.refractions.udig.catalog.IResolve#resolve(java.lang.Class,
     * org.eclipse.core.runtime.IProgressMonitor)
     */
    public <T> T resolve( Class<T> adaptee, IProgressMonitor monitor ) throws IOException {
        if (adaptee == null)
            return null;
        // if (adaptee.isAssignableFrom(IService.class))
        // return adaptee.cast(parent);
        if (adaptee.isAssignableFrom(IGeoResourceInfo.class))
            return adaptee.cast(createInfo(monitor));
        if (adaptee.isAssignableFrom(IGeoResource.class))
            return adaptee.cast(this);
        if (adaptee.isAssignableFrom(FeatureStore.class)) {
            JDBCDataStore dataStore = getService().getDS(monitor);

            FeatureSource<SimpleFeatureType, SimpleFeature> fs = dataStore
                    .getFeatureSource(typename);

            if (fs instanceof FeatureStore< ? , ? >){
                return adaptee.cast(fs);
            }
            if (adaptee.isAssignableFrom(FeatureSource.class)) {
                dataStore = getService().getDS(monitor);

                return adaptee.cast(dataStore.getFeatureSource(typename));
            }
        }
        return super.resolve(adaptee, monitor);
    }
    /*
     * @see net.refractions.udig.catalog.IResolve#canResolve(java.lang.Class)
     */
    public <T> boolean canResolve( Class<T> adaptee ) {
        if (adaptee == null)
            return false;
        return (adaptee.isAssignableFrom(IGeoResourceInfo.class)
                || adaptee.isAssignableFrom(FeatureStore.class)
                || adaptee.isAssignableFrom(FeatureSource.class) || adaptee
                .isAssignableFrom(IService.class))
                || super.canResolve(adaptee);
    }

    protected IGeoResourceInfo createInfo( IProgressMonitor monitor ) throws IOException {
        if (getStatus() == Status.BROKEN) {
            return null; // could not connect
        }
        getService().rLock.lock();
        try {
            return new OracleResourceInfo();
        } finally {
            getService().rLock.unlock();
        }
    }

    class OracleResourceInfo extends IGeoResourceInfo {

        private SimpleFeatureType ft = null;
        OracleResourceInfo() throws IOException {
            JDBCDataStore dataStore = getService().getDS(null);
            ft = dataStore.getSchema(typename);

            try {
                FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore
                        .getFeatureSource(typename);
                bounds = (ReferencedEnvelope) source.getBounds();
                if (bounds == null) {
                    CoordinateReferenceSystem crs = null;
                    // GeometryDescriptor defaultGeometry =
                    // source.getSchema().getGeometryDescriptor();
                    crs = source.getSchema().getCoordinateReferenceSystem();
                    bounds = new ReferencedEnvelope(new Envelope(), crs);
                    FeatureIterator<SimpleFeature> iter = source.getFeatures().features();
                    try {
                        while( iter.hasNext() ) {
                            SimpleFeature element = iter.next();
                            if (bounds.isNull())
                                bounds.init(element.getBounds());
                            else
                                bounds.include(element.getBounds());
                        }
                    } finally {
                        iter.close();
                    }
                }
                // CoordinateReferenceSystem geomcrs = source.getSchema().getCRS();

                // if(geomcrs!=null && !geomcrs.equals(CRS.decode("EPSG:4269"))){
                // bounds = JTS.transform(bounds,CRS.transform(geomcrs,CRS.decode("EPSG:4269")));
                // }else{
                // if(geomcrs == null)
                // System.err.println("CRS unknown for Shp");
                // }
            } catch (Exception e) {
                CatalogPlugin
                        .getDefault()
                        .getLog()
                        .log(
                                new org.eclipse.core.runtime.Status(
                                        IStatus.WARNING,
                                        "net.refractions.udig.catalog", 0, Messages.OracleGeoResource_error_layerBounds, e)); //$NON-NLS-1$
                bounds = new ReferencedEnvelope(new Envelope(), null);
            }

            icon = Glyph.icon(ft);
            keywords = new String[]{"postgis", //$NON-NLS-1$
                    ft.getName().getLocalPart(), ft.getName().getNamespaceURI()};
        }

        public CoordinateReferenceSystem getCRS() {
            return ft.getCoordinateReferenceSystem();
        }

        public String getName() {
            return ft.getName().getLocalPart();
        }

        public URI getSchema() {
            try {
                return new URI(ft.getName().getNamespaceURI());
            } catch (URISyntaxException e) {
                return null;
            }
        }

        public String getTitle() {
            return ft.getName().getLocalPart();
        }
    }
}